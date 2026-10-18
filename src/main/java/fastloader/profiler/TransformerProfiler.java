package fastloader.profiler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.launchwrapper.IClassNameTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/**
 * Wraps every ASM class transformer registered in the {@link LaunchClassLoader} with a timer. Class transformation is
 * one of the largest costs of starting a big 1.7.10 modpack, and this shows exactly which coremod is responsible.
 * <p>
 * The Mixin transformer proxy is left untouched (Mixin identifies it by type), so Mixin time is not included here.
 */
public final class TransformerProfiler {

    /**
     * Opt-in: replacing entries of the transformer list breaks coremods that look themselves up in it (AE2's
     * ApiRepairer recursed into itself). The stack sampler covers transformers without touching anything.
     */
    public static final boolean ENABLED = LoadProfiler.ENABLED
        && "true".equalsIgnoreCase(System.getProperty("fastloader.profiler.transformers"));

    private static final int SLOWEST_KEPT = 30;

    static final List<Timed> TIMED = new ArrayList<>();
    /** Single slowest (transformer, class) pairs, min-heap on time. */
    static final PriorityQueue<Slow> SLOWEST = new PriorityQueue<>((a, b) -> Long.compare(a.nanos, b.nanos));
    static String failure;

    private static boolean failed;
    private static Field transformersField;

    private TransformerProfiler() {}

    /** Wraps transformers that are not wrapped yet. Cheap to call repeatedly. */
    @SuppressWarnings("unchecked")
    public static synchronized void install() {
        if (!ENABLED || failed || !LoadProfiler.isActive()) return;
        try {
            LaunchClassLoader loader = Launch.classLoader;
            if (loader == null) return;
            if (transformersField == null) {
                transformersField = LaunchClassLoader.class.getDeclaredField("transformers");
                transformersField.setAccessible(true);
            }
            List<IClassTransformer> list = (List<IClassTransformer>) transformersField.get(loader);
            int wrapped = 0;
            for (int i = 0; i < list.size(); i++) {
                IClassTransformer t = list.get(i);
                if (t instanceof Timed || !shouldWrap(t)) continue;
                Timed timed = t instanceof IClassNameTransformer ? new TimedNaming(t) : new Timed(t);
                list.set(i, timed);
                TIMED.add(timed);
                wrapped++;
            }
            if (wrapped > 0) LoadProfiler.note("Timing " + wrapped + " new class transformer(s), total " + TIMED.size());
        } catch (Throwable t) {
            failed = true;
            failure = t.toString();
            LoadProfiler.note("Transformer timing unavailable: " + t);
        }
    }

    private static boolean shouldWrap(IClassTransformer t) {
        String name = t.getClass()
            .getName();
        return !name.startsWith("org.spongepowered.") && !name.contains(".mixin.transformer.");
    }

    static void recordSlow(String transformer, String className, long nanos) {
        synchronized (SLOWEST) {
            if (SLOWEST.size() < SLOWEST_KEPT) {
                SLOWEST.add(new Slow(transformer, className, nanos));
            } else if (SLOWEST.peek().nanos < nanos) {
                SLOWEST.poll();
                SLOWEST.add(new Slow(transformer, className, nanos));
            }
        }
    }

    static class Timed implements IClassTransformer {

        final IClassTransformer delegate;
        final String name;
        final AtomicLong nanos = new AtomicLong();
        final AtomicLong calls = new AtomicLong();
        /** Calls that actually returned different bytes, i.e. classes this transformer modified. */
        final AtomicLong changed = new AtomicLong();
        /** Class this transformer is currently transforming on this thread. */
        private final ThreadLocal<String> current = new ThreadLocal<>();

        Timed(IClassTransformer delegate) {
            this.delegate = delegate;
            this.name = delegate.getClass()
                .getName();
        }

        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            String outer = current.get();
            if (transformedName != null && transformedName.equals(outer)) {
                // The delegate walks the transformer list itself and normally skips its own entry, but cannot
                // recognise it behind this wrapper: behave as that skipped entry.
                return basicClass;
            }
            current.set(transformedName);
            long start = System.nanoTime();
            try {
                byte[] result = delegate.transform(name, transformedName, basicClass);
                if (result != basicClass) changed.incrementAndGet();
                return result;
            } finally {
                current.set(outer);
                long d = System.nanoTime() - start;
                nanos.addAndGet(d);
                calls.incrementAndGet();
                if (d > 2_000_000L) recordSlow(this.name, transformedName, d);
            }
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    /** Keeps the {@link IClassNameTransformer} contract, which FML and Mixin look up by type. */
    static final class TimedNaming extends Timed implements IClassNameTransformer {

        TimedNaming(IClassTransformer delegate) {
            super(delegate);
        }

        @Override
        public String unmapClassName(String name) {
            return ((IClassNameTransformer) delegate).unmapClassName(name);
        }

        @Override
        public String remapClassName(String name) {
            return ((IClassNameTransformer) delegate).remapClassName(name);
        }
    }

    static final class Slow {

        final String transformer;
        final String className;
        final long nanos;

        Slow(String transformer, String className, long nanos) {
            this.transformer = transformer;
            this.className = className;
            this.nanos = nanos;
        }
    }
}
