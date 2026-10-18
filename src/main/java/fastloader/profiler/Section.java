package fastloader.profiler;

import java.util.ArrayList;
import java.util.List;

/** One timed node of the startup tree. Mutated only while holding the profiler lock. */
public final class Section {

    final Section parent;
    final String name;
    List<Section> children;

    long startNanos;
    long endNanos;
    long[] startStats;
    long[] endStats;
    boolean closed;
    String thread;

    /**
     * What the section is, for the report tables: "bar" / "step" (sampled FML splash bars, tag = bar title), "mod"
     * (one mod handling one FML event, tag = event class), "listener" (resource reload listener), or null.
     */
    String kind;
    String tag;
    int barSteps;

    Section(Section parent, String name, String thread) {
        this.parent = parent;
        this.name = name;
        this.thread = thread;
    }

    Section child(String name) {
        Section s = new Section(
            this,
            name,
            Thread.currentThread()
                .getName());
        if (children == null) children = new ArrayList<>();
        children.add(s);
        return s;
    }

    void open(long nanos, long[] stats) {
        startNanos = nanos;
        startStats = stats;
    }

    void close(long nanos, long[] stats) {
        if (closed) return;
        closed = true;
        endNanos = nanos;
        endStats = stats;
    }

    long durationNanos() {
        return (closed ? endNanos : System.nanoTime()) - startNanos;
    }

    /** Time not covered by any child section. */
    long selfNanos() {
        long d = durationNanos();
        if (children != null) for (Section c : children) d -= c.durationNanos();
        return Math.max(0, d);
    }

    long gcMillis() {
        return endStats == null ? 0 : endStats[0] - startStats[0];
    }

    long jitMillis() {
        return endStats == null ? 0 : endStats[1] - startStats[1];
    }

    long loadedClasses() {
        return endStats == null ? 0 : endStats[2] - startStats[2];
    }

    String path() {
        if (parent == null || parent.parent == null) return name;
        return parent.path() + " / " + name;
    }
}
