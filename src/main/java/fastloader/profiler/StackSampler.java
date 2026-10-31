package fastloader.profiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fastloader.FastLoader;

/**
 * Low-frequency stack sampler for the server thread. Every {@link #intervalMs} it looks at the innermost profiler
 * section the server thread is in and, if it is doing chunk work, records its stack. This explains the self time of
 * sections we cannot instrument directly (RWG decorators, CoFH, Et Futurum, setBlock internals...), whatever version
 * of those mods is installed.
 */
public final class StackSampler {

    private StackSampler() {}

    public static volatile boolean enabled = true;
    public static volatile int intervalMs = 10;

    private static final Object LOCK = new Object();
    private static Map<String, SectionSamples> current = new HashMap<>();
    private static long totalSamples;
    private static volatile Thread worker;

    public static synchronized void start() {
        if (!enabled || worker != null) return;
        Thread t = new Thread(StackSampler::run, "FastLoader stack sampler");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        worker = t;
        t.start();
    }

    public static synchronized void stop() {
        Thread t = worker;
        worker = null;
        if (t != null) t.interrupt();
    }

    static void reset() {
        synchronized (LOCK) {
            current = new HashMap<>();
            totalSamples = 0;
        }
    }

    private static void run() {
        Thread self = Thread.currentThread();
        while (worker == self) {
            try {
                Thread.sleep(Math.max(1, intervalMs));
            } catch (InterruptedException e) {
                return;
            }
            try {
                sampleOnce();
            } catch (Throwable t) {
                // never let diagnostics kill anything; just skip this sample
                FastLoader.LOG.debug("stack sample failed", t);
            }
        }
    }

    private static void sampleOnce() {
        if (!ChunkProfiler.enabled) return;
        ChunkProfiler.ThreadState st = ChunkProfiler.serverState;
        if (st == null || st.depth <= 0) return;
        String key = topKey(st);
        if (key == null) return;
        StackTraceElement[] trace = st.thread.getStackTrace();
        // the thread may have left chunk work while we were waiting for the safepoint
        if (st.depth <= 0) return;
        record(key, trace);
    }

    private static String topKey(ChunkProfiler.ThreadState st) {
        ChunkProfiler.Frame[] stack = st.stack;
        int d = Math.min(st.depth, stack.length);
        if (d <= 0) return null;
        ChunkProfiler.Frame f = stack[d - 1];
        return f == null ? null : f.key;
    }

    private static void record(String key, StackTraceElement[] trace) {
        // stack top: first three frames that are not FastLoader's own hooks
        StringBuilder top = new StringBuilder();
        int taken = 0;
        String mod = null;
        for (StackTraceElement e : trace) {
            if (isOwnFrame(e)) continue;
            if (taken < 3) {
                if (taken > 0) top.append(" < ");
                top.append(shortName(e));
                taken++;
            }
            if (mod == null && isModFrame(e)) mod = shortName(e);
            if (taken >= 3 && mod != null) break;
        }
        if (taken == 0) return;
        synchronized (LOCK) {
            totalSamples++;
            SectionSamples s = current.get(key);
            if (s == null) {
                s = new SectionSamples();
                current.put(key, s);
            }
            s.total++;
            inc(s.stackTops, top.toString());
            inc(s.modCode, mod == null ? "(only Minecraft / Forge code on the stack)" : mod);
        }
    }

    private static boolean isOwnFrame(StackTraceElement e) {
        return e.getClassName()
            .startsWith("fastloader.")
            || e.getMethodName()
                .contains("fastloader$");
    }

    private static boolean isModFrame(StackTraceElement e) {
        String c = e.getClassName();
        return !(c.startsWith("net.minecraft.") || c.startsWith("net.minecraftforge.")
            || c.startsWith("cpw.mods.fml.")
            || c.startsWith("java.")
            || c.startsWith("javax.")
            || c.startsWith("sun.")
            || c.startsWith("jdk.")
            || c.startsWith("com.google.")
            || c.startsWith("it.unimi.")
            || c.startsWith("fastloader."));
    }

    private static String shortName(StackTraceElement e) {
        String c = e.getClassName();
        if (c.startsWith("net.minecraft.") || c.startsWith("java.")) c = c.substring(c.lastIndexOf('.') + 1);
        return c + "." + e.getMethodName();
    }

    private static void inc(Map<String, long[]> map, String k) {
        long[] v = map.get(k);
        if (v == null) map.put(k, new long[] { 1 });
        else v[0]++;
    }

    // ================================================================ report

    static void appendReport(StringBuilder sb) {
        if (!enabled) return;
        List<Map.Entry<String, SectionSamples>> sections;
        long total;
        Map<String, SectionSamples> copy = new HashMap<>();
        synchronized (LOCK) {
            total = totalSamples;
            for (Map.Entry<String, SectionSamples> e : current.entrySet()) copy.put(e.getKey(), e.getValue().copy());
        }
        if (total == 0) return;
        sections = new ArrayList<>(copy.entrySet());
        sections.sort((a, b) -> Long.compare(b.getValue().total, a.getValue().total));
        sb.append(
            String.format(
                "%nStack samples of the server thread inside chunk work (every %d ms): %d samples, ~%.0f ms each%n",
                intervalMs,
                total,
                (double) intervalMs));
        sb.append("  Grouped by the innermost profiler section; 'code' = innermost mod method, 'top' = top of stack\n");
        int shown = 0;
        for (Map.Entry<String, SectionSamples> e : sections) {
            SectionSamples s = e.getValue();
            double pct = s.total * 100.0 / total;
            if (pct < 1.0 || shown >= 15) break;
            shown++;
            sb.append(String.format("  %5.1f%% %6d  %s%n", pct, s.total, e.getKey()));
            appendTop(sb, "code", s.modCode, s.total, 6);
            appendTop(sb, "top ", s.stackTops, s.total, 6);
        }
    }

    private static void appendTop(StringBuilder sb, String label, Map<String, long[]> map, long total, int n) {
        List<Map.Entry<String, long[]>> list = new ArrayList<>(map.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            Map.Entry<String, long[]> e = list.get(i);
            double pct = e.getValue()[0] * 100.0 / total;
            if (pct < 2.0) break;
            sb.append(String.format("           %s %5.1f%%  %s%n", label, pct, e.getKey()));
        }
    }

    private static final class SectionSamples {

        long total;
        final Map<String, long[]> stackTops = new HashMap<>();
        final Map<String, long[]> modCode = new HashMap<>();

        SectionSamples copy() {
            SectionSamples c = new SectionSamples();
            c.total = total;
            for (Map.Entry<String, long[]> e : stackTops.entrySet()) c.stackTops.put(e.getKey(), e.getValue().clone());
            for (Map.Entry<String, long[]> e : modCode.entrySet()) c.modCode.put(e.getKey(), e.getValue().clone());
            return c;
        }
    }
}
