package fastloader.newgen.nether.feature;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import fastloader.newgen.block.BlockState;
import fastloader.newgen.block.NGBlocks;

/** Basalt delta pools, basalt columns, basalt pillars and replace-blobs. */
public final class BasaltFeatures {

    private BasaltFeatures() {}

    private static boolean isLava(World w, int x, int y, int z) {
        return w.getBlock(x, y, z)
            .getMaterial() == Material.lava;
    }

    /**
     * Delta: a shallow lava pool sunk into the floor, rimmed with magma. Pools only form where the lava is fully
     * enclosed so they never spill.
     */
    public static void delta(World w, Random r, int x, int y, int z) {
        int radius = 3 + r.nextInt(5);
        int rim = r.nextInt(3);
        boolean lava = r.nextInt(5) != 0;
        double rx = radius + r.nextDouble(), rz = radius + r.nextDouble();
        for (int dx = -radius - rim; dx <= radius + rim; dx++) for (int dz = -radius - rim; dz <= radius + rim; dz++) {
            double dist = (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz);
            int px = x + dx, pz = z + dz;
            int floor = FeatureUtil.findFloor(w, px, y + 2, pz, 5);
            if (floor < 0) continue;
            if (dist <= 1.0) {
                if (!enclosed(w, px, floor, pz)) continue;
                FeatureUtil.set(w, px, floor, pz, lava ? BlockState.of(Blocks.lava) : NGBlocks.MAGMA);
            } else if (rim > 0 && dist <= Math.pow((radius + rim) / (double) radius, 2)) {
                Block b = w.getBlock(px, floor, pz);
                if (NGBlocks.isNetherBase(b) && r.nextFloat() < 0.85F)
                    FeatureUtil.set(w, px, floor, pz, NGBlocks.MAGMA);
            }
        }
    }

    private static boolean enclosed(World w, int x, int y, int z) {
        return (FeatureUtil.isSolid(w, x, y - 1, z) || isLava(w, x, y - 1, z))
            && (FeatureUtil.isSolid(w, x + 1, y, z) || isLava(w, x + 1, y, z))
            && (FeatureUtil.isSolid(w, x - 1, y, z) || isLava(w, x - 1, y, z))
            && (FeatureUtil.isSolid(w, x, y, z + 1) || isLava(w, x, y, z + 1))
            && (FeatureUtil.isSolid(w, x, y, z - 1) || isLava(w, x, y, z - 1));
    }

    /** Columns of basalt rising from the floor (and lava) and, when a ceiling is close, hanging from it. */
    public static void columns(World w, Random r, int x, int y, int z, int reachMin, int reachMax, int hMin, int hMax) {
        int reach = reachMin + r.nextInt(reachMax - reachMin + 1);
        int height = hMin + r.nextInt(hMax - hMin + 1);
        int ceil = FeatureUtil.findCeiling(w, x, y + 1, z, height * 2 + 4);
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
            int dist = Math.abs(dx) + Math.abs(dz);
            if (dist > reach + 1) continue;
            if (r.nextFloat() < 0.15F) continue;
            int px = x + dx, pz = z + dz;
            int colH = Math.max(1, height - r.nextInt(dist + 1) - dist / 2);
            int floor = findFloorOrLava(w, px, y + 3, pz, 8);
            if (floor >= 0) {
                for (int k = 1; k <= colH; k++) {
                    int py = floor + k;
                    if (!(FeatureUtil.isReplaceable(w, px, py, pz) || isLava(w, px, py, pz))) break;
                    FeatureUtil.set(w, px, py, pz, NGBlocks.BASALT);
                }
            }
            if (ceil > 0) {
                int c = FeatureUtil.findCeiling(w, px, ceil - 3, pz, 6);
                if (c > 0) for (int k = 1; k <= colH; k++) {
                    if (!FeatureUtil.isReplaceable(w, px, c - k, pz)) break;
                    FeatureUtil.set(w, px, c - k, pz, NGBlocks.BASALT);
                }
            }
        }
    }

    private static int findFloorOrLava(World w, int x, int y, int z, int range) {
        for (int i = 0; i < range && y - i > 1; i++) {
            int yy = y - i;
            if (FeatureUtil.isAir(w, x, yy, z)) {
                Block below = w.getBlock(x, yy - 1, z);
                if (below.getMaterial() == Material.lava || FeatureUtil.isSolid(w, x, yy - 1, z)) return yy - 1;
            }
        }
        return -1;
    }

    /** Soul sand valley basalt pillar: connects the ceiling down towards the floor, with a ragged outline. */
    public static void pillar(World w, Random r, int x, int y, int z) {
        if (!FeatureUtil.isAir(w, x, y, z) || !FeatureUtil.isSolid(w, x, y + 1, z)) return;
        boolean n = true, s = true, e = true, wst = true;
        int yy = y;
        while (yy > 1 && FeatureUtil.isAir(w, x, yy, z)) {
            FeatureUtil.set(w, x, yy, z, NGBlocks.BASALT);
            n = n && side(w, r, x, yy, z - 1);
            s = s && side(w, r, x, yy, z + 1);
            e = e && side(w, r, x + 1, yy, z);
            wst = wst && side(w, r, x - 1, yy, z);
            yy--;
        }
        // flare at the bottom
        yy++;
        side(w, r, x, yy, z - 1);
        side(w, r, x, yy, z + 1);
        side(w, r, x + 1, yy, z);
        side(w, r, x - 1, yy, z);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (r.nextInt(10) == 0 && FeatureUtil.isAir(w, x + dx, yy - 1, z + dz)) {
                FeatureUtil.set(w, x + dx, yy - 1, z + dz, NGBlocks.BASALT);
            }
        }
    }

    private static boolean side(World w, Random r, int x, int y, int z) {
        if (r.nextInt(10) != 0 && FeatureUtil.isAir(w, x, y, z)) {
            FeatureUtil.set(w, x, y, z, NGBlocks.BASALT);
            return true;
        }
        return false;
    }

    /** Replaces netherrack with the given block in a lumpy sphere (blackstone / basalt blobs). */
    public static void replaceBlob(World w, Random r, int x, int y, int z, BlockState state, int minR, int maxR) {
        int rad = minR + r.nextInt(maxR - minR + 1);
        int rx = rad - r.nextInt(2), ry = rad - r.nextInt(2), rz = rad - r.nextInt(2);
        for (int dx = -rx; dx <= rx; dx++) for (int dy = -ry; dy <= ry; dy++) for (int dz = -rz; dz <= rz; dz++) {
            double d = (dx * dx) / (double) (rx * rx + 1) + (dy * dy) / (double) (ry * ry + 1)
                + (dz * dz) / (double) (rz * rz + 1);
            if (d > 1.0) continue;
            int py = y + dy;
            if (py < 1 || py > 126) continue;
            if (w.getBlock(x + dx, py, z + dz) == Blocks.netherrack) FeatureUtil.set(w, x + dx, py, z + dz, state);
        }
    }
}
