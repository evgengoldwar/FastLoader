package fastloader.profiler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cpw.mods.fml.common.ProgressManager;
import cpw.mods.fml.common.ProgressManager.ProgressBar;

/**
 * Rebuilds the FML splash screen bars (mod construction/preInit/init/postInit with one step per mod, resource
 * reload with one step per listener, texture stitching, ...) by reading {@link ProgressManager} every few ms.
 * <p>
 * ProgressManager is loaded by other coremods before Mixin is ready in typical modpacks, so it cannot be patched;
 * reading it from a background thread needs no transformation at all.
 */
final class ProgressSampler implements Runnable {

    private static final long PERIOD_MS = Long.getLong("fastloader.profiler.sampleMs", 5L);

    private static Thread thread;
    private static volatile boolean running;

    /** One level of the bar stack as currently recorded. */
    private static final class Level {

        final ProgressBar bar;
        final Section barSection;
        int step;
        Section stepSection;

        Level(ProgressBar bar, Section barSection) {
            this.bar = bar;
            this.barSection = barSection;
        }
    }

    private final List<Level> levels = new ArrayList<>();

    static synchronized void start() {
        if (thread != null) return;
        running = true;
        thread = new Thread(new ProgressSampler(), "FastLoader-ProgressSampler");
        thread.setDaemon(true);
        thread.start();
    }

    static synchronized void stop() {
        running = false;
        Thread t = thread;
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(200);
            } catch (InterruptedException ignored) {}
        }
    }

    @Override
    public void run() {
        Section group = LoadProfiler
            .begin("FML loading bars (splash screen, sampled every " + PERIOD_MS + " ms)", "group", null);
        List<ProgressBar> current = new ArrayList<>();
        try {
            while (running) {
                current.clear();
                Iterator<ProgressBar> it = ProgressManager.barIterator();
                while (it.hasNext()) current.add(it.next());
                update(current);
                Thread.sleep(PERIOD_MS);
            }
        } catch (Throwable t) {
            LoadProfiler.note("Splash bar sampler stopped: " + t);
        } finally {
            closeFrom(0);
            LoadProfiler.end(group);
        }
    }

    private void update(List<ProgressBar> bars) {
        // first level where the recorded state differs from the real one
        int d = 0;
        while (d < levels.size() && d < bars.size()) {
            Level l = levels.get(d);
            if (l.bar != bars.get(d) || l.step != l.bar.getStep()) break;
            d++;
        }
        if (d == levels.size() && d == bars.size()) return;

        LoadProfiler.hook("FML splash bars (sampled)");
        if (d < levels.size() && d < bars.size() && levels.get(d).bar == bars.get(d)) {
            // same bar, it advanced a step: close everything nested in the old step, then the step itself
            closeFrom(d + 1);
            Level l = levels.get(d);
            if (l.stepSection != null) LoadProfiler.end(l.stepSection);
            openStep(l);
            d++;
        } else {
            closeFrom(d);
        }
        for (int i = d; i < bars.size(); i++) {
            ProgressBar bar = bars.get(i);
            Section s = LoadProfiler.begin(bar.getTitle(), "bar", bar.getTitle());
            if (s == null) return;
            s.barSteps = bar.getSteps();
            Level l = new Level(bar, s);
            levels.add(l);
            openStep(l);
        }
    }

    private static void openStep(Level l) {
        l.step = l.bar.getStep();
        l.stepSection = l.step == 0 ? null
            : LoadProfiler.begin(String.valueOf(l.bar.getMessage()), "step", l.bar.getTitle());
    }

    private void closeFrom(int depth) {
        for (int i = levels.size() - 1; i >= depth; i--) {
            Level l = levels.remove(i);
            if (l.stepSection != null) LoadProfiler.end(l.stepSection);
            LoadProfiler.end(l.barSection);
        }
    }
}
