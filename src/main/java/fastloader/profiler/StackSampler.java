package fastloader.profiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Statistical profiler of the loading thread: reads its stack every few ms from a background thread. Nothing in the
 * game is patched or wrapped, so it works with any modpack, and it shows where time goes inside the sections of the
 * load tree: which mod's code, which class transformer, how much is class loading.
 */
final class StackSampler implements Runnable {

    static final boolean ENABLED = LoadProfiler.ENABLED
        && !"false".equalsIgnoreCase(System.getProperty("fastloader.profiler.stacks"));
    static final long PERIOD_MS = Long.getLong("fastloader.profiler.stackSampleMs", 10L);

    private static Thread thread;
    private static Thread target;
    private static volatile boolean running;

    // all guarded by StackSampler.class
    static int samples;
    static int classLoadingSamples;
    /** Package -> samples with code from that package anywhere on the stack. */
    static final Map<String, int[]> PACKAGES_INCLUSIVE = new HashMap<>();
    /** First stack frame outside the JDK -> samples. */
    static final Map<String, int[]> HOT_METHODS = new HashMap<>();
    /** Class transformer -> samples with it on the stack. */
    static final Map<String, int[]> TRANSFORMERS = new HashMap<>();
    static String targetName;

    private StackSampler() {}

    static synchronized void start(Thread loadingThread) {
        if (!ENABLED || thread != null) return;
        target = loadingThread;
        targetName = loadingThread.getName();
        running = true;
        thread = new Thread(new StackSampler(), "FastLoader-StackSampler");
        thread.setDaemon(true);
        thread.start();
    }

    static synchronized void stop() {
        running = false;
    }

    @Override
    public void run() {
        try {
            while (running && target.isAlive()) {
                StackTraceElement[] st = target.getStackTrace();
                if (st.length > 0) record(st);
                Thread.sleep(PERIOD_MS);
            }
        } catch (Throwable t) {
            LoadProfiler.note("Stack sampler stopped: " + t);
        }
    }

    private static void record(StackTraceElement[] st) {
        Set<String> packages = new HashSet<>();
        Set<String> transformers = new HashSet<>();
        boolean classLoading = false;
        String hot = null;
        for (StackTraceElement e : st) {
            String cn = e.getClassName();
            String m = e.getMethodName();
            if (hot == null && !isJdk(cn)) hot = cn + "." + m;
            if ("findClass".equals(m) && cn.contains("ClassLoader") && !isJdk(cn)) classLoading = true;
            if (cn.startsWith("org.spongepowered.asm.mixin.transformer.MixinTransformer")) {
                transformers.add("Mixin (all mixin configs)");
            } else if (("transform".equals(m) || "transformClass".equals(m)) && !isInfrastructure(cn)) {
                transformers.add(cn);
            }
            if (!isJdk(cn) && !isInfrastructure(cn)) packages.add(packageOf(cn));
        }
        synchronized (StackSampler.class) {
            samples++;
            if (classLoading) classLoadingSamples++;
            for (String p : packages) inc(PACKAGES_INCLUSIVE, p);
            for (String t : transformers) inc(TRANSFORMERS, t);
            if (hot != null) inc(HOT_METHODS, hot);
        }
    }

    private static void inc(Map<String, int[]> map, String key) {
        int[] c = map.get(key);
        if (c == null) map.put(key, new int[] { 1 });
        else c[0]++;
    }

    private static boolean isJdk(String cn) {
        return cn.startsWith("java.") || cn.startsWith("javax.")
            || cn.startsWith("jdk.")
            || cn.startsWith("sun.")
            || cn.startsWith("com.sun.");
    }

    /** Class loading and transformer plumbing, which is on the stack of every class load. */
    private static boolean isInfrastructure(String cn) {
        return cn.startsWith("net.minecraft.launchwrapper.") || cn.startsWith("fastloader.profiler.")
            || cn.startsWith("com.gtnewhorizons.retrofuturabootstrap.")
            || cn.startsWith("org.spongepowered.asm.service.")
            || cn.equals("org.spongepowered.asm.mixin.transformer.Proxy");
    }

    /** Up to three package segments, e.g. "gregtech.api.util" or "com.mitchej123.hodgepodge". */
    static String packageOf(String cn) {
        String[] parts = cn.split("\\.");
        int n = Math.min(3, parts.length - 1);
        if (n <= 0) return cn;
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < n; i++) sb.append('.')
            .append(parts[i]);
        return sb.toString();
    }
}
