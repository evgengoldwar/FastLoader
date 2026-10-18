package fastloader.profiler;

import java.io.File;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.launchwrapper.Launch;

/**
 * Records the whole Minecraft startup as a tree of timed sections plus a flat list of milestones, so that the slow
 * parts of loading a modpack can be found and optimised.
 * <p>
 * Everything is written live to {@code logs/fastloader/latest-live.log} (useful when the game hangs or crashes during
 * loading) and summarised in {@code logs/fastloader/latest-report.txt} once loading has finished.
 * <p>
 * System properties:
 * <ul>
 * <li>{@code -Dfastloader.profiler=false} disables the profiler entirely</li>
 * <li>{@code -Dfastloader.profiler.transformers=false} disables per-transformer timing</li>
 * <li>{@code -Dfastloader.profiler.minMs=20} hides sections faster than this in the report tree</li>
 * </ul>
 */
public final class LoadProfiler {

    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("fastloader.profiler"));
    static final long MIN_REPORT_NANOS = Long.getLong("fastloader.profiler.minMs", 20L) * 1_000_000L;

    private static final Object LOCK = new Object();

    /** Wall clock time of JVM start, in epoch millis. */
    static long jvmStartMillis;
    /** {@link System#nanoTime()} at profiler init, and the epoch millis that correspond to it. */
    static long initNanos;
    static long initMillis;

    static final Section ROOT = new Section(null, "Minecraft startup", null);
    static final List<Milestone> MILESTONES = new ArrayList<>();

    private static final Map<Thread, Deque<Section>> STACKS = new HashMap<>();
    private static final Map<Object, Section> BARS = new IdentityHashMap<>();
    private static final Map<Object, Section> BAR_STEPS = new IdentityHashMap<>();
    private static final Map<Object, Integer> BAR_STEP_INDEX = new IdentityHashMap<>();
    private static final Map<String, Section> NAMED = new HashMap<>();

    private static boolean initialized;
    private static boolean finished;
    static File outputDir;
    private static LiveLog live;

    private static List<GarbageCollectorMXBean> gcBeans;
    private static CompilationMXBean jitBean;
    private static ClassLoadingMXBean classBean;
    private static MemoryMXBean memoryBean;

    private LoadProfiler() {}

    /** Called as early as possible, from the FastLoader coremod constructor. Safe to call more than once. */
    public static void init() {
        if (!ENABLED) return;
        synchronized (LOCK) {
            if (initialized) return;
            initialized = true;
            initNanos = System.nanoTime();
            initMillis = System.currentTimeMillis();
            try {
                jvmStartMillis = ManagementFactory.getRuntimeMXBean()
                    .getStartTime();
            } catch (Throwable t) {
                jvmStartMillis = initMillis;
            }
            gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            jitBean = ManagementFactory.getCompilationMXBean();
            classBean = ManagementFactory.getClassLoadingMXBean();
            memoryBean = ManagementFactory.getMemoryMXBean();

            File home = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
            outputDir = new File(new File(home, "logs"), "fastloader");
            live = new LiveLog(new File(outputDir, "latest-live.log"));
            ROOT.open(initNanos, snapshot());
            ROOT.thread = Thread.currentThread()
                .getName();

            live.line(
                String.format(
                    "FastLoader startup profiler. JVM started %d ms before the FastLoader coremod was loaded.",
                    initMillis - jvmStartMillis));
            live.line("Format: [ms since JVM start] event");
        }
        milestone("JVM start", jvmStartMillis);
        milestone("FastLoader coremod loaded");
    }

    public static boolean isActive() {
        return ENABLED && initialized && !finished;
    }

    // ---------------------------------------------------------------- milestones

    public static void milestone(String name) {
        if (!isActive()) return;
        long now = System.nanoTime();
        synchronized (LOCK) {
            Milestone m = new Milestone(name, now, snapshot());
            MILESTONES.add(m);
            live.line(String.format("[%8d] ==== %s", toJvmMillis(now), name));
        }
    }

    private static void milestone(String name, long epochMillis) {
        synchronized (LOCK) {
            long nanos = initNanos - (initMillis - epochMillis) * 1_000_000L;
            MILESTONES.add(new Milestone(name, nanos, new long[4]));
        }
    }

    // ---------------------------------------------------------------- generic sections

    /** Opens a section on the current thread; it nests under whatever section is currently open on this thread. */
    public static Section begin(String name) {
        if (!isActive()) return null;
        long now = System.nanoTime();
        synchronized (LOCK) {
            Deque<Section> stack = stack();
            Section parent = stack.isEmpty() ? ROOT : stack.peek();
            Section s = parent.child(name);
            s.open(now, snapshot());
            stack.push(s);
            live.line(String.format("[%8d] %s> %s", toJvmMillis(now), indent(s), name));
            return s;
        }
    }

    public static void end(Section s) {
        if (s == null || !ENABLED || !initialized) return;
        long now = System.nanoTime();
        synchronized (LOCK) {
            closeUpTo(s, now);
        }
    }

    /** Named sections are for begin/end pairs that live in different methods (e.g. mixin HEAD and RETURN). */
    public static void begin(String key, String name) {
        Section s = begin(name);
        if (s == null) return;
        synchronized (LOCK) {
            NAMED.put(key, s);
        }
    }

    public static void endNamed(String key) {
        Section s;
        synchronized (LOCK) {
            s = NAMED.remove(key);
        }
        end(s);
    }

    // ---------------------------------------------------------------- FML ProgressManager bars

    public static void onBarPush(Object bar, String title, int steps) {
        if (!isActive()) return;
        synchronized (LOCK) {
            // both push() overloads are hooked and one calls the other
            if (BARS.containsKey(bar)) return;
        }
        Section s = begin(title);
        if (s == null) return;
        s.isBar = true;
        s.barTitle = title;
        s.barSteps = steps;
        synchronized (LOCK) {
            BARS.put(bar, s);
        }
        // New transformers may have been registered since the last check
        TransformerProfiler.install();
    }

    public static void onBarStep(Object bar, int stepIndex, String message) {
        if (!isActive()) return;
        long now = System.nanoTime();
        synchronized (LOCK) {
            Section barSection = BARS.get(bar);
            if (barSection == null) return;
            // both step() overloads are hooked and one calls the other
            Integer last = BAR_STEP_INDEX.put(bar, stepIndex);
            if (last != null && last == stepIndex) return;
            Section prev = BAR_STEPS.remove(bar);
            if (prev != null) closeUpTo(prev, now);
            // make sure we nest under the bar even if something left a section open
            Deque<Section> stack = stack();
            while (!stack.isEmpty() && stack.peek() != barSection && stack.contains(barSection)) {
                stack.pop()
                    .close(now, snapshot());
            }
            Section step = barSection.child(message == null ? "<null>" : message);
            step.open(now, snapshot());
            if (stack.peek() == barSection) stack.push(step);
            BAR_STEPS.put(bar, step);
        }
    }

    public static void onBarPop(Object bar) {
        if (!ENABLED || !initialized) return;
        long now = System.nanoTime();
        synchronized (LOCK) {
            BAR_STEP_INDEX.remove(bar);
            Section step = BAR_STEPS.remove(bar);
            if (step != null) closeUpTo(step, now);
            Section s = BARS.remove(bar);
            if (s != null) closeUpTo(s, now);
        }
    }

    // ---------------------------------------------------------------- finish

    /** Ends profiling and writes the report. Only the first call has an effect. */
    public static void finish(String reason) {
        if (!isActive()) return;
        milestone(reason);
        long now = System.nanoTime();
        synchronized (LOCK) {
            finished = true;
            for (Deque<Section> stack : STACKS.values()) {
                while (!stack.isEmpty()) stack.pop()
                    .close(now, snapshot());
            }
            ROOT.close(now, snapshot());
            live.line(String.format("[%8d] Startup finished: %s", toJvmMillis(now), reason));
            try {
                File report = ReportWriter.write();
                live.line("Report written to " + report.getAbsolutePath());
                System.out.println(
                    String.format(
                        "[FastLoader] Startup took %.2f s (since JVM start). Report: %s",
                        (now - jvmStartNanos()) / 1e9,
                        report.getAbsolutePath()));
            } catch (Throwable t) {
                live.line("Failed to write report: " + t);
                t.printStackTrace();
            }
            live.close();
        }
    }

    /** Appends a line to the live log after startup has finished (e.g. world loading times). */
    public static void note(String text) {
        if (!ENABLED || !initialized) return;
        synchronized (LOCK) {
            LiveLog l = live;
            if (finished) {
                l = new LiveLog(new File(outputDir, "latest-live.log"), true);
                l.line(String.format("[%8d] %s", toJvmMillis(System.nanoTime()), text));
                l.close();
            } else {
                l.line(String.format("[%8d] %s", toJvmMillis(System.nanoTime()), text));
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    static long jvmStartNanos() {
        return initNanos - (initMillis - jvmStartMillis) * 1_000_000L;
    }

    static long toJvmMillis(long nanos) {
        return (nanos - jvmStartNanos()) / 1_000_000L;
    }

    private static Deque<Section> stack() {
        return STACKS.computeIfAbsent(Thread.currentThread(), t -> new ArrayDeque<>());
    }

    private static void closeUpTo(Section s, long now) {
        if (s.closed) return;
        for (Deque<Section> stack : STACKS.values()) {
            if (stack.contains(s)) {
                while (!stack.isEmpty()) {
                    Section top = stack.pop();
                    top.close(now, snapshot());
                    logEnd(top);
                    if (top == s) return;
                }
            }
        }
        s.close(now, snapshot());
        logEnd(s);
    }

    private static void logEnd(Section s) {
        // only log ends of sections that are long enough to be interesting, to keep the live log readable
        if (s.durationNanos() >= MIN_REPORT_NANOS || s.children != null) {
            live.line(
                String.format(
                    "[%8d] %s< %s  %d ms",
                    toJvmMillis(s.endNanos),
                    indent(s),
                    s.name,
                    s.durationNanos() / 1_000_000L));
        }
    }

    private static String indent(Section s) {
        StringBuilder sb = new StringBuilder();
        for (Section p = s.parent; p != null && p != ROOT; p = p.parent) sb.append("  ");
        return sb.toString();
    }

    /** gcMillis, jitMillis, loadedClasses, heapUsedBytes */
    static long[] snapshot() {
        long gc = 0;
        for (GarbageCollectorMXBean b : gcBeans) {
            long t = b.getCollectionTime();
            if (t > 0) gc += t;
        }
        long jit = jitBean != null && jitBean.isCompilationTimeMonitoringSupported()
            ? jitBean.getTotalCompilationTime()
            : 0;
        long heap = 0;
        try {
            heap = memoryBean.getHeapMemoryUsage()
                .getUsed();
        } catch (Throwable ignored) {}
        return new long[] { gc, jit, classBean.getTotalLoadedClassCount(), heap };
    }

    static final class Milestone {

        final String name;
        final long nanos;
        final long[] stats;

        Milestone(String name, long nanos, long[] stats) {
            this.name = name;
            this.nanos = nanos;
            this.stats = stats;
        }
    }
}
