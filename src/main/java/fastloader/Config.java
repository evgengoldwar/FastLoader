package fastloader;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import fastloader.pregen.ChunkPregenerator;
import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.ProfileReport;

public class Config {

    private static final String PROFILER = "profiler";
    private static final String PREGEN = "pregen";

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        configuration.setCategoryComment(
            PROFILER,
            "Chunk pipeline profiler: logs where chunk loading / generation / population / lighting / saving spends time.");
        ChunkProfiler.enabled = configuration.getBoolean(
            "enabled",
            PROFILER,
            true,
            "Profile the chunk pipeline from server start. Can be toggled in game with /fastloader profile on|off.");
        ChunkProfiler.profileLighting = configuration.getBoolean(
            "profileLighting",
            PROFILER,
            true,
            "Time World.updateLightByType calls that happen inside chunk work (cheap, but called very often).");
        ChunkProfiler.profileEvents = configuration.getBoolean(
            "profileEventListeners",
            PROFILER,
            true,
            "Time every Forge event listener that runs inside chunk work (ChunkEvent.Load, PopulateChunkEvent, DecorateBiomeEvent, OreGenEvent...).");
        ChunkProfiler.reportIntervalNs = configuration
            .getInt("reportIntervalSeconds", PROFILER, 60, 5, 3600, "How often a full report is logged / written to file.")
            * 1_000_000_000L;
        ChunkProfiler.slowChunkNs = configuration.getInt(
            "slowChunkThresholdMs",
            PROFILER,
            50,
            1,
            10_000,
            "Log a per-chunk breakdown when generating / populating a single chunk takes at least this long.") * 1_000_000L;
        ChunkProfiler.slowTickNs = configuration.getInt(
            "slowTickThresholdMs",
            PROFILER,
            50,
            1,
            10_000,
            "Log a breakdown of the server tick when it takes at least this long.") * 1_000_000L;
        ChunkProfiler.maxLogsPerInterval = configuration.getInt(
            "maxLogsPerInterval",
            PROFILER,
            20,
            0,
            10_000,
            "Max slow chunk / slow tick / cascade lines logged per report interval (each kind), to keep the log readable.");
        ChunkProfiler.logCascades = configuration.getBoolean(
            "logCascades",
            PROFILER,
            true,
            "Log every cascading worldgen event (chunk generated or loaded while another chunk is populated) with its cause.");
        ProfileReport.topEntries = configuration
            .getInt("topEntries", PROFILER, 30, 5, 500, "How many rows the 'top' tables of a report contain.");
        ProfileReport.writeFiles = configuration.getBoolean(
            "writeReportFiles",
            PROFILER,
            true,
            "Also write every report (including the full call tree) to <game dir>/fastloader/chunkprofile-*.log.");
        ProfileReport.treeInLog = configuration
            .getBoolean("callTreeInLog", PROFILER, false, "Also put the full call tree into the main log.");

        configuration.setCategoryComment(PREGEN, "/fastloader pregen: benchmark / pregeneration tool.");
        ChunkPregenerator.tickBudgetNs = configuration.getInt(
            "tickBudgetMs",
            PREGEN,
            35,
            1,
            1000,
            "Milliseconds per server tick the pregenerator may spend generating chunks.") * 1_000_000L;
        ChunkPregenerator.progressIntervalNs = configuration
            .getInt("progressLogSeconds", PREGEN, 10, 1, 3600, "How often the pregenerator logs its progress.")
            * 1_000_000_000L;

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
