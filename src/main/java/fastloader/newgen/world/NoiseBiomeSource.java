package fastloader.newgen.world;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

/**
 * Base class for biome sources that pick biomes from continuous climate noise (modern "multi noise" sources)
 * instead of the old layer stack. Biomes are resolved on a 4x4 block grid ("quart" positions), like modern versions.
 */
public abstract class NoiseBiomeSource extends WorldChunkManager {

    private static final int CACHE_BITS = 12;
    private static final int CACHE_SIZE = 1 << CACHE_BITS;
    private final long[] cacheKeys = new long[CACHE_SIZE];
    private final BiomeGenBase[] cacheValues = new BiomeGenBase[CACHE_SIZE];

    protected NoiseBiomeSource() {
        super();
        Arrays.fill(cacheKeys, Long.MIN_VALUE);
    }

    /** Biome at a quart position (block coordinate >> 2). */
    protected abstract BiomeGenBase computeBiome(int quartX, int quartZ);

    public final synchronized BiomeGenBase getBiomeAtQuart(int qx, int qz) {
        long key = ((long) qx << 32) ^ (qz & 0xFFFFFFFFL);
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> (64 - CACHE_BITS));
        if (cacheKeys[slot] == key) return cacheValues[slot];
        BiomeGenBase b = computeBiome(qx, qz);
        cacheKeys[slot] = key;
        cacheValues[slot] = b;
        return b;
    }

    /** Block-level biome: the quart grid is jittered per block so borders are ragged instead of 4-block steps. */
    public BiomeGenBase getBiomeAtBlock(int x, int z) {
        long h = (x * 3129871L) ^ (z * 116129781L);
        h = h * h * 42317861L + h * 11L;
        int jx = (int) ((h >> 16) & 3) - 1;
        int jz = (int) ((h >> 20) & 3) - 1;
        return getBiomeAtQuart((x + jx) >> 2, (z + jz) >> 2);
    }

    @Override
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        return getBiomeAtBlock(x, z);
    }

    @Override
    public float[] getRainfall(float[] out, int x, int z, int w, int h) {
        if (out == null || out.length < w * h) out = new float[w * h];
        for (int dz = 0; dz < h; dz++) for (int dx = 0; dx < w; dx++) {
            float r = getBiomeAtBlock(x + dx, z + dz).getIntRainfall() / 65536.0F;
            out[dx + dz * w] = Math.min(r, 1.0F);
        }
        return out;
    }

    @Override
    public float getTemperatureAtHeight(float temp, int y) {
        return temp;
    }

    /** Biomes for generation, in 4x4 block units, like vanilla. */
    @Override
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] out, int qx, int qz, int w, int h) {
        if (out == null || out.length < w * h) out = new BiomeGenBase[w * h];
        for (int dz = 0; dz < h; dz++)
            for (int dx = 0; dx < w; dx++) out[dx + dz * w] = getBiomeAtQuart(qx + dx, qz + dz);
        return out;
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] out, int x, int z, int w, int h) {
        return getBiomeGenAt(out, x, z, w, h, true);
    }

    @Override
    public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] out, int x, int z, int w, int h, boolean cache) {
        if (out == null || out.length < w * h) out = new BiomeGenBase[w * h];
        for (int dz = 0; dz < h; dz++)
            for (int dx = 0; dx < w; dx++) out[dx + dz * w] = getBiomeAtBlock(x + dx, z + dz);
        return out;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean areBiomesViable(int x, int z, int radius, List allowed) {
        int x0 = (x - radius) >> 2, z0 = (z - radius) >> 2, x1 = (x + radius) >> 2, z1 = (z + radius) >> 2;
        for (int qz = z0; qz <= z1; qz++) for (int qx = x0; qx <= x1; qx++) {
            if (!allowed.contains(getBiomeAtQuart(qx, qz))) return false;
        }
        return true;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ChunkPosition findBiomePosition(int x, int z, int radius, List allowed, Random random) {
        int x0 = (x - radius) >> 2, z0 = (z - radius) >> 2, x1 = (x + radius) >> 2, z1 = (z + radius) >> 2;
        ChunkPosition found = null;
        int count = 0;
        for (int qz = z0; qz <= z1; qz += 2) for (int qx = x0; qx <= x1; qx += 2) {
            if (allowed.contains(getBiomeAtQuart(qx, qz)) && (found == null || random.nextInt(count + 1) == 0)) {
                found = new ChunkPosition((qx << 2) + 2, 0, (qz << 2) + 2);
                count++;
            }
        }
        return found;
    }

    @Override
    public void cleanupCache() {}

    public static long seedOf(World world) {
        return world.getSeed();
    }
}
