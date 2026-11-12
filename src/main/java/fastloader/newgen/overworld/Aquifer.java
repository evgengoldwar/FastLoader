package fastloader.newgen.overworld;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import fastloader.newgen.noise.NoiseMath;

/**
 * Simplified modern aquifers: underground space is split into cells (16x12x16) and each cell gets its own fluid
 * level - flooded with water, lava (deep down) or dry. Where two neighbouring cells disagree, a stone barrier is left
 * so no water walls float in the caves. Space near and above the surface uses the global sea level, giving oceans,
 * rivers and flooded cave mouths.
 */
public final class Aquifer {

    public static final int SEA_LEVEL = 63;
    private static final int CW = 16, CH = 12;
    private static final int NONE = Integer.MIN_VALUE;

    private final long seed;
    private final boolean lavaDeep;

    public Aquifer(long seed, boolean lavaDeep) {
        this.seed = seed;
        this.lavaDeep = lavaDeep;
    }

    /** Fluid level of a cell, NONE if dry. Encoded as y + (lava ? 1000 : 0). */
    private int cellLevel(int gx, int gy, int gz) {
        long h = NoiseMath.hash(seed, gx, gy, gz);
        h = h * 6364136223846793005L + 1442695040888963407L;
        int roll = (int) ((h >>> 33) % 100);
        int baseY = gy * CH;
        if (lavaDeep && baseY <= 8) return 10 + 1000;
        if (roll < 30) {
            int level = baseY + (int) ((h >>> 20) & 0xF) % CH;
            if (baseY < 20 && lavaDeep && roll < 12) return level + 1000;
            return level;
        }
        return NONE;
    }

    private int levelAt(int x, int y, int z) {
        return cellLevel(Math.floorDiv(x, CW), Math.floorDiv(y, CH), Math.floorDiv(z, CW));
    }

    private static boolean filled(int code, int y) {
        if (code == NONE) return false;
        int level = code >= 900 ? code - 1000 : code;
        return y <= level;
    }

    /**
     * Block for a non-solid position.
     *
     * @param surface approximate terrain surface height of this column
     */
    public Block compute(int x, int y, int z, double surface, boolean enabled) {
        if (y >= surface - 8 || !enabled) {
            return y < SEA_LEVEL && y >= surface - 12 ? Blocks.water : (lavaDeep && y <= 10 ? Blocks.lava : Blocks.air);
        }
        int code = levelAt(x, y, z);
        if (!filled(code, y)) {
            if (lavaDeep && y <= 10) return Blocks.lava;
            return Blocks.air;
        }
        // barrier check against the horizontal/below neighbours
        int gx = Math.floorDiv(x, CW), gy = Math.floorDiv(y, CH), gz = Math.floorDiv(z, CW);
        int lx = Math.floorMod(x, CW), ly = Math.floorMod(y, CH), lz = Math.floorMod(z, CW);
        if (lx <= 1 && !filled(cellLevel(gx - 1, gy, gz), y)) return Blocks.stone;
        if (lx >= CW - 2 && !filled(cellLevel(gx + 1, gy, gz), y)) return Blocks.stone;
        if (lz <= 1 && !filled(cellLevel(gx, gy, gz - 1), y)) return Blocks.stone;
        if (lz >= CW - 2 && !filled(cellLevel(gx, gy, gz + 1), y)) return Blocks.stone;
        if (ly <= 1 && !filled(cellLevel(gx, gy - 1, gz), y)) return Blocks.stone;
        return code >= 900 ? Blocks.lava : Blocks.water;
    }
}
