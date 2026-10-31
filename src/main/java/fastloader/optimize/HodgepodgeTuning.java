package fastloader.optimize;

import java.lang.reflect.Field;

import fastloader.FastLoader;

/**
 * Hodgepodge (GTNH) throttles chunk generation to {@code maxChunkGenPerPlayerPerTick} chunks per player per tick
 * within {@code chunkGenBudgetMs} (default 4 chunks / 20 ms = 80 chunks per second at most). With terrain generated
 * on background threads a chunk costs the server thread less, so more of them fit into a tick. These values are
 * applied at server start. forcePopulateAgeTicks is left to Hodgepodge: lowering it makes Hodgepodge force-process
 * holes outside its budget almost every tick at large view distances.
 */
public final class HodgepodgeTuning {

    private HodgepodgeTuning() {}

    public static volatile boolean enabled = true;
    public static volatile int maxChunkGenPerPlayerPerTick = 10;
    public static volatile int chunkGenBudgetMs = 30;

    private static final String CONFIG_CLASS = "com.mitchej123.hodgepodge.config.SpeedupsConfig";

    public static void apply() {
        if (!enabled) return;
        Class<?> c;
        try {
            c = Class.forName(CONFIG_CLASS);
        } catch (Throwable t) {
            return; // no Hodgepodge, or a version without the scheduler
        }
        StringBuilder sb = new StringBuilder();
        set(c, "maxChunkGenPerPlayerPerTick", maxChunkGenPerPlayerPerTick, sb);
        set(c, "chunkGenBudgetMs", chunkGenBudgetMs, sb);
        if (sb.length() > 0) {
            FastLoader.LOG.info("[hodgepodge] chunk generation throttle tuned:{} (optimizations.hodgepodgeTuning)", sb);
        }
    }

    private static void set(Class<?> c, String name, int value, StringBuilder sb) {
        try {
            Field f = c.getField(name);
            if (f.getType() != int.class) return;
            int old = f.getInt(null);
            if (old == value) return;
            f.setInt(null, value);
            sb.append(' ')
                .append(name)
                .append(' ')
                .append(old)
                .append(" -> ")
                .append(value);
        } catch (Throwable ignored) {
            // field missing in this Hodgepodge version
        }
    }
}
