package fastloader;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import fastloader.optimize.ChunkLookupCache;
import fastloader.optimize.FastWorldgenWrites;
import fastloader.optimize.HodgepodgeTuning;
import fastloader.optimize.SimulationDistance;
import fastloader.optimize.TerrainPrefetcher;
import fastloader.pregen.ChunkPregenerator;
import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.ProfileReport;
import fastloader.profiler.StackSampler;

public class Config {

    private static final String PROFILER = "profiler";
    private static final String PREGEN = "pregen";
    private static final String OPTIMIZATIONS = "optimizations";

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
            "Count World.updateLightByType calls inside chunk work and time 1 of every 32 to estimate their cost.");
        ChunkProfiler.countSetBlock = configuration.getBoolean(
            "countSetBlock",
            PROFILER,
            true,
            "Count World.setBlock calls inside chunk work per section (shows which generator places how many blocks).");
        ChunkProfiler.profileEvents = configuration.getBoolean(
            "profileEventListeners",
            PROFILER,
            true,
            "Time every Forge event listener that runs inside chunk work (ChunkEvent.Load, PopulateChunkEvent, DecorateBiomeEvent, OreGenEvent...).");
        ChunkProfiler.reportIntervalNs = configuration.getInt(
            "reportIntervalSeconds",
            PROFILER,
            60,
            5,
            3600,
            "While chunks keep being generated, a report is also written at least this often.") * 1_000_000_000L;
        ChunkProfiler.idleFlushNs = configuration.getInt(
            "reportAfterIdleSeconds",
            PROFILER,
            10,
            1,
            3600,
            "Write a report this many seconds after chunk generation stops (no commands needed). Intervals without generated chunks are not reported.")
            * 1_000_000_000L;
        ChunkProfiler.slowChunkNs = configuration.getInt(
            "slowChunkThresholdMs",
            PROFILER,
            50,
            1,
            10_000,
            "Log a per-chunk breakdown when generating / populating a single chunk takes at least this long.")
            * 1_000_000L;
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
        StackSampler.enabled = configuration.getBoolean(
            "stackSampling",
            PROFILER,
            true,
            "Sample the server thread's stack while it does chunk work, to explain time inside code that is not instrumented (RWG decorators, mod generators, setBlock internals).");
        StackSampler.intervalMs = configuration
            .getInt("stackSampleIntervalMs", PROFILER, 10, 1, 1000, "Milliseconds between two stack samples.");
        ProfileReport.topEntries = configuration
            .getInt("topEntries", PROFILER, 30, 5, 500, "How many rows the 'top' tables of a report contain.");
        ProfileReport.writeFiles = configuration.getBoolean(
            "writeReportFiles",
            PROFILER,
            true,
            "Also write every report (including the full call tree) to <game dir>/fastloader/chunkprofile-*.log.");
        ProfileReport.treeInLog = configuration
            .getBoolean("callTreeInLog", PROFILER, false, "Also put the full call tree into the main log.");

        configuration.setCategoryComment(
            OPTIMIZATIONS,
            "Chunk generation optimizations. None of them changes what is generated, only how fast and on which thread.");
        ChunkLookupCache.enabled = configuration.getBoolean(
            "chunkLookupCache",
            OPTIMIZATIONS,
            true,
            "Cache the last 4 chunks looked up through provideChunk (every getBlock/setBlock does such a lookup).");

        TerrainPrefetcher.enabled = configuration.getBoolean(
            "terrainPrefetch",
            OPTIMIZATIONS,
            true,
            "Generate terrain (the chunk generator's provideChunk) for chunks around players on background threads, like modern Minecraft. Population, lighting and everything else that touches the world stays on the server thread. Only used for the generators listed in prefetchGenerators.");
        TerrainPrefetcher.threads = configuration.getInt(
            "prefetchThreads",
            OPTIMIZATIONS,
            0,
            0,
            32,
            "Background terrain threads. 0 = automatic (CPU cores - 3, at most 6, at least 1).");
        TerrainPrefetcher.extraRadius = configuration.getInt(
            "prefetchExtraRadius",
            OPTIMIZATIONS,
            2,
            0,
            8,
            "Prefetch terrain this many chunks beyond the view distance (population needs neighbours).");
        TerrainPrefetcher.maxReady = configuration.getInt(
            "prefetchMaxReady",
            OPTIMIZATIONS,
            512,
            16,
            8192,
            "Maximum prefetched chunks kept in memory per dimension (about 100-200 KB each).");
        TerrainPrefetcher.waitMs = configuration.getInt(
            "prefetchWaitMs",
            OPTIMIZATIONS,
            50,
            0,
            1000,
            "When the server needs a chunk a worker is still generating, wait this long for it before generating it itself.");
        TerrainPrefetcher.generators = new java.util.HashSet<>(
            java.util.Arrays.asList(
                configuration.getStringList(
                    "prefetchGenerators",
                    OPTIMIZATIONS,
                    new String[] { "rwg.world.ChunkGeneratorRealistic" },
                    "Chunk generator classes whose terrain may be generated on background threads. They must re-seed per chunk and keep their state in the generator instance. Vanilla generators are NOT safe (GenLayer uses a static IntCache).")));

        FastWorldgenWrites.enabled = configuration.getBoolean(
            "fastWorldgenWrites",
            OPTIMIZATIONS,
            true,
            "During chunk population, store 'opaque block replaces opaque block' writes (stone -> dirt, gravel, ores, bedrock...) directly in chunks the client does not have yet. For these writes the full setBlock only stores the block anyway: no light, heightmap, client or neighbour change. Blocks with tile entities, falling blocks and blocks with custom add/break logic always use the full setBlock.");
        SimulationDistance.chunks = configuration.getInt(
            "simulationDistance",
            OPTIMIZATIONS,
            10,
            0,
            64,
            "Like modern Minecraft: beyond this many chunks from every player, chunks get no random block ticks / ice / snow / lightning and non-player entities update once per second instead of every tick (they still despawn). Chunks are still lit and sent, chunk-loaded chunks and tile entities are not affected. Only matters when the view distance is larger. 0 = vanilla.");
        HodgepodgeTuning.enabled = configuration.getBoolean(
            "hodgepodgeTuning",
            OPTIMIZATIONS,
            true,
            "Raise Hodgepodge's chunk generation throttle (4 chunks per player per tick / 20 ms by default) at server start, so the time saved by background terrain turns into faster world loading. Hodgepodge still never runs generation past the end of the 50 ms tick.");
        HodgepodgeTuning.maxChunkGenPerPlayerPerTick = configuration.getInt(
            "hodgepodgeMaxChunkGenPerPlayerPerTick",
            OPTIMIZATIONS,
            10,
            1,
            20,
            "Value for Hodgepodge's maxChunkGenPerPlayerPerTick.");
        HodgepodgeTuning.chunkGenBudgetMs = configuration.getInt(
            "hodgepodgeTickBudgetMs",
            OPTIMIZATIONS,
            30,
            0,
            50,
            "Value for Hodgepodge's chunkGenBudgetMs. Hodgepodge does not count the rest of the tick (entities, block ticks), so values above ~30 can push ticks past 50 ms.");
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
