package fastloader.optimize;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.structure.MapGenStructure;

import fastloader.FastLoader;
import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/**
 * Generates terrain (IChunkProvider.provideChunk) for chunks around players on background threads, the way modern
 * Minecraft runs its noise stage off the main thread. The main thread keeps doing everything that touches the world:
 * when it asks the generator for a chunk that is ready (or still being prepared) it gets that chunk instead of
 * generating it, then lights the heightmap and registers structure starts as usual, and population runs as before.
 * <p>
 * Safety rules:
 * <ul>
 * <li>Each worker uses its own generator instance and its own WorldChunkManager (biome caches are not thread safe),
 * created on the main thread. World biome lookups made by worker code are answered by that private manager.</li>
 * <li>Map generator events are suppressed while worker generators are built, so mods never see (and never keep a
 * reference to) these extra instances. Structure map generators are skipped on workers (they touch world storage);
 * the main generator's recreateStructures() registers the starts when the chunk is handed over.</li>
 * <li>Skylight / heightmap is computed on the main thread, lighting engines are never touched by workers.</li>
 * <li>Only generators listed in the config are used (RWG by default), the result is identical to main thread
 * generation because generators re-seed per chunk. Any worker error disables prefetching for that world.</li>
 * </ul>
 */
public final class TerrainPrefetcher {

    private TerrainPrefetcher() {}

    // ------------------------------------------------------------------ config
    public static volatile boolean enabled = true;
    public static volatile int threads = 0; // 0 = auto
    public static volatile int extraRadius = 2;
    public static volatile int maxReady = 512;
    public static volatile int waitMs = 50;
    public static volatile Set<String> generators = new HashSet<>();

    static {
        generators.add("rwg.world.ChunkGeneratorRealistic");
    }

    // ------------------------------------------------------------------ stats
    public static final LongAdder hits = new LongAdder();
    public static final LongAdder waitedHits = new LongAdder();
    public static final LongAdder misses = new LongAdder();
    public static final LongAdder timeouts = new LongAdder();
    public static final LongAdder submitted = new LongAdder();
    public static final LongAdder discarded = new LongAdder();

    // ------------------------------------------------------------------ worker thread context
    private static final ThreadLocal<WorldChunkManager> WORKER_CM = new ThreadLocal<>();
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    /** Set while worker generators are constructed, makes TerrainGen.getModdedMapGen return the original. */
    public static volatile boolean suppressMapGenEvents;

    private static ExecutorService executor;
    private static int poolSize;
    private static final Map<World, WorldState> STATES = new IdentityHashMap<>();
    /** main thread generator -> state, used when the generator asks for a prefetched chunk */
    private static final Map<IChunkProvider, WorldState> BY_GENERATOR = new IdentityHashMap<>();
    private static final Set<World> UNSUPPORTED = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /** True on a terrain worker thread while it generates. */
    public static boolean isWorkerThread() {
        return WORKER_CM.get() != null;
    }

    /** The private biome manager of the current worker thread, or null on other threads. */
    public static WorldChunkManager workerChunkManager() {
        return WORKER_CM.get();
    }

    /** Structure generators touch shared world storage, they run on the main thread only. */
    public static boolean skipOnWorker(MapGenBase gen) {
        return gen instanceof MapGenStructure && WORKER_CM.get() != null;
    }

    // ================================================================== main thread: handing chunks over

    /**
     * Called by the generator on the main thread right before it would generate (x, z). Returns the prefetched chunk
     * (waiting up to {@link #waitMs} if a worker is on it) or null to generate normally.
     */
    public static Chunk take(IChunkProvider generator, int x, int z) {
        if (!enabled || Thread.currentThread() != ChunkProfiler.serverThread()) return null;
        WorldState ws = BY_GENERATOR.get(generator);
        if (ws == null || ws.disabled) return null;
        long key = key(x, z);
        Chunk c = ws.ready.remove(key);
        if (c != null) {
            hits.increment();
            return c;
        }
        CompletableFuture<Chunk> f = ws.inFlight.get(key);
        if (f == null) {
            misses.increment();
            return null;
        }
        try {
            c = f.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timeouts.increment();
            return null;
        } catch (Exception e) {
            return null;
        }
        ws.ready.remove(key);
        if (c == null) return null;
        waitedHits.increment();
        return c;
    }

    // ================================================================== main thread: scheduling

    /** Called at the end of every server tick. */
    public static void tick() {
        if (!enabled) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null) return;
        if (Thread.currentThread() != ChunkProfiler.serverThread()) return;
        for (WorldServer world : server.worldServers) {
            if (world == null || world.playerEntities.isEmpty()) continue;
            WorldState ws = state(world);
            if (ws == null || ws.disabled) continue;
            try {
                ws.schedule(server);
            } catch (Throwable t) {
                disable(ws, "scheduling failed", t);
            }
        }
    }

    private static WorldState state(WorldServer world) {
        WorldState ws = STATES.get(world);
        if (ws != null || UNSUPPORTED.contains(world)) return ws;
        IChunkProvider main = world.theChunkProviderServer.currentChunkProvider;
        if (main == null || !generators.contains(
            main.getClass()
                .getName())) {
            UNSUPPORTED.add(world);
            return null;
        }
        try {
            ws = new WorldState(world, main);
            STATES.put(world, ws);
            BY_GENERATOR.put(main, ws);
            FastLoader.LOG.info(
                "[prefetch] terrain for dim {} ({}) is generated on {} background threads",
                world.provider.dimensionId,
                main.getClass()
                    .getName(),
                poolSize());
            return ws;
        } catch (Throwable t) {
            FastLoader.LOG
                .warn("[prefetch] cannot prefetch terrain for dim {}: {}", world.provider.dimensionId, t.toString());
            UNSUPPORTED.add(world);
            return null;
        }
    }

    public static void onWorldUnload(World world) {
        WorldState ws = STATES.remove(world);
        if (ws != null) {
            ws.disabled = true;
            BY_GENERATOR.values()
                .remove(ws);
        }
        UNSUPPORTED.remove(world);
    }

    public static void onServerStopping() {
        for (WorldState ws : STATES.values()) ws.disabled = true;
        STATES.clear();
        BY_GENERATOR.clear();
        UNSUPPORTED.clear();
    }

    private static void disable(WorldState ws, String why, Throwable t) {
        if (ws.disabled) return;
        ws.disabled = true;
        ws.ready.clear();
        FastLoader.LOG.error(
            "[prefetch] disabled for dim " + ws.world.provider.dimensionId
                + " ("
                + why
                + "), chunks are generated on the main thread again",
            t);
    }

    private static synchronized ExecutorService executor() {
        if (executor == null) {
            poolSize = poolSize();
            ThreadPoolExecutor ex = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                30,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "FastLoader terrain worker-" + THREAD_IDS.incrementAndGet());
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                });
            executor = ex;
        }
        return executor;
    }

    static int poolSize() {
        if (threads > 0) return threads;
        int cores = Runtime.getRuntime()
            .availableProcessors();
        // leave the server thread, the client (singleplayer), netty and the IO threads some room
        return Math.max(1, Math.min(6, cores - 3));
    }

    static long key(int x, int z) {
        return (long) x & 0xffffffffL | ((long) z & 0xffffffffL) << 32;
    }

    public static String describe() {
        long h = hits.sum(), w = waitedHits.sum(), m = misses.sum();
        long total = h + w + m;
        return String.format(
            "Terrain prefetch: %s, %d threads | chunks served from background: %d ready + %d after waiting, generated on main: %d (%.1f%% from background) | wait timeouts %d, submitted %d, discarded %d",
            enabled ? "on" : "off",
            executor == null ? 0 : poolSize,
            h,
            w,
            m,
            total == 0 ? 0 : (h + w) * 100.0 / total,
            timeouts.sum(),
            submitted.sum(),
            discarded.sum());
    }

    // ================================================================== per world

    private static final class Generator {

        final IChunkProvider provider;
        final WorldChunkManager chunkManager;

        Generator(IChunkProvider provider, WorldChunkManager chunkManager) {
            this.provider = provider;
            this.chunkManager = chunkManager;
        }
    }

    private static final class WorldState {

        final WorldServer world;
        final AnvilChunkLoader loader;
        final BlockingQueue<Generator> generators;
        final ConcurrentHashMap<Long, Chunk> ready = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Long, CompletableFuture<Chunk>> inFlight = new ConcurrentHashMap<>();
        /** chunks known to be loaded or saved, so they are never prefetched (main thread only) */
        final Set<Long> known = new HashSet<>();
        volatile boolean disabled;
        private long ticks;
        private long lastPlayersKey;
        /** the previous scan stopped because the queue was full: continue next tick */
        private boolean scanCut;

        WorldState(WorldServer world, IChunkProvider main) throws Exception {
            this.world = world;
            IChunkLoader l = ((ChunkLoaderAccess) world.theChunkProviderServer).fastloader$chunkLoader();
            this.loader = l instanceof AnvilChunkLoader ? (AnvilChunkLoader) l : null;
            int n = poolSize();
            this.generators = new ArrayBlockingQueue<>(n);
            for (int i = 0; i < n; i++) generators.add(createGenerator(world, main));
            executor();
        }

        /** Builds a private generator + biome manager on the main thread, without firing map gen events. */
        private static Generator createGenerator(WorldServer world, IChunkProvider main) throws Exception {
            IChunkProvider gen;
            WorldChunkManager cm;
            suppressMapGenEvents = true;
            try {
                gen = world.provider.createChunkGenerator();
                cm = world.provider.terrainType.getChunkManager(world);
            } finally {
                suppressMapGenEvents = false;
            }
            if (gen == null || gen.getClass() != main.getClass()) {
                throw new IllegalStateException(
                    "the world type creates " + (gen == null ? "null"
                        : gen.getClass()
                            .getName())
                        + ", not "
                        + main.getClass()
                            .getName());
            }
            if (cm == null || cm == world.getWorldChunkManager()) {
                throw new IllegalStateException("no private biome manager available");
            }
            int replaced = 0;
            for (Class<?> c = gen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    if (WorldChunkManager.class.isAssignableFrom(f.getType()) && f.getType()
                        .isInstance(cm)) {
                        f.set(gen, cm);
                        replaced++;
                    } else if (MapGenBase.class.isAssignableFrom(f.getType())) {
                        // the private instance must use the same map generators as the main one (no mod replaced
                        // a terrain map generator through InitMapGenEvent), structures are skipped anyway
                        Object mine = f.get(gen);
                        Object theirs = f.get(main);
                        if (mine != null && theirs != null
                            && !(theirs instanceof MapGenStructure)
                            && mine.getClass() != theirs.getClass()) {
                            throw new IllegalStateException(
                                "map generator " + f.getName()
                                    + " is replaced by "
                                    + theirs.getClass()
                                        .getName());
                        }
                    }
                }
            }
            if (replaced == 0) throw new IllegalStateException("generator has no biome manager field");
            return new Generator(gen, cm);
        }

        void schedule(MinecraftServer server) {
            int viewDist = server.getConfigurationManager()
                .getViewDistance();
            int radius = viewDist + extraRadius;

            // a full scan walks (2 * radius + 1)^2 positions: only do it when a player changed chunk, when the last
            // scan was cut short by the queue limit, or every 5 ticks
            long playersKey = 17;
            for (Object o : world.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                playersKey = playersKey * 31 + key((int) Math.floor(p.posX) >> 4, (int) Math.floor(p.posZ) >> 4);
            }
            boolean moved = playersKey != lastPlayersKey;
            if (!moved && !scanCut && ticks++ % 5 != 0) return;
            lastPlayersKey = playersKey;
            scanCut = false;

            // drop results nobody will ask for anymore
            if (!ready.isEmpty()) {
                ready.entrySet()
                    .removeIf(e -> {
                        int x = (int) (long) e.getKey();
                        int z = (int) (e.getKey() >>> 32);
                        if (world.theChunkProviderServer.chunkExists(x, z) || farFromPlayers(x, z, radius + 2)) {
                            discarded.increment();
                            return true;
                        }
                        return false;
                    });
            }
            if (known.size() > 200_000) known.clear();

            int maxInFlight = poolSize * 4;
            if (inFlight.size() >= maxInFlight || ready.size() >= maxReady) {
                scanCut = true;
                return;
            }

            // nearest first for every player
            for (Object o : world.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                int pcx = (int) Math.floor(p.posX) >> 4;
                int pcz = (int) Math.floor(p.posZ) >> 4;
                for (int r = 0; r <= radius; r++) {
                    int size = r == 0 ? 1 : 8 * r;
                    for (int i = 0; i < size; i++) {
                        int dx, dz;
                        if (r == 0) {
                            dx = 0;
                            dz = 0;
                        } else {
                            int side = i / (2 * r), off = i % (2 * r);
                            if (side == 0) {
                                dx = -r + off;
                                dz = -r;
                            } else if (side == 1) {
                                dx = r;
                                dz = -r + off;
                            } else if (side == 2) {
                                dx = r - off;
                                dz = r;
                            } else {
                                dx = -r;
                                dz = r - off;
                            }
                        }
                        if (!consider(pcx + dx, pcz + dz)) continue;
                        if (inFlight.size() >= maxInFlight || ready.size() >= maxReady) {
                            scanCut = true;
                            return;
                        }
                    }
                }
            }
        }

        /** @return true if a task was submitted */
        private boolean consider(int x, int z) {
            Long k = key(x, z);
            if (known.contains(k) || ready.containsKey(k) || inFlight.containsKey(k)) return false;
            if (world.theChunkProviderServer.chunkExists(x, z) || (loader != null && loader.chunkExists(world, x, z))) {
                known.add(k);
                return false;
            }
            submit(x, z, k);
            return true;
        }

        private boolean farFromPlayers(int x, int z, int radius) {
            for (Object o : world.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                int pcx = (int) Math.floor(p.posX) >> 4;
                int pcz = (int) Math.floor(p.posZ) >> 4;
                if (Math.abs(pcx - x) <= radius && Math.abs(pcz - z) <= radius) return false;
            }
            return true;
        }

        private void submit(int x, int z, Long k) {
            submitted.increment();
            CompletableFuture<Chunk> f = new CompletableFuture<>();
            inFlight.put(k, f);
            executor().execute(() -> {
                Chunk chunk = null;
                Throwable error = null;
                try {
                    chunk = generate(x, z);
                } catch (Throwable t) {
                    error = t;
                }
                // publish before completing, so a main thread waiting on the future finds (and removes) it
                if (chunk != null && !disabled) ready.put(k, chunk);
                inFlight.remove(k);
                if (error != null) {
                    f.completeExceptionally(error);
                    disable(this, "worker error at chunk " + x + ", " + z, error);
                } else {
                    f.complete(chunk);
                }
            });
        }

        private Chunk generate(int x, int z) {
            if (disabled) return null;
            Generator g;
            try {
                g = generators.take();
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                return null;
            }
            WORKER_CM.set(g.chunkManager);
            ChunkProfiler.push(Section.PREFETCH);
            try {
                Chunk c = g.provider.provideChunk(x, z);
                if (c == null || c.xPosition != x || c.zPosition != z) {
                    throw new IllegalStateException("generator returned a wrong chunk for " + x + ", " + z);
                }
                return c;
            } finally {
                ChunkProfiler.pop(Section.PREFETCH);
                WORKER_CM.remove();
                generators.add(g);
            }
        }
    }

    /** Implemented by ChunkProviderServer through a mixin. */
    public interface ChunkLoaderAccess {

        IChunkLoader fastloader$chunkLoader();
    }

    /** For tests and reports. */
    public static List<String> activeDimensions() {
        List<String> l = new ArrayList<>();
        for (WorldState ws : STATES.values()) {
            l.add(ws.world.provider.dimensionId + (ws.disabled ? " (disabled)" : ""));
        }
        return l;
    }
}
