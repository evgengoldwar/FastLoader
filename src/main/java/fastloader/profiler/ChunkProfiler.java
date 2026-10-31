package fastloader.profiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

import fastloader.FastLoader;

/**
 * Hierarchical profiler for the whole chunk pipeline (load, generate, populate, light, save, send).
 * <p>
 * Mixins call {@link #push} at the start of a step and {@link #pop} at its end. Every thread keeps its own stack and
 * its own call tree, so the hot path never synchronizes. On top of the tree the profiler tracks:
 * <ul>
 * <li>per-chunk breakdowns of slow chunk generations / populations,</li>
 * <li>cascading worldgen (a chunk being generated or loaded while another chunk is populated) and who caused it,</li>
 * <li>per-tick chunk work on the server thread and slow ticks,</li>
 * <li>periodic reports (log + file) with the call tree and the top offenders.</li>
 * </ul>
 */
public final class ChunkProfiler {

    private ChunkProfiler() {}

    // ---------------------------------------------------------------- settings (see Config)
    public static volatile boolean enabled = true;
    public static volatile boolean profileLighting = true;
    public static volatile boolean countSetBlock = true;
    static final int LIGHT_SAMPLE_RATE = 32;
    public static volatile boolean profileEvents = true;
    public static volatile long slowChunkNs = 50_000_000L;
    public static volatile long slowTickNs = 50_000_000L;
    public static volatile long reportIntervalNs = 60_000_000_000L;
    public static volatile int maxLogsPerInterval = 20;
    /** A report is written this long after the last generated chunk. */
    public static volatile long idleFlushNs = 10_000_000_000L;
    public static volatile boolean logCascades = true;

    // ---------------------------------------------------------------- frame flags
    static final int F_GENERATED = 1;
    static final int F_DISK = 2;
    static final int F_POPULATED = 4;

    private static final int MAX_TREE_DEPTH = 48;

    // ---------------------------------------------------------------- state
    /** Bumped on reset: threads start a new tree the next time they are idle (depth 0). */
    static volatile int epoch;
    /** Bumped when the profiler is switched off: every stack is dropped immediately. */
    static volatile int hardEpoch;

    static final List<ThreadState> STATES = new CopyOnWriteArrayList<>();
    /** Only threads that actually pushed a frame get a state (pop / nested checks never create one). */
    private static final ThreadLocal<ThreadState> TL = new ThreadLocal<>();

    static volatile Interval interval = new Interval();
    static volatile Thread serverThread;

    // session totals (never reset)
    static final LongAdder totalGenerated = new LongAdder();
    static final LongAdder totalLoaded = new LongAdder();
    static final LongAdder totalPopulated = new LongAdder();
    static final LongAdder totalCascades = new LongAdder();

    // per-tick data, server thread only
    private static long tickStartNs;
    private static long lastGenerated;
    private static long lastGenerationNs;
    private static long tickChunkNs;
    private static int tickGenerated, tickLoaded, tickPopulated, tickCascades;
    private static final HashMap<String, long[]> tickSelf = new HashMap<>();
    private static boolean tickDirty;
    private static final Map<Integer, String[]> worldKeys = new HashMap<>();

    // ================================================================ public API used by mixins

    public static void push(Section s) {
        if (enabled) push0(s, s.label, false, 0, 0, 0);
    }

    public static void push(Section s, String key) {
        if (enabled) push0(s, key, false, 0, 0, 0);
    }

    /** Only records the section when it runs inside another profiled section (lighting, events...). */
    public static void pushNested(Section s) {
        if (enabled) {
            ThreadState st = TL.get();
            if (st != null && st.depth > 0 && st.hardEpoch == hardEpoch) push0(s, s.label, false, 0, 0, 0);
        }
    }

    public static void pushNested(Section s, String key) {
        if (enabled) {
            ThreadState st = TL.get();
            if (st != null && st.depth > 0 && st.hardEpoch == hardEpoch) push0(s, key, false, 0, 0, 0);
        }
    }

    public static void pushChunk(Section s, int dim, int x, int z) {
        if (enabled) push0(s, s.label, true, dim, x, z);
    }

    public static void pushChunk(Section s, String key, int dim, int x, int z) {
        if (enabled) push0(s, key, true, dim, x, z);
    }

    /**
     * Counts a very frequent call against the running section without timing it (two nanoTime calls per light
     * update would cost more than many of the updates themselves).
     */
    public static void count(Section s) {
        if (!enabled) return;
        ThreadState st = TL.get();
        if (st == null || st.depth == 0 || st.hardEpoch != hardEpoch) return;
        ProfileNode n = st.stack[st.depth - 1].node;
        if (n != null) n.child(s, s.label).count++;
    }

    /**
     * Light update entry: always counted, every {@link #LIGHT_SAMPLE_RATE}th call inside chunk work is timed.
     * @return true if a sample frame was pushed (the caller must pop {@link Section#LIGHT_SAMPLE})
     */
    public static boolean lightUpdate() {
        if (!enabled) return false;
        ThreadState st = TL.get();
        if (st == null || st.depth == 0 || st.hardEpoch != hardEpoch) return false;
        ProfileNode n = st.stack[st.depth - 1].node;
        if (n != null) n.child(Section.LIGHT_UPDATE, Section.LIGHT_UPDATE.label).count++;
        if (++st.lightCalls % LIGHT_SAMPLE_RATE != 0) return false;
        push0(Section.LIGHT_SAMPLE, Section.LIGHT_SAMPLE.label, false, 0, 0, 0);
        return true;
    }

    /** Ends the most recent frame of the given section (unwinding frames left behind by exceptions). */
    public static void pop(Section s) {
        ThreadState st = TL.get();
        if (st == null || st.depth == 0) return;
        if (st.hardEpoch != hardEpoch) {
            st.clear();
            return;
        }
        int top = st.depth - 1;
        if (st.stack[top].section != s) {
            int j = top - 1;
            while (j >= 0 && st.stack[j].section != s) j--;
            if (j < 0) return; // push was skipped (profiler toggled / nested-only section)
            long now = System.nanoTime();
            while (st.depth - 1 > j) finish(st, now);
        }
        finish(st, System.nanoTime());
    }

    /** {@code safeLoadChunk} or a sync ChunkIO load returned a chunk from disk. */
    public static void onDiskLoad(int dim) {
        if (!enabled) return;
        Interval iv = interval;
        iv.diskLoaded.increment();
        iv.dim(dim).loaded.increment();
        totalLoaded.increment();
        if (Thread.currentThread() == serverThread) tickLoaded++;
        markLoadFrames(F_DISK);
    }

    /**
     * {@code AnvilChunkLoader.loadEntities}: when it runs directly inside ChunkIO stage 2 the chunk was really read from
     * disk, so that frame gets its coordinates and the load is counted.
     */
    public static void onLoadEntities(int dim, int x, int z) {
        if (!enabled) return;
        ThreadState st = TL.get();
        if (st == null || st.depth == 0) return;
        Frame top = st.stack[st.depth - 1];
        if (top.section != Section.ASYNC_FINISH) return;
        top.coords = true;
        top.dim = dim;
        top.x = x;
        top.z = z;
        Interval iv = interval;
        iv.asyncFinished.increment();
        iv.dim(dim).loaded.increment();
        totalLoaded.increment();
        if (Thread.currentThread() == serverThread) tickLoaded++;
        markLoadFrames(F_DISK);
    }

    public static void onChunkSaved() {
        if (!enabled) return;
        interval.saved.increment();
    }

    public static void onChunkUnloaded() {
        if (!enabled) return;
        interval.unloaded.increment();
    }

    public static void onPacketBuilt() {
        if (!enabled) return;
        interval.packetsBuilt.increment();
    }

    public static void onServerTickStart() {
        Thread t = Thread.currentThread();
        serverThread = t;
        ThreadState st = TL.get();
        if (st != null && st.depth != 0) {
            // something threw inside a profiled section last tick, drop the stale frames
            st.clear();
        }
        tickStartNs = System.nanoTime();
        tickChunkNs = 0;
        tickGenerated = tickLoaded = tickPopulated = tickCascades = 0;
        if (tickDirty) {
            for (long[] v : tickSelf.values()) {
                v[0] = 0;
                v[1] = 0;
            }
            tickDirty = false;
        }
    }

    public static void onServerTickEnd() {
        if (serverThread != Thread.currentThread()) return;
        long now = System.nanoTime();
        long dur = now - tickStartNs;
        Interval iv = interval;
        if (enabled) {
            iv.ticks++;
            iv.tickHist.add(dur);
            iv.chunkWorkNs += tickChunkNs;
            if (tickChunkNs > iv.maxChunkWorkNs) iv.maxChunkWorkNs = tickChunkNs;
            if (dur >= slowTickNs) {
                iv.slowTicks++;
                if (iv.slowTickLogs.incrementAndGet() <= maxLogsPerInterval) logSlowTick(dur);
            }
        }
        // automatic reports: only when chunks were generated, and as soon as generation stops
        long generated = iv.generated.sum();
        if (generated != lastGenerated) {
            lastGenerated = generated;
            lastGenerationNs = now;
        }
        if (generated > 0 && enabled && now - lastGenerationNs >= idleFlushNs) {
            ProfileReport.periodic(iv, "chunk generation stopped");
            resetInterval();
        } else if (now - iv.startNanos >= reportIntervalNs) {
            if (enabled && generated > 0) ProfileReport.periodic(iv, "interval");
            resetInterval();
        }
    }

    /** Time spent in a world's tick / entity update, only shown in the slow tick breakdown. */
    public static void addTickTime(int dim, int what, long ns) {
        if (!enabled || serverThread != Thread.currentThread()) return;
        String[] keys = worldKeys.get(dim);
        if (keys == null) {
            keys = new String[] { "[world dim " + dim + "] WorldServer.tick (incl. chunk work)",
                "[world dim " + dim + "] updateEntities", "[world dim " + dim + "] block/chunk ticking" };
            worldKeys.put(dim, keys);
        }
        tickAdd(keys[what], ns, false);
    }

    /** True when the running interval contains generated chunks (i.e. a report would say something). */
    public static boolean hasGeneration() {
        return interval.generated.sum() > 0;
    }

    public static void resetInterval() {
        interval = new Interval();
        epoch++;
        lastGenerated = 0;
    }

    public static void setEnabled(boolean on) {
        if (!on) hardEpoch++;
        enabled = on;
        if (on) resetInterval();
    }

    // ================================================================ internals

    private static void push0(Section s, String key, boolean coords, int dim, int x, int z) {
        ThreadState st = TL.get();
        if (st == null) {
            st = new ThreadState(Thread.currentThread());
            TL.set(st);
            STATES.add(st);
        }
        if (st.hardEpoch != hardEpoch) st.clear();
        if (st.depth == 0 && st.epoch != epoch) st.newTree(epoch);
        if (st.depth == st.stack.length) st.grow();
        Frame f = st.stack[st.depth];
        ProfileNode parent = st.depth == 0 ? st.root : st.stack[st.depth - 1].node;
        f.section = s;
        f.key = key;
        f.node = parent == null || st.depth >= MAX_TREE_DEPTH ? null : parent.child(s, key);
        f.child = 0;
        f.flags = 0;
        f.coords = coords;
        f.dim = dim;
        f.x = x;
        f.z = z;
        f.detail = null;
        st.depth++;

        switch (s) {
            case TERRAIN:
                markLoadFrames(F_GENERATED);
                attachDetailToNearest(st, Section.LOAD_ORIGINAL);
                break;
            case PROVIDER_POPULATE: {
                Frame p = nearest(st, Section.POPULATE, st.depth - 2);
                if (p != null) p.flags |= F_POPULATED;
                break;
            }
            case POPULATE:
                // standalone population (not inside a chunk generation that already collects details)
                if (st.detailFrames == 0) {
                    f.detail = new HashMap<>();
                    st.detailFrames++;
                }
                break;
            default:
                break;
        }
        // start the clock last so bookkeeping is not billed to the section
        f.start = System.nanoTime();
    }

    private static void finish(ThreadState st, long now) {
        Frame f = st.stack[--st.depth];
        long dur = now - f.start;
        long self = dur - f.child;
        if (self < 0) self = 0;
        if (st.depth > 0) st.stack[st.depth - 1].child += dur;
        if (f.node != null) f.node.record(dur, self);

        if (f.detail != null) {
            st.detailFrames--;
            mergeDetail(f.detail, f.key, self);
        }
        if (st.detailFrames > 0) {
            for (int i = st.depth - 1; i >= 0; i--) {
                Frame a = st.stack[i];
                if (a.detail != null) mergeDetail(a.detail, f.key, self);
            }
        }

        boolean server = st.thread == serverThread;
        if (server) {
            tickAdd(f.key, self, true);
            if (st.depth == 0) tickChunkNs += dur;
        }

        if (onFinish(st, f, dur, server)) {
            // a log line was written: don't bill its cost to the frames that are still running
            long spent = System.nanoTime() - now;
            for (int i = 0; i < st.depth; i++) st.stack[i].start += spent;
        }

        f.detail = null;
        f.node = null;
        f.key = null;
    }

    /** @return true if something was logged */
    private static boolean onFinish(ThreadState st, Frame f, long dur, boolean server) {
        Interval iv = interval;
        boolean logged = false;
        switch (f.section) {
            case TERRAIN:
                iv.generated.increment();
                totalGenerated.increment();
                iv.terrainHist.add(dur);
                Frame load = nearest(st, Section.LOAD_ORIGINAL, st.depth - 1);
                if (load != null && load.coords) iv.dim(load.dim).generated.increment();
                if (server) tickGenerated++;
                break;
            case POPULATE:
                if ((f.flags & F_POPULATED) != 0) {
                    iv.populated.increment();
                    totalPopulated.increment();
                    iv.populateHist.add(dur);
                    if (f.coords) iv.dim(f.dim).populated.increment();
                    if (server) tickPopulated++;
                    if (f.detail != null && dur >= slowChunkNs) logged = logSlowChunk(st, f, dur, "populate");
                }
                break;
            case LOAD_CHUNK:
            case LOAD_ORIGINAL:
            case ASYNC_FINISH:
                if ((f.flags & (F_GENERATED | F_DISK)) != 0) logged = checkCascade(st, f, server);
                if (f.section == Section.LOAD_ORIGINAL && (f.flags & F_GENERATED) != 0) {
                    iv.generateTotalHist.add(dur);
                    if (f.detail != null && dur >= slowChunkNs) logged |= logSlowChunk(st, f, dur, "generate");
                }
                break;
            default:
                break;
        }
        return logged;
    }

    /** Flags all load frames of the chunk currently being loaded (up to the populate boundary of the parent). */
    private static void markLoadFrames(int flag) {
        ThreadState st = TL.get();
        if (!enabled || st == null) return;
        for (int i = st.depth - 1; i >= 0; i--) {
            Frame f = st.stack[i];
            if (f.section.isPopulateBoundary()) break;
            if (f.section.isLoad()) f.flags |= flag;
        }
    }

    private static void attachDetailToNearest(ThreadState st, Section s) {
        Frame f = nearest(st, s, st.depth - 2);
        if (f != null && f.detail == null) {
            f.detail = new HashMap<>();
            st.detailFrames++;
        }
    }

    private static Frame nearest(ThreadState st, Section s, int from) {
        for (int i = from; i >= 0; i--) {
            Frame f = st.stack[i];
            if (f.section == s) return f;
            if (f.section.isPopulateBoundary() && s != Section.POPULATE) return null;
        }
        return null;
    }

    private static boolean checkCascade(ThreadState st, Frame f, boolean server) {
        // only count the outermost load frame of this chunk, and only when it sits inside a population
        int boundary = -1;
        for (int j = st.depth - 1; j >= 0; j--) {
            Section s = st.stack[j].section;
            if (s.isLoad()) return false;
            if (s.isPopulateBoundary()) {
                boundary = j;
                break;
            }
        }
        if (boundary < 0) return false;

        String culprit = st.stack[boundary].key;
        for (int j = st.depth - 1; j > boundary; j--) {
            Frame c = st.stack[j];
            if (c.section.isAttributable()) {
                culprit = c.key;
                break;
            }
        }
        boolean generated = (f.flags & F_GENERATED) != 0;
        Interval iv = interval;
        iv.cascade(culprit, generated);
        totalCascades.increment();
        if (f.coords) iv.dim(f.dim).cascades.increment();
        if (server) tickCascades++;

        if (logCascades && iv.cascadeLogs.incrementAndGet() <= maxLogsPerInterval) {
            Frame pop = null;
            for (int j = boundary; j >= 0; j--) {
                if (st.stack[j].section == Section.POPULATE && st.stack[j].coords) {
                    pop = st.stack[j];
                    break;
                }
            }
            StringBuilder sb = new StringBuilder("[cascade] chunk ");
            appendCoords(sb, f);
            sb.append(generated ? " was GENERATED" : " was LOADED from disk");
            sb.append(" while populating ");
            if (pop != null) appendCoords(sb, pop);
            else sb.append("?");
            sb.append(" | culprit: ")
                .append(culprit)
                .append(" | path: ");
            int start = Math.max(0, st.depth - 12);
            for (int j = start; j <= st.depth; j++) {
                if (j > start) sb.append(" > ");
                Frame p = st.stack[j];
                sb.append(p.key);
                if (p.coords) {
                    sb.append(' ');
                    appendCoords(sb, p);
                }
            }
            FastLoader.LOG.info(sb.toString());
            return true;
        }
        return false;
    }

    private static boolean logSlowChunk(ThreadState st, Frame f, long dur, String what) {
        Interval iv = interval;
        if (iv.slowChunkLogs.incrementAndGet() > maxLogsPerInterval) return false;
        StringBuilder sb = new StringBuilder("[slow chunk] ").append(what)
            .append(' ');
        appendCoords(sb, f);
        sb.append(String.format(" took %.1f ms on %s", dur / 1e6, st.thread.getName()));
        boolean nested = false;
        for (int i = st.depth - 1; i >= 0; i--) {
            if (st.stack[i].section.isPopulateBoundary()) {
                nested = true;
                break;
            }
        }
        if (nested) sb.append(" (cascaded from another chunk's population)");
        sb.append(" | self time:");
        appendTop(sb, f.detail, 10, dur);
        FastLoader.LOG.info(sb.toString());
        return true;
    }

    private static void logSlowTick(long dur) {
        StringBuilder sb = new StringBuilder();
        sb.append(
            String.format(
                "[slow tick] %.1f ms (chunk work %.1f ms) | chunks: generated %d, loaded %d, populated %d, cascades %d | top:",
                dur / 1e6,
                tickChunkNs / 1e6,
                tickGenerated,
                tickLoaded,
                tickPopulated,
                tickCascades));
        appendTop(sb, tickSelf, 8, dur);
        FastLoader.LOG.info(sb.toString());
    }

    private static void appendTop(StringBuilder sb, Map<String, long[]> map, int n, long totalNs) {
        List<Map.Entry<String, long[]>> list = new ArrayList<>();
        for (Map.Entry<String, long[]> e : map.entrySet()) {
            if (e.getValue()[0] > 0) list.add(e);
        }
        list.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            Map.Entry<String, long[]> e = list.get(i);
            long[] v = e.getValue();
            sb.append(
                String.format(
                    "%n    %8.2f ms %5.1f%% %6dx  %s",
                    v[0] / 1e6,
                    totalNs == 0 ? 0 : v[0] * 100.0 / totalNs,
                    v[1],
                    e.getKey()));
        }
    }

    private static void appendCoords(StringBuilder sb, Frame f) {
        if (!f.coords) return;
        sb.append("dim ")
            .append(f.dim)
            .append(" [")
            .append(f.x)
            .append(", ")
            .append(f.z)
            .append("] (block ")
            .append(f.x << 4)
            .append(", ")
            .append(f.z << 4)
            .append(')');
    }

    private static void mergeDetail(HashMap<String, long[]> map, String key, long self) {
        long[] v = map.get(key);
        if (v == null) {
            v = new long[2];
            map.put(key, v);
        }
        v[0] += self;
        v[1]++;
    }

    private static void tickAdd(String key, long ns, boolean countCall) {
        long[] v = tickSelf.get(key);
        if (v == null) {
            v = new long[2];
            tickSelf.put(key, v);
        }
        v[0] += ns;
        if (countCall) v[1]++;
        tickDirty = true;
    }

    // ================================================================ per-thread state

    static final class Frame {

        Section section;
        String key;
        ProfileNode node;
        long start;
        long child;
        int flags;
        boolean coords;
        int dim, x, z;
        HashMap<String, long[]> detail;
    }

    static final class ThreadState {

        final Thread thread;
        final String role;
        volatile ProfileNode root = new ProfileNode(null, "root");
        volatile int epoch = ChunkProfiler.epoch;
        int hardEpoch = ChunkProfiler.hardEpoch;
        Frame[] stack = new Frame[32];
        int depth;
        int detailFrames;
        int lightCalls;

        ThreadState(Thread thread) {
            this.thread = thread;
            this.role = roleOf(thread.getName());
            for (int i = 0; i < stack.length; i++) stack[i] = new Frame();
        }

        void grow() {
            Frame[] n = new Frame[stack.length * 2];
            System.arraycopy(stack, 0, n, 0, stack.length);
            for (int i = stack.length; i < n.length; i++) n[i] = new Frame();
            stack = n;
        }

        void newTree(int newEpoch) {
            root = new ProfileNode(null, "root");
            epoch = newEpoch;
        }

        void clear() {
            for (int i = 0; i < depth; i++) {
                stack[i].detail = null;
                stack[i].node = null;
            }
            depth = 0;
            detailFrames = 0;
            hardEpoch = ChunkProfiler.hardEpoch;
        }

        private static String roleOf(String name) {
            // "Chunk I/O Executor Thread-3" -> "Chunk I/O Executor Thread"
            int end = name.length();
            while (end > 0 && Character.isDigit(name.charAt(end - 1))) end--;
            while (end > 0 && (name.charAt(end - 1) == '-' || name.charAt(end - 1) == '#'
                || name.charAt(end - 1) == ' ')) end--;
            return end == 0 ? name : name.substring(0, end);
        }
    }
}
