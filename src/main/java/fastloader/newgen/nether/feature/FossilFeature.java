package fastloader.newgen.nether.feature;

import java.util.Random;

import net.minecraft.world.World;

import fastloader.newgen.block.NGBlocks;

/** Bone-block skeletons half buried in soul sand valleys: a spine with pairs of curved ribs. */
public final class FossilFeature {

    private FossilFeature() {}

    public static void generate(World w, Random r, int x, int y, int z) {
        int floor = FeatureUtil.findFloor(w, x, y, z, 16);
        if (floor < 0) return;
        int length = 5 + r.nextInt(9);
        boolean alongX = r.nextBoolean();
        int base = floor - r.nextInt(2);
        int ribH = 2 + r.nextInt(3);
        int ribW = 1 + r.nextInt(2);
        // bone block axis meta: 4 = east/west, 8 = north/south for rotated pillars
        int spineMeta = alongX ? 4 : 8;
        for (int i = 0; i < length; i++) {
            int sx = alongX ? x + i : x, sz = alongX ? z : z + i;
            int sy = base + ribH;
            if (FeatureUtil.isReplaceable(w, sx, sy, sz) || NGBlocks.isNetherBase(w.getBlock(sx, sy, sz))) {
                w.setBlock(sx, sy, sz, NGBlocks.BONE_BLOCK.block, spineMeta, 2);
            }
            if (i % 2 == 1 && i < length - 1) {
                for (int side = -1; side <= 1; side += 2) {
                    for (int k = 0; k <= ribH; k++) {
                        int out = ribW + (k == ribH ? 0 : (k == 0 ? 0 : 1));
                        int rx = alongX ? sx : sx + side * out;
                        int rz = alongX ? sz + side * out : sz;
                        int ry = base + k;
                        if (r.nextInt(8) == 0) continue; // weathered
                        if (FeatureUtil.isReplaceable(w, rx, ry, rz) || NGBlocks.isNetherBase(w.getBlock(rx, ry, rz))) {
                            w.setBlock(rx, ry, rz, NGBlocks.BONE_BLOCK.block, 0, 2);
                        }
                    }
                }
            }
        }
    }
}
