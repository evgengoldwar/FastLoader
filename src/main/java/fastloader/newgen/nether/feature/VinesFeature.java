package fastloader.newgen.nether.feature;

import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import fastloader.newgen.block.NGBlocks;

/** Weeping vines hanging from crimson ceilings and twisting vines growing up from warped floors. */
public final class VinesFeature {

    private VinesFeature() {}

    public static void weeping(World w, Random r, int x, int y, int z) {
        int ceil = FeatureUtil.findCeiling(w, x, y, z, 24);
        if (ceil < 0) return;
        if (w.getBlock(x, ceil, z) != net.minecraft.init.Blocks.netherrack
            && !FeatureUtil.is(w, x, ceil, z, NGBlocks.NETHER_WART_BLOCK)) return;
        // wart patch on the ceiling
        for (int i = 0; i < 200; i++) {
            int px = x + r.nextInt(6) - r.nextInt(6);
            int py = ceil - r.nextInt(2) + r.nextInt(2);
            int pz = z + r.nextInt(6) - r.nextInt(6);
            if (!FeatureUtil.isAir(w, px, py, pz)) continue;
            if (w.getBlock(px, py + 1, pz) == Blocks.netherrack
                || FeatureUtil.is(w, px, py + 1, pz, NGBlocks.NETHER_WART_BLOCK)) {
                FeatureUtil.set(w, px, py, pz, NGBlocks.NETHER_WART_BLOCK);
            }
        }
        // the vines themselves
        for (int i = 0; i < 100; i++) {
            int px = x + r.nextInt(8) - r.nextInt(8);
            int py = ceil + r.nextInt(2) - r.nextInt(7);
            int pz = z + r.nextInt(8) - r.nextInt(8);
            if (!FeatureUtil.isAir(w, px, py, pz)) continue;
            if (!FeatureUtil.isSolid(w, px, py + 1, pz)) continue;
            int len = 1 + r.nextInt(8);
            if (r.nextInt(6) == 0) len *= 2;
            for (int k = 0; k < len && FeatureUtil.isAir(w, px, py - k, pz); k++) {
                FeatureUtil.set(w, px, py - k, pz, NGBlocks.WEEPING_VINES);
            }
        }
    }

    public static void twisting(World w, Random r, int x, int y, int z) {
        for (int i = 0; i < 128; i++) {
            int px = x + r.nextInt(8) - r.nextInt(8);
            int pz = z + r.nextInt(8) - r.nextInt(8);
            int floor = FeatureUtil.findFloor(w, px, y + 4, pz, 10);
            if (floor < 0) continue;
            if (!FeatureUtil.is(w, px, floor, pz, NGBlocks.WARPED_NYLIUM)
                && !FeatureUtil.is(w, px, floor, pz, NGBlocks.WARPED_WART_BLOCK)) continue;
            if (r.nextInt(3) != 0) continue;
            int len = 1 + r.nextInt(8);
            if (r.nextInt(5) == 0) len += r.nextInt(8);
            for (int k = 1; k <= len && FeatureUtil.isAir(w, px, floor + k, pz); k++) {
                FeatureUtil.set(w, px, floor + k, pz, NGBlocks.TWISTING_VINES);
            }
        }
    }
}
