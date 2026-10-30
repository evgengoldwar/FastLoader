package fastloader.profiler;

import java.util.Arrays;

/**
 * One node of a call tree. Each tree is written by exactly one thread, other threads only read it for reports,
 * so plain fields are good enough (reports may be off by one call, never corrupted).
 */
final class ProfileNode {

    private static final ProfileNode[] EMPTY = new ProfileNode[0];

    final Section section;
    final String key;
    volatile ProfileNode[] children = EMPTY;

    long count;
    long totalNs;
    long selfNs;
    long maxNs;

    ProfileNode(Section section, String key) {
        this.section = section;
        this.key = key;
    }

    ProfileNode child(Section section, String key) {
        ProfileNode[] arr = children;
        // keys are cached strings, so identity almost always hits
        for (ProfileNode n : arr) {
            if (n.key == key) return n;
        }
        for (ProfileNode n : arr) {
            if (n.key.equals(key)) return n;
        }
        ProfileNode n = new ProfileNode(section, key);
        ProfileNode[] copy = Arrays.copyOf(arr, arr.length + 1);
        copy[arr.length] = n;
        children = copy;
        return n;
    }

    void record(long durationNs, long selfTimeNs) {
        count++;
        totalNs += durationNs;
        selfNs += selfTimeNs;
        if (durationNs > maxNs) maxNs = durationNs;
    }

    /** Adds another tree (same position) into this one. Used to merge threads with the same role for reports. */
    void mergeFrom(ProfileNode other) {
        count += other.count;
        totalNs += other.totalNs;
        selfNs += other.selfNs;
        if (other.maxNs > maxNs) maxNs = other.maxNs;
        for (ProfileNode oc : other.children) {
            child(oc.section, oc.key).mergeFrom(oc);
        }
    }

    long childrenTotalNs() {
        long sum = 0;
        for (ProfileNode c : children) sum += c.totalNs;
        return sum;
    }
}
