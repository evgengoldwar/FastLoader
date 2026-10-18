package fastloader.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/**
 * EXPERIMENTAL, off by default ({@code preloadClasses=true} to enable).
 * <p>
 * Loads the classes the previous startup loaded on a background thread: read, transformed (coremods, Mixin) and
 * defined, but not initialised, so no static initialiser runs earlier than it would have. The LaunchClassLoader is
 * not parallel capable, so transformers still run one class at a time, just while the main thread is busy with mod
 * code instead of waiting for them.
 * <p>
 * Starts when the FastLoader mod is constructed: by then all coremods are set up and the late mixin configs are
 * registered. A class that fails to load here is removed from the loader's "invalid" cache, so the game gets a
 * normal second attempt later.
 */
public final class ClassPreloader {

    private static final String LIST_FILE = "class-preload.txt";

    private static Thread thread;
    private static volatile boolean running;
    static final AtomicInteger LOADED = new AtomicInteger();
    static final AtomicInteger ALREADY = new AtomicInteger();
    static final AtomicInteger FAILED = new AtomicInteger();
    static int listed;
    static long millis = -1;
    static String status = "disabled (experimental, preloadClasses=false)";

    private ClassPreloader() {}

    public static synchronized void start() {
        if (thread != null || !FastLoaderConfig.getBoolean("preloadClasses")) return;
        final LaunchClassLoader loader = Launch.classLoader;
        final Map<String, Class<?>> cached;
        final Set<String> invalid;
        final Set<String> parentOnly;
        try {
            cached = cachedClasses(loader);
            invalid = invalidClasses(loader);
            parentOnly = classLoaderExceptions(loader);
        } catch (Throwable t) {
            status = "unavailable: " + t;
            return;
        }
        final List<String> names = readList();
        listed = names.size();
        if (names.isEmpty()) {
            status = "no class list yet, it is recorded during this startup";
            return;
        }
        status = "running";
        running = true;
        thread = new Thread(() -> {
            long start = System.nanoTime();
            for (String name : names) {
                if (!running) break;
                if (cached.containsKey(name)) {
                    ALREADY.incrementAndGet();
                    continue;
                }
                if (ClassBytesPrefetcher.startsWithAny(name, parentOnly)) continue;
                try {
                    Class.forName(name, false, loader);
                    LOADED.incrementAndGet();
                } catch (Throwable t) {
                    FAILED.incrementAndGet();
                    // do not let a failure that may only be due to loading early stick for the real load
                    invalid.remove(name);
                }
            }
            millis = (System.nanoTime() - start) / 1_000_000L;
            if (running) {
                running = false;
                status = "done";
            }
        }, "FastLoader-ClassPreload");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    /** Stops preloading and remembers the classes this startup loaded (only when the feature is enabled). */
    public static synchronized void onStartupFinished() {
        boolean wasRunning = running;
        running = false;
        if (wasRunning) status = "stopped at end of loading";
        if (!FastLoaderConfig.getBoolean("preloadClasses")) return;
        try {
            Set<String> names = new TreeSet<>(cachedClasses(Launch.classLoader).keySet());
            File file = new File(FastLoaderConfig.dataDir(), LIST_FILE);
            try (PrintWriter out = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                for (String name : names) {
                    // skip runtime generated classes, there is nothing to load ahead of time for them
                    if (!name.contains("$$") && !name.startsWith("fastloader.")) out.println(name);
                }
            }
        } catch (Throwable t) {
            System.err.println("[FastLoader] Cannot save the class preload list: " + t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Class<?>> cachedClasses(LaunchClassLoader loader) throws Exception {
        return (Map<String, Class<?>>) ClassBytesPrefetcher.field("cachedClasses")
            .get(loader);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> invalidClasses(LaunchClassLoader loader) throws Exception {
        return (Set<String>) ClassBytesPrefetcher.field("invalidClasses")
            .get(loader);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> classLoaderExceptions(LaunchClassLoader loader) throws Exception {
        return (Set<String>) ClassBytesPrefetcher.field("classLoaderExceptions")
            .get(loader);
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
                if (!line.isEmpty()) names.add(line);
            }
        } catch (Throwable t) {
            System.err.println("[FastLoader] Cannot read " + file + ": " + t);
        }
        return names;
    }

    public static String describe() {
        return String.format(
            "%s; %d listed, %d loaded ahead, %d were already loaded, %d failed (retried normally later)%s",
            status,
            listed,
            LOADED.get(),
            ALREADY.get(),
            FAILED.get(),
            millis >= 0 ? ", took " + millis + " ms" : "");
    }
}
