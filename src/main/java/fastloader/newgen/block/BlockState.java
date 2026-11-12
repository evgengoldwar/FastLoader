package fastloader.newgen.block;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/** A block + metadata pair used by the generators. */
public final class BlockState {

    public static final BlockState AIR = new BlockState(Blocks.air, 0);

    public final Block block;
    public final int meta;

    public BlockState(Block block, int meta) {
        this.block = block;
        this.meta = meta;
    }

    public static BlockState of(Block block) {
        return new BlockState(block, 0);
    }

    public static BlockState of(Block block, int meta) {
        return new BlockState(block, meta);
    }

    public boolean is(Block b, int m) {
        return block == b && meta == m;
    }

    public boolean matches(Block b, int m) {
        return block == b && (meta == m || meta == -1);
    }

    @Override
    public String toString() {
        return Block.blockRegistry.getNameForObject(block) + ":" + meta;
    }
}
