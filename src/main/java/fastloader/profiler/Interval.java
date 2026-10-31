package fastloader.profiler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Counters for one report interval. A fresh instance is swapped in after every report. */
final class Interval {

    final long startNanos = System.nanoTime();
    final long startMillis = System.currentTimeMillis();

    // chunk pipeline counters
    final LongAdder generated = new LongAdder();
    final LongAdder diskLoaded = new LongAdder();
    final LongAdder asyncFinished = new LongAdder();
    final LongAdder populated = new LongAdder();
    final LongAdder saved = new LongAdder();
    final LongAdder unloaded = new LongAdder();
    final LongAdder cascadeGenerated = new LongAdder();
    final LongAdder cascadeLoaded = new LongAdder();
    final LongAdder packetsBuilt = new LongAdder();
    final ConcurrentHashMap<Integer, DimCounters> perDim = new ConcurrentHashMap<>();
    /** culprit key -> [generated, loaded] */
    final ConcurrentHashMap<String, LongAdder[]> cascadeCulprits = new ConcurrentHashMap<>();

    /** caller signature -> count, for chunk loads requested outside other chunk work (sampled) */
    final ConcurrentHashMap<String, LongAdder> loadCallers = new ConcurrentHashMap<>();
    final AtomicInteger loadCallerSamples = new AtomicInteger();
    /** generated chunks by distance to the nearest player, see ChunkProfiler.DIST_BOUNDS; last slot = no player */
    final java.util.concurrent.atomic.AtomicLongArray genDistance = new java.util.concurrent.atomic.AtomicLongArray(
        ChunkProfiler.DIST_BOUNDS.length + 2);

    // timings
    final Histogram terrainHist = new Histogram();
    final Histogram populateHist = new Histogram();
    final Histogram generateTotalHist = new Histogram();
    final Histogram tickHist = new Histogram();

    // server tick stats (server thread only)
    long ticks;
    long slowTicks;
    long chunkWorkNs;
    long maxChunkWorkNs;

    // log rate limits
    final AtomicInteger slowChunkLogs = new AtomicInteger();
    final AtomicInteger cascadeLogs = new AtomicInteger();
    final AtomicInteger slowTickLogs = new AtomicInteger();

    DimCounters dim(int dim) {
        DimCounters c = perDim.get(dim);
        if (c == null) {
            c = new DimCounters();
            DimCounters prev = perDim.putIfAbsent(dim, c);
            if (prev != null) c = prev;
        }
        return c;
    }

    void cascade(String culprit, boolean generatedChunk) {
        LongAdder[] a = cascadeCulprits.get(culprit);
        if (a == null) {
            a = new LongAdder[] { new LongAdder(), new LongAdder() };
            LongAdder[] prev = cascadeCulprits.putIfAbsent(culprit, a);
            if (prev != null) a = prev;
        }
        a[generatedChunk ? 0 : 1].increment();
        (generatedChunk ? cascadeGenerated : cascadeLoaded).increment();
    }

    static final class DimCounters {

        final LongAdder generated = new LongAdder();
        final LongAdder loaded = new LongAdder();
        final LongAdder populated = new LongAdder();
        final LongAdder cascades = new LongAdder();
    }
}
