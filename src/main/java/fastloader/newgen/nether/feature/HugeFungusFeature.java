package fastloader.newgen.nether.feature;

import java.util.Random;

import net.minecraft.world.World;

import fastloader.newgen.block.BlockState;
import fastloader.newgen.block.NGBlocks;

/** Huge crimson / warped fungus: a stem (sometimes 3x3 thick) topped with a wart-block hat lit by shroomlights. */
public final class HugeFungusFeature {

    private HugeFungusFeature() {}

    public static boolean generate(World w, Random r, int x, int y, int z, boolean crimson, boolean planted) {
        BlockState nylium = crimson ? NGBlocks.CRIMSON_NYLIUM : NGBlocks.WARPED_NYLIUM;
        if (!FeatureUtil.is(w, x, y - 1, z, nylium)) return false;
        BlockState stem = crimson ? NGBlocks.CRIMSON_STEM : NGBlocks.WARPED_STEM;
        BlockState hat = crimson ? NGBlocks.NETHER_WART_BLOCK : NGBlocks.WARPED_WART_BLOCK;

        int height = 4 + r.nextInt(10);
        if (r.nextInt(12) == 0) height *= 2;
        if (!planted) {
            if (y + height + 1 >= 125) return false;
        }
        // keep room under the ceiling
        for (int i = 1; i <= height; i++) {
            if (!FeatureUtil.isReplaceable(w, x, y + i, z)) {
                if (i < 5) return false;
                height = i - 1;
                break;
            }
        }
        boolean thick = !planted && r.nextFloat() < 0.06F;
        int hatHeight = Math.min(r.nextInt(1 + height / 3) + 5, height);
        int top = y + height;

        // stem
        int sr = thick ? 1 : 0;
        for (int dy = 0; dy < height; dy++) {
            for (int dx = -sr; dx <= sr; dx++) for (int dz = -sr; dz <= sr; dz++) {
                boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                if (corner && r.nextFloat() < 0.1F) continue;
                if (FeatureUtil.isReplaceable(w, x + dx, y + dy, z + dz))
                    FeatureUtil.set(w, x + dx, y + dy, z + dz, stem);
            }
        }

        // hat
        int hatStart = top - hatHeight;
        for (int yy = hatStart; yy <= top; yy++) {
            int layer = yy - hatStart;
            int radius;
            if (yy == top) radius = 1 + sr;
            else if (layer < hatHeight / 3) radius = 3 + sr;
            else radius = 2 + sr;
            if (hatHeight > 8 && layer < hatHeight - 3) radius++;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                int px = x + dx, pz = z + dz;
                boolean edgeX = Math.abs(dx) == radius, edgeZ = Math.abs(dz) == radius;
                boolean corner = edgeX && edgeZ;
                boolean edge = edgeX || edgeZ;
                if (Math.abs(dx) <= sr && Math.abs(dz) <= sr && yy < top) continue; // stem
                if (!FeatureUtil.isReplaceable(w, px, yy, pz)) continue;
                if (yy == top) {
                    if (!corner || r.nextFloat() < 0.4F) FeatureUtil.set(w, px, yy, pz, hat);
                    continue;
                }
                if (corner) {
                    if (r.nextFloat() < 0.1F) FeatureUtil.set(w, px, yy, pz, hat);
                    continue;
                }
                if (edge) {
                    if (r.nextFloat() < 0.98F) {
                        FeatureUtil.set(w, px, yy, pz, hat);
                        if (crimson && yy == hatStart && r.nextFloat() < 0.25F) hangVines(w, r, px, yy - 1, pz);
                    }
                    continue;
                }
                // inside the hat: sparse wart with shroomlights clinging to it
                float f = r.nextFloat();
                if (f < 0.12F) FeatureUtil.set(w, px, yy, pz, hat);
                else if (f < 0.17F) FeatureUtil.set(w, px, yy, pz, NGBlocks.SHROOMLIGHT);
            }
        }
        return true;
    }

    private static void hangVines(World w, Random r, int x, int y, int z) {
        int len = 1 + r.nextInt(3);
        for (int i = 0; i < len && FeatureUtil.isAir(w, x, y - i, z); i++) {
            FeatureUtil.set(w, x, y - i, z, NGBlocks.WEEPING_VINES);
        }
    }
}
