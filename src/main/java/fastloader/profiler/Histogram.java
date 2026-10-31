package fastloader.profiler;

import java.util.concurrent.atomic.AtomicLongArray;

/** Fixed-bucket millisecond histogram with approximate percentiles. */
final class Histogram {

    private static final double[] BOUNDS_MS = { 0.5, 1, 2, 5, 10, 20, 50, 100, 200, 500, 1000 };

    private final AtomicLongArray buckets = new AtomicLongArray(BOUNDS_MS.length + 1);
    private final AtomicLongArray sums = new AtomicLongArray(2); // [count, totalNs]
    private volatile long maxNs;

    void add(long ns) {
        double ms = ns / 1e6;
        int i = 0;
        while (i < BOUNDS_MS.length && ms >= BOUNDS_MS[i]) i++;
        buckets.incrementAndGet(i);
        sums.incrementAndGet(0);
        sums.addAndGet(1, ns);
        if (ns > maxNs) maxNs = ns;
    }

    long count() {
        return sums.get(0);
    }

    double avgMs() {
        long c = sums.get(0);
        return c == 0 ? 0 : sums.get(1) / 1e6 / c;
    }

    double maxMs() {
        return maxNs / 1e6;
    }

    /** Upper bound of the bucket that contains the given percentile. */
    String percentile(double p) {
        long c = count();
        if (c == 0) return "-";
        long target = (long) Math.ceil(c * p);
        long acc = 0;
        for (int i = 0; i < buckets.length(); i++) {
            acc += buckets.get(i);
            if (acc >= target) {
                return i < BOUNDS_MS.length ? "<" + fmt(BOUNDS_MS[i]) : ">=" + fmt(BOUNDS_MS[BOUNDS_MS.length - 1]);
            }
        }
        return "-";
    }

    String describe() {
        if (count() == 0) return "no samples";
        StringBuilder sb = new StringBuilder();
        sb.append(
            String.format(
                "n=%d avg=%.2fms p50%s p90%s p99%s max=%.2fms |",
                count(),
                avgMs(),
                percentile(0.5),
                percentile(0.9),
                percentile(0.99),
                maxMs()));
        for (int i = 0; i < buckets.length(); i++) {
            long b = buckets.get(i);
            if (b == 0) continue;
            String label = i < BOUNDS_MS.length ? "<" + fmt(BOUNDS_MS[i]) : ">=" + fmt(BOUNDS_MS[BOUNDS_MS.length - 1]);
            sb.append(' ')
                .append(label)
                .append("ms:")
                .append(b);
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        return v == (long) v ? Long.toString((long) v) : Double.toString(v);
    }
}
