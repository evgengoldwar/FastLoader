package fastloader.newgen.overworld.feature;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenAbstractTree;

import fastloader.newgen.block.BlockState;
import fastloader.newgen.block.NGBlocks;

/**
 * Cherry tree: a trunk that forks into two or three bent branches, each with a wide, flat, drooping canopy. Uses Et
 * Futurum cherry logs/leaves when present, otherwise birch logs with pink-ish (birch) leaves.
 */
public class CherryTreeFeature extends WorldGenAbstractTree {

    public CherryTreeFeature() {
        super(false);
    }

    @Override
    public boolean generate(World w, Random r, int x, int y, int z) {
        Block soil = w.getBlock(x, y - 1, z);
        if (soil != Blocks.grass && soil != Blocks.dirt) return false;
        BlockState log = NGBlocks.CHERRY_LOG != null ? NGBlocks.CHERRY_LOG : BlockState.of(Blocks.log, 2);
        BlockState leaves = NGBlocks.CHERRY_LEAVES != null ? NGBlocks.CHERRY_LEAVES : BlockState.of(Blocks.leaves, 2);
        int trunk = 3 + r.nextInt(3);
        for (int i = 0; i < trunk + 2; i++) if (!canReplace(w, x, y + i, z)) return false;
        for (int i = 0; i < trunk; i++) setBlockAndNotifyAdequately(w, x, y + i, z, log.block, log.meta);
        int branches = 2 + r.nextInt(2);
        double angle = r.nextDouble() * Math.PI * 2;
        for (int b = 0; b < branches; b++) {
            double a = angle + b * (Math.PI * 2 / branches) + r.nextGaussian() * 0.3;
            int len = 2 + r.nextInt(3);
            int bx = x, by = y + trunk - 1, bz = z;
            for (int i = 1; i <= len; i++) {
                bx = x + (int) Math.round(Math.cos(a) * i);
                bz = z + (int) Math.round(Math.sin(a) * i);
                by = y + trunk - 1 + i;
                if (canReplace(w, bx, by, bz)) setBlockAndNotifyAdequately(w, bx, by, bz, log.block, log.meta | 12);
            }
            canopy(w, r, bx, by + 1, bz, leaves);
        }
        setBlockAndNotifyAdequately(w, x, y - 1, z, Blocks.dirt, 0);
        return true;
    }

    private void canopy(World w, Random r, int cx, int cy, int cz, BlockState leaves) {
        int rad = 3 + r.nextInt(2);
        for (int dy = -1; dy <= 1; dy++) {
            int lr = dy == 1 ? rad - 1 : rad;
            for (int dx = -lr; dx <= lr; dx++) for (int dz = -lr; dz <= lr; dz++) {
                double d = dx * dx + dz * dz;
                if (d > lr * lr + 1) continue;
                if (d > (lr - 1) * (lr - 1) && r.nextInt(3) == 0) continue;
                int px = cx + dx, py = cy + dy, pz = cz + dz;
                if (w.isAirBlock(px, py, pz)) setBlockAndNotifyAdequately(w, px, py, pz, leaves.block, leaves.meta);
            }
        }
        // drooping edges
        for (int i = 0; i < 10; i++) {
            int px = cx + r.nextInt(rad * 2 + 1) - rad, pz = cz + r.nextInt(rad * 2 + 1) - rad;
            if (w.isAirBlock(px, cy - 2, pz) && w.getBlock(px, cy - 1, pz) == leaves.block) {
                setBlockAndNotifyAdequately(w, px, cy - 2, pz, leaves.block, leaves.meta);
            }
        }
    }

    private boolean canReplace(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return b.isAir(w, x, y, z) || b.isLeaves(w, x, y, z) || b == Blocks.tallgrass || b.isReplaceable(w, x, y, z);
    }
}
