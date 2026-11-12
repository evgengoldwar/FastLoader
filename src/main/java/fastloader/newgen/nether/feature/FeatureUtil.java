package fastloader.newgen.nether.feature;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import fastloader.newgen.block.BlockState;

public final class FeatureUtil {

    private FeatureUtil() {}

    public static void set(World w, int x, int y, int z, BlockState s) {
        if (s == null || y < 0 || y >= 256) return;
        w.setBlock(x, y, z, s.block, s.meta, 2);
    }

    public static boolean isAir(World w, int x, int y, int z) {
        return w.isAirBlock(x, y, z);
    }

    /** Air or something a feature may overwrite (plants, fire, vines). */
    public static boolean isReplaceable(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        if (b.isAir(w, x, y, z)) return true;
        Material m = b.getMaterial();
        return m == Material.plants || m == Material.vine
            || m == Material.fire
            || b == Blocks.tallgrass
            || b.isReplaceable(w, x, y, z) && m != Material.lava && m != Material.water;
    }

    public static boolean isSolid(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return b.getMaterial()
            .isSolid() && b.isOpaqueCube();
    }

    public static boolean is(World w, int x, int y, int z, BlockState s) {
        return s != null && w.getBlock(x, y, z) == s.block && w.getBlockMetadata(x, y, z) == s.meta;
    }

    /** Finds the floor (solid block with air above) scanning down from y (inclusive) at most {@code range} blocks. */
    public static int findFloor(World w, int x, int y, int z, int range) {
        for (int i = 0; i < range && y - i > 1; i++) {
            int yy = y - i;
            if (isAir(w, x, yy, z) && isSolid(w, x, yy - 1, z)) return yy - 1;
        }
        return -1;
    }

    /** Finds a ceiling (solid block with air below) scanning up from y. */
    public static int findCeiling(World w, int x, int y, int z, int range) {
        for (int i = 0; i < range && y + i < 126; i++) {
            int yy = y + i;
            if (isAir(w, x, yy, z) && isSolid(w, x, yy + 1, z)) return yy + 1;
        }
        return -1;
    }
}
