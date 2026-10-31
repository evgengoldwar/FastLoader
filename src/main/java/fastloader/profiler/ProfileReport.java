package fastloader.profiler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import cpw.mods.fml.common.Loader;
import fastloader.FastLoader;

/** Turns the profiler data into a human readable report (log + file). */
public final class ProfileReport {

    private ProfileReport() {}

    public static volatile int topEntries = 30;
    public static volatile boolean writeFiles = true;
    public static volatile boolean treeInLog = false;
    public static volatile double treeMinPercent = 0.5;

    private static File reportFile;

    /** Starts a new report file (called when a server starts). */
    public static synchronized void newSession() {
        reportFile = null;
    }

    static void periodic(Interval iv, String reason) {
        FastLoader.LOG.info("Chunk profile ({}): {}", reason, shortStatus());
        String summary = build(iv, false);
        FastLoader.LOG.info("\n" + (treeInLog ? build(iv, true) : summary));
        if (writeFiles) writeToFile(build(iv, true));
    }

    /** Report of the running interval, used by the command. Returns the file it was written to (or null). */
    public static File manual() {
        Interval iv = ChunkProfiler.interval;
        String full = build(iv, true);
        FastLoader.LOG.info("\n" + (treeInLog ? full : build(iv, false)));
        return writeToFile(full);
    }

    /** One line status for chat. */
    public static String shortStatus() {
        Interval iv = ChunkProfiler.interval;
        double sec = Math.max(0.001, (System.nanoTime() - iv.startNanos) / 1e9);
        return String.format(
            "%.0fs: MSPT avg %.1f max %.1f, slow ticks %d | gen %d (%.1f/s), loaded %d, populated %d, cascades %d | chunk work %.1f ms/tick",
            sec,
            iv.tickHist.avgMs(),
            iv.tickHist.maxMs(),
            iv.slowTicks,
            iv.generated.sum(),
            iv.generated.sum() / sec,
            iv.diskLoaded.sum() + iv.asyncFinished.sum(),
            iv.populated.sum(),
            iv.cascadeGenerated.sum() + iv.cascadeLoaded.sum(),
            iv.ticks == 0 ? 0 : iv.chunkWorkNs / 1e6 / iv.ticks);
    }

    // ================================================================ building

    static String build(Interval iv, boolean withTree) {
        double sec = Math.max(0.001, (System.nanoTime() - iv.startNanos) / 1e9);
        StringBuilder sb = new StringBuilder();
        String since = new SimpleDateFormat("HH:mm:ss").format(new Date(iv.startMillis));
        line(sb, "==================== FastLoader chunk profile: %.1f s since %s ====================", sec, since);

        // ---- ticks
        line(sb, "Server ticks: %d (%.1f TPS) | MSPT %s", iv.ticks, iv.ticks / sec, iv.tickHist.describe());
        if (iv.ticks > 0) {
            line(
                sb,
                "Chunk work on server thread: avg %.2f ms/tick, max %.2f ms/tick, %.1f%% of tick time | ticks >= %d ms: %d",
                iv.chunkWorkNs / 1e6 / iv.ticks,
                iv.maxChunkWorkNs / 1e6,
                iv.tickHist.avgMs() == 0 ? 0 : (iv.chunkWorkNs / 1e6 / iv.ticks) * 100.0 / iv.tickHist.avgMs(),
                ChunkProfiler.slowTickNs / 1_000_000L,
                iv.slowTicks);
        }

        // ---- counters
        long loaded = iv.diskLoaded.sum() + iv.asyncFinished.sum();
        line(
            sb,
            "Chunks: generated %d (%.1f/s) | loaded from disk %d (sync %d, async-finish %d) | populated %d | saved %d | unloaded %d | packets %d",
            iv.generated.sum(),
            iv.generated.sum() / sec,
            loaded,
            iv.diskLoaded.sum(),
            iv.asyncFinished.sum(),
            iv.populated.sum(),
            iv.saved.sum(),
            iv.unloaded.sum(),
            iv.packetsBuilt.sum());
        for (Map.Entry<Integer, Interval.DimCounters> e : iv.perDim.entrySet()) {
            Interval.DimCounters c = e.getValue();
            line(
                sb,
                "    dim %d: generated %d, loaded %d, populated %d, cascades %d",
                e.getKey(),
                c.generated.sum(),
                c.loaded.sum(),
                c.populated.sum(),
                c.cascades.sum());
        }
        line(
            sb,
            "Session totals: generated %d, loaded %d, populated %d, cascades %d",
            ChunkProfiler.totalGenerated.sum(),
            ChunkProfiler.totalLoaded.sum(),
            ChunkProfiler.totalPopulated.sum(),
            ChunkProfiler.totalCascades.sum());
        line(sb, "Terrain (provideChunk) per chunk:       %s", iv.terrainHist.describe());
        line(sb, "Population per chunk:                   %s", iv.populateHist.describe());
        line(sb, "Full generation (originalLoadChunk):    %s", iv.generateTotalHist.describe());

        // ---- cascades
        long casc = iv.cascadeGenerated.sum() + iv.cascadeLoaded.sum();
        line(
            sb,
            "Cascading worldgen: %d chunks generated + %d loaded from disk while populating another chunk",
            iv.cascadeGenerated.sum(),
            iv.cascadeLoaded.sum());
        if (casc > 0) {
            List<Map.Entry<String, LongAdder[]>> list = new ArrayList<>(iv.cascadeCulprits.entrySet());
            list.sort((a, b) -> Long.compare(sum(b.getValue()), sum(a.getValue())));
            for (int i = 0; i < Math.min(15, list.size()); i++) {
                Map.Entry<String, LongAdder[]> e = list.get(i);
                line(
                    sb,
                    "    %6d generated %6d loaded  <- %s",
                    e.getValue()[0].sum(),
                    e.getValue()[1].sum(),
                    e.getKey());
            }
        }

        // ---- world state
        appendWorldState(sb);

        // ---- trees per thread role
        Map<String, ProfileNode> roles = mergedTrees();
        for (Map.Entry<String, ProfileNode> e : roles.entrySet()) {
            ProfileNode root = e.getValue();
            long rootTotal = root.childrenTotalNs();
            if (rootTotal == 0) continue;
            Map<String, Flat> flat = new HashMap<>();
            flatten(root, flat, new HashSet<String>());
            List<Flat> flats = new ArrayList<>(flat.values());

            line(sb, "");
            line(
                sb,
                "---- Thread '%s': %.1f ms profiled (%.1f%% of wall time) ----",
                e.getKey(),
                rootTotal / 1e6,
                rootTotal / 1e7 / sec);
            line(sb, "Top sections by SELF time (where the time is actually spent):");
            header(sb);
            flats.sort((a, b) -> Long.compare(b.self, a.self));
            for (int i = 0; i < Math.min(topEntries, flats.size()); i++) row(sb, flats.get(i), rootTotal);

            appendCategory(sb, flats, rootTotal, "Mod world generators (IWorldGenerator) by total time:", Section.WORLDGEN);
            appendCategory(sb, flats, rootTotal, "Chunk provider terrain (provideChunk) by total time:", Section.TERRAIN);
            appendCategory(
                sb,
                flats,
                rootTotal,
                "Chunk provider population by total time:",
                Section.PROVIDER_POPULATE);
            appendCategory(sb, flats, rootTotal, "Event listeners fired during chunk work:", Section.EVENT);
            appendCategory(sb, flats, rootTotal, "Map generators / structures:", Section.MAPGEN, Section.STRUCTURE);
            appendCategory(sb, flats, rootTotal, "Biome decoration:", Section.DECORATE);
            appendCategory(
                sb,
                flats,
                rootTotal,
                "GregTech worldgen:",
                Section.GT_CONTAINER,
                Section.GT_VEIN_SEARCH,
                Section.GT_VEIN,
                Section.GT_STONE,
                Section.GT_SMALL_ORE);

            appendCategory(
                sb,
                flats,
                rootTotal,
                "Realistic World Gen internals:",
                Section.RWG_TERRAIN,
                Section.RWG_MAPGEN,
                Section.RWG_SURFACE,
                Section.RWG_DECORATE,
                Section.LIQUIDS);
            appendCounters(sb, flats);

            if (withTree) {
                line(sb, "Call tree (total ms | self ms | calls | avg ms | max ms):");
                appendTree(sb, root, rootTotal, 0);
            }
        }
        line(sb, "==================== end of FastLoader chunk profile ====================");
        return sb.toString();
    }

    private static void appendCategory(StringBuilder sb, List<Flat> flats, long rootTotal, String title,
        Section... sections) {
        List<Flat> list = new ArrayList<>();
        for (Flat f : flats) {
            for (Section s : sections) {
                if (f.section == s) {
                    list.add(f);
                    break;
                }
            }
        }
        if (list.isEmpty()) return;
        list.sort((a, b) -> Long.compare(b.total, a.total));
        line(sb, title);
        header(sb);
        for (int i = 0; i < Math.min(topEntries, list.size()); i++) row(sb, list.get(i), rootTotal);
    }

    private static void appendCounters(StringBuilder sb, List<Flat> flats) {
        long lights = 0, blocks = 0, sampleNs = 0, samples = 0;
        List<Flat> list = new ArrayList<>();
        for (Flat f : flats) {
            lights += f.lightUpdates;
            blocks += f.setBlocks;
            if (f.section == Section.LIGHT_SAMPLE) {
                sampleNs += f.total;
                samples += f.count;
            }
            if (f.setBlocks > 0 || f.lightUpdates > 0) list.add(f);
        }
        if (list.isEmpty()) return;
        double perLightMs = samples == 0 ? 0 : sampleNs / 1e6 / samples;
        line(
            sb,
            "Block writes and light updates (counted, not timed): setBlock %d, light updates %d, estimated light time %.1f ms (%.4f ms per update, %d samples)",
            blocks,
            lights,
            lights * perLightMs,
            perLightMs,
            samples);
        list.sort((a, b) -> Long.compare(b.setBlocks + b.lightUpdates, a.setBlocks + a.lightUpdates));
        line(
            sb,
            "    %10s %9s %11s %10s %11s %9s  %s",
            "setBlock",
            "per call",
            "light upd",
            "per call",
            "est. light",
            "calls",
            "section (direct calls only)");
        for (int i = 0; i < Math.min(topEntries, list.size()); i++) {
            Flat f = list.get(i);
            line(
                sb,
                "    %10d %9.1f %11d %10.1f %8.1f ms %9d  %s",
                f.setBlocks,
                f.count == 0 ? 0 : (double) f.setBlocks / f.count,
                f.lightUpdates,
                f.count == 0 ? 0 : (double) f.lightUpdates / f.count,
                f.lightUpdates * perLightMs,
                f.count,
                f.key);
        }
    }

    private static void header(StringBuilder sb) {
        line(sb, "    %10s %10s %6s %9s %9s %9s  %s", "total ms", "self ms", "self%", "calls", "avg ms", "max ms", "section");
    }

    private static void row(StringBuilder sb, Flat f, long rootTotal) {
        line(
            sb,
            "    %10.1f %10.1f %5.1f%% %9d %9.3f %9.2f  %s",
            f.total / 1e6,
            f.self / 1e6,
            rootTotal == 0 ? 0 : f.self * 100.0 / rootTotal,
            f.count,
            f.count == 0 ? 0 : f.total / 1e6 / f.count,
            f.max / 1e6,
            f.key);
    }

    private static void appendTree(StringBuilder sb, ProfileNode node, long rootTotal, int depth) {
        List<ProfileNode> kids = new ArrayList<>();
        for (ProfileNode c : node.children) {
            if (c.section == null || !c.section.isCounter()) kids.add(c);
        }
        kids.sort((a, b) -> Long.compare(b.totalNs, a.totalNs));
        int shown = 0;
        long hiddenNs = 0;
        int hidden = 0;
        for (ProfileNode c : kids) {
            if (c.totalNs * 100.0 / rootTotal < treeMinPercent || shown >= 25) {
                hiddenNs += c.totalNs;
                hidden++;
                continue;
            }
            shown++;
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < depth; i++) indent.append("  ");
            line(
                sb,
                "%s- %.1f | %.1f | %d | %.3f | %.2f  %s%s",
                indent,
                c.totalNs / 1e6,
                c.selfNs / 1e6,
                c.count,
                c.count == 0 ? 0 : c.totalNs / 1e6 / c.count,
                c.maxNs / 1e6,
                c.key,
                counters(c));
            appendTree(sb, c, rootTotal, depth + 1);
        }
        if (hidden > 0) {
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < depth; i++) indent.append("  ");
            line(sb, "%s- %.1f ms in %d smaller entries", indent, hiddenNs / 1e6, hidden);
        }
    }

    private static String counters(ProfileNode n) {
        long blocks = 0, lights = 0;
        for (ProfileNode c : n.children) {
            if (c.section == Section.SET_BLOCK) blocks += c.count;
            else if (c.section == Section.LIGHT_UPDATE) lights += c.count;
        }
        if (blocks == 0 && lights == 0) return "";
        return "  {setBlock " + blocks + ", light " + lights + "}";
    }

    private static void appendWorldState(StringBuilder sb) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null) return;
        StringBuilder w = new StringBuilder("Worlds:");
        for (WorldServer ws : server.worldServers) {
            if (ws == null) continue;
            w.append(
                String.format(
                    " [dim %d: %d chunks loaded, %d entities, %d tile entities]",
                    ws.provider.dimensionId,
                    ws.theChunkProviderServer.loadedChunks.size(),
                    ws.loadedEntityList.size(),
                    ws.loadedTileEntityList.size()));
        }
        line(sb, "%s", w);
        int gtPending = gtPendingTasks();
        if (gtPending >= 0) line(sb, "GregTech pending oregen tasks (deferred WorldGenContainers): %d", gtPending);
    }

    // ================================================================ GT pending queue (reflection, optional)

    private static Field gtPendingField;
    private static boolean gtPendingLooked;

    private static int gtPendingTasks() {
        if (!gtPendingLooked) {
            gtPendingLooked = true;
            for (String cls : new String[] { "gregtech.common.GTWorldgenerator", "gregtech.common.GT_Worldgenerator" }) {
                try {
                    Field f = Class.forName(cls)
                        .getDeclaredField("PENDING_TASKS");
                    f.setAccessible(true);
                    gtPendingField = f;
                    break;
                } catch (Throwable ignored) {}
            }
        }
        if (gtPendingField == null) return -1;
        try {
            Object v = gtPendingField.get(null);
            return v instanceof Collection ? ((Collection<?>) v).size() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    // ================================================================ tree helpers

    static final class Flat {

        final Section section;
        final String key;
        long count, total, self, max;
        /** direct counter children */
        long setBlocks, lightUpdates;

        Flat(Section section, String key) {
            this.section = section;
            this.key = key;
        }
    }

    /** Sums nodes with the same key. Inclusive time is only taken from the outermost occurrence on a path. */
    private static void flatten(ProfileNode node, Map<String, Flat> out, Set<String> onPath) {
        for (ProfileNode c : node.children) {
            if (c.section != null && c.section.isCounter()) continue;
            Flat f = out.get(c.key);
            if (f == null) {
                f = new Flat(c.section, c.key);
                out.put(c.key, f);
            }
            f.count += c.count;
            f.self += c.selfNs;
            for (ProfileNode cc : c.children) {
                if (cc.section == Section.SET_BLOCK) f.setBlocks += cc.count;
                else if (cc.section == Section.LIGHT_UPDATE) f.lightUpdates += cc.count;
            }
            if (c.maxNs > f.max) f.max = c.maxNs;
            boolean outer = onPath.add(c.key);
            if (outer) f.total += c.totalNs;
            flatten(c, out, onPath);
            if (outer) onPath.remove(c.key);
        }
    }

    private static Map<String, ProfileNode> mergedTrees() {
        Map<String, ProfileNode> roles = new LinkedHashMap<>();
        int epoch = ChunkProfiler.epoch;
        // server thread first
        List<ChunkProfiler.ThreadState> states = new ArrayList<>(ChunkProfiler.STATES);
        states.sort((a, b) -> Boolean.compare(b.thread == ChunkProfiler.serverThread, a.thread == ChunkProfiler.serverThread));
        for (ChunkProfiler.ThreadState st : states) {
            if (st.epoch != epoch) continue;
            ProfileNode merged = roles.get(st.role);
            if (merged == null) {
                merged = new ProfileNode(null, "root");
                roles.put(st.role, merged);
            }
            merged.mergeFrom(st.root);
        }
        return roles;
    }

    private static long sum(LongAdder[] a) {
        return a[0].sum() + a[1].sum();
    }

    private static void line(StringBuilder sb, String fmt, Object... args) {
        sb.append(args.length == 0 ? fmt : String.format(fmt, args))
            .append('\n');
    }

    // ================================================================ file output

    private static synchronized File writeToFile(String text) {
        try {
            if (reportFile == null) {
                File dir = new File(
                    Loader.instance()
                        .getConfigDir()
                        .getParentFile(),
                    "fastloader");
                if (!dir.isDirectory() && !dir.mkdirs()) return null;
                reportFile = new File(
                    dir,
                    "chunkprofile-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".log");
                FastLoader.LOG.info("Chunk profile reports are written to {}", reportFile.getAbsolutePath());
            }
            try (Writer w = new FileWriter(reportFile, true)) {
                w.write(text);
                w.write('\n');
            }
            return reportFile;
        } catch (IOException e) {
            FastLoader.LOG.warn("Could not write chunk profile report", e);
            return null;
        }
    }
}
