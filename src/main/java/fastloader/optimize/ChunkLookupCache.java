package fastloader.optimize;

import net.minecraft.world.chunk.Chunk;

/**
 * Tiny direct-mapped cache in front of {@code ChunkProviderServer.loadedChunkHashMap}.
 * <p>
 * Every World.getBlock / setBlock / light lookup goes through {@code provideChunk}, i.e. a hash map lookup. Worldgen
 * does tens of thousands of them per chunk, almost always on the same few chunks, so a 4-entry cache indexed by the
 * low bit of x and z (a 2x2 neighbourhood) answers most of them without touching the map.
 * <p>
 * A cached chunk is only returned while {@link Chunk#isChunkLoaded} is set (cleared by onChunkUnload) and its own
 * coordinates match, so an unloaded or replaced chunk can never be returned. The cache is only used on the server
 * thread.
 */
public final class ChunkLookupCache {

    private ChunkLookupCache() {}

    public static volatile boolean enabled = true;

    // statistics, server thread only
    public static long hits;
    public static long misses;

    public static int slot(long key) {
        // key = x & 0xffffffff | z << 32 -> (x & 1) | (z & 1) << 1
        return (int) (key & 1L) | (int) ((key >>> 31) & 2L);
    }

    public static Chunk get(Chunk[] cache, long key) {
        Chunk c = cache[slot(key)];
        if (c != null && c.isChunkLoaded && c.xPosition == (int) key && c.zPosition == (int) (key >>> 32)) {
            hits++;
            return c;
        }
        misses++;
        return null;
    }

    public static String describe() {
        long total = hits + misses;
        return String.format(
            "Chunk lookup cache: %s, %d lookups, %.1f%% hits",
            enabled ? "on" : "off",
            total,
            total == 0 ? 0 : hits * 100.0 / total);
    }

    public static void resetStats() {
        hits = 0;
        misses = 0;
    }
}
