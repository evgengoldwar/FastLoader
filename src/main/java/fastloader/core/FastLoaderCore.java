package fastloader.core;

import fastloader.profiler.LoadProfiler;

/** Entry points tying the optimisations and the profiler to the game's loading stages. */
public final class FastLoaderCore {

    private static boolean finished;

    private FastLoaderCore() {}

    /** FastLoader coremod constructor: as early as a mod can run. */
    public static void onCoremodInit() {
        LoadProfiler.init();
        ClassBytesPrefetcher.start();
    }

    /** FastLoader mod construction: coremods and late mixin configs are all registered by now. */
    public static void onModConstruct() {
        ClassPreloader.start();
    }

    /** Main menu on the client, server started on a dedicated server. */
    public static synchronized void onStartupFinished(String reason) {
        if (finished) return;
        finished = true;
        ClassBytesPrefetcher.onStartupFinished();
        ClassPreloader.onStartupFinished();
        LoadProfiler.finish(reason);
    }

    /** For the report. */
    public static String[] describe() {
        return new String[] { "Class bytes prefetch:  " + ClassBytesPrefetcher.describe(),
            "Class preload:         " + ClassPreloader.describe() };
    }
}
