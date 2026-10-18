package fastloader.core;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/**
 * Warms the LaunchClassLoader's raw class bytes cache from background threads.
 * <p>
 * For every class, the loader looks the {@code .class} file up in all jars of the classpath (twice on RFB) and reads
 * and inflates it on the loading thread, before transformers run. It checks its {@code resourceCache} first though,
 * so filling that cache ahead of time, with the classes the previous startup needed, moves the lookup and the
 * reading to other cores. The bytes come from the loader's own {@code findResource}, so they are exactly what the
 * loader would have read; only untransformed bytes are cached, transformers still run as usual.
 */
public final class ClassBytesPrefetcher {

    private static final String LIST_FILE = "class-bytes.txt";

    private static LaunchClassLoader loader;
    private static Map<String, byte[]> resourceCache;
    private static Set<String> transformerExceptions;
    private static Set<String> classLoaderExceptions;

    private static volatile boolean running;
    private static final ConcurrentLinkedQueue<String> QUEUE = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<String> MISSES = new ConcurrentLinkedQueue<>();

    static final AtomicInteger PREFETCHED = new AtomicInteger();
    static final AtomicInteger ALREADY_CACHED = new AtomicInteger();
    static final AtomicLong BYTES = new AtomicLong();
    static int listed;
    static int threads;
    static long firstPassMillis = -1;
    static String status = "not started";
    private static int urlsAtStart;

    private ClassBytesPrefetcher() {}

    /** Called from the coremod constructor. */
    @SuppressWarnings("unchecked")
    public static synchronized void start() {
        if (!FastLoaderConfig.getBoolean("prefetchClassBytes")) {
            status = "disabled in config";
            return;
        }
        try {
            loader = Launch.classLoader;
            resourceCache = (Map<String, byte[]>) field("resourceCache").get(loader);
            transformerExceptions = (Set<String>) field("transformerExceptions").get(loader);
            classLoaderExceptions = (Set<String>) field("classLoaderExceptions").get(loader);
        } catch (Throwable t) {
            status = "unavailable: " + t;
            return;
        }
        List<String> names = readList();
        listed = names.size();
        if (names.isEmpty()) {
            status = "no class list yet, it is recorded during this startup";
            return;
        }
        QUEUE.addAll(names);
        urlsAtStart = loader.getURLs().length;
        running = true;
        int n = FastLoaderConfig.getInt("prefetchThreads");
        if (n <= 0) n = Math.max(2, Math.min(8, Runtime.getRuntime()
            .availableProcessors() / 3));
        threads = n;
        status = "running";
        long start = System.nanoTime();
        AtomicInteger alive = new AtomicInteger(n);
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(() -> {
                work();
                if (alive.decrementAndGet() == 0) {
                    firstPassMillis = (System.nanoTime() - start) / 1_000_000L;
                    retryMisses();
                }
            }, "FastLoader-ClassPrefetch-" + i);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.start();
        }
    }

    /** Stops prefetching and remembers which classes this startup read, for the next one. */
    public static synchronized void onStartupFinished() {
        boolean wasRunning = running;
        running = false;
        QUEUE.clear();
        if (wasRunning) status = "finished";
        if (resourceCache == null) return;
        Set<String> names = new TreeSet<>(resourceCache.keySet());
        File file = new File(FastLoaderConfig.dataDir(), LIST_FILE);
        try (PrintWriter out = new PrintWriter(
            new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (String name : names) out.println(name);
        } catch (Throwable t) {
            System.err.println("[FastLoader] Cannot write " + file + ": " + t);
        }
    }

    private static void work() {
        String name;
        while (running && (name = QUEUE.poll()) != null) {
            try {
                if (resourceCache.containsKey(name)) {
                    ALREADY_CACHED.incrementAndGet();
                    continue;
                }
                URL url = loader.findResource(name.replace('.', '/') + ".class");
                if (url == null) {
                    // its jar may not be on the classpath yet (mod jars are added during mod discovery)
                    MISSES.add(name);
                    continue;
                }
                byte[] bytes = read(url);
                if (bytes != null && resourceCache.putIfAbsent(name, bytes) == null) {
                    PREFETCHED.incrementAndGet();
                    BYTES.addAndGet(bytes.length);
                }
            } catch (Throwable ignored) {
                // leave this class to the loader itself
            }
        }
    }

    /** Retries missed classes once the classpath stopped growing, a few times. */
    private static void retryMisses() {
        int lastUrls = urlsAtStart;
        for (int round = 0; round < 5 && running && !MISSES.isEmpty(); round++) {
            // wait until mod discovery added jars, and then until it stopped adding them for a moment
            long deadline = System.currentTimeMillis() + 120_000L;
            int urls = lastUrls;
            while (running && System.currentTimeMillis() < deadline) {
                sleep(300);
                int now = loader.getURLs().length;
                if (now == urls && now != lastUrls) break;
                urls = now;
            }
            if (!running) break;
            lastUrls = urls;
            List<String> retry = new ArrayList<>();
            String name;
            while ((name = MISSES.poll()) != null) retry.add(name);
            QUEUE.addAll(retry);
            work();
        }
        if (running) status = "done, " + MISSES.size() + " classes not found in any jar";
    }

    private static List<String> readList() {
        List<String> names = new ArrayList<>();
        File file = new File(FastLoaderConfig.dataDir(), LIST_FILE);
        if (!file.isFile()) return names;
        try (BufferedReader in = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                // the loader bypasses the cache for these, prefetching them would only cost memory
                if (!line.isEmpty() && !startsWithAny(line, transformerExceptions)
                    && !startsWithAny(line, classLoaderExceptions)) names.add(line);
            }
        } catch (Throwable t) {
            System.err.println("[FastLoader] Cannot read " + file + ": " + t);
        }
        return names;
    }

    static boolean startsWithAny(String name, Collection<String> prefixes) {
        for (String p : prefixes) if (name.startsWith(p)) return true;
        return false;
    }

    private static byte[] read(URL url) throws Exception {
        try (InputStream in = url.openStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    static Field field(String name) throws NoSuchFieldException {
        Field f = LaunchClassLoader.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    /** One line for the report. */
    public static String describe() {
        return String.format(
            "%s; %d listed, %d read ahead (%d MB) with %d threads, %d were already cached%s",
            status,
            listed,
            PREFETCHED.get(),
            BYTES.get() / (1024 * 1024),
            threads,
            ALREADY_CACHED.get(),
            firstPassMillis >= 0 ? ", first pass took " + firstPassMillis + " ms" : "");
    }
}
