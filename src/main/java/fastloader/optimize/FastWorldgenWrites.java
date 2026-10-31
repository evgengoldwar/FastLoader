package fastloader.optimize;

import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import fastloader.profiler.ChunkProfiler;

/**
 * Fast path for the bulk of worldgen block writes: an opaque block replaced by another opaque block (stone to dirt,
 * gravel, ores, bedrock...) inside a chunk the client does not have yet.
 * <p>
 * For such a write {@code World.setBlock} (see World.setBlock / Chunk.func_150807_a / World.markAndNotifyBlock) does
 * nothing but store the block and its metadata and mark the chunk modified:
 * <ul>
 * <li>heightmap / precipitation map / sky light: unchanged, the column stays opaque at this height (we require y below
 * the heightmap and keep the precipitation map rule);</li>
 * <li>light: both blocks have opacity 255 and emit no light, so the light check finds nothing to change;</li>
 * <li>block callbacks: only used for classes that do not override onBlockAdded, breakBlock and onBlockPreDestroy, have
 * no tile entity and are not falling blocks (gravel / sand schedule a tick in onBlockAdded);</li>
 * <li>client sync and IWorldAccess listeners: vanilla only calls them when chunk.func_150802_k() is true, we require it
 * to be false; neighbour notifications only happen with flag 1, we require it to be clear.</li>
 * </ul>
 * Only used on the server thread while ChunkProviderServer.populate runs.
 */
public final class FastWorldgenWrites {

    private FastWorldgenWrites() {}

    public static volatile boolean enabled = true;

    /** ChunkProviderServer.populate nesting depth on the server thread. */
    public static int populateDepth;

    private static final ClassValue<Boolean> PLAIN = new ClassValue<Boolean>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            if (BlockFalling.class.isAssignableFrom(type)) return false;
            return notOverridden(
                type,
                new String[] { "onBlockAdded", "func_149726_b" },
                World.class,
                int.class,
                int.class,
                int.class)
                && notOverridden(
                    type,
                    new String[] { "breakBlock", "func_149749_a" },
                    World.class,
                    int.class,
                    int.class,
                    int.class,
                    Block.class,
                    int.class)
                && notOverridden(
                    type,
                    new String[] { "onBlockPreDestroy", "func_149725_f" },
                    World.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class);
        }
    };

    private static boolean notOverridden(Class<?> type, String[] names, Class<?>... args) {
        for (String name : names) {
            try {
                Method m = type.getMethod(name, args);
                return m.getDeclaringClass() == Block.class;
            } catch (NoSuchMethodException ignored) {
                // try the other (dev / obfuscated) name
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    /** @return true if the write was done here, the caller must then return true from setBlock */
    public static boolean tryWrite(World world, int x, int y, int z, Block block, int meta, int flags) {
        if (populateDepth == 0 || !enabled || (flags & 1) != 0 || y < 0 || y >= 256) return false;
        if (world.isRemote || world.captureBlockSnapshots) return false;
        if (Thread.currentThread() != ChunkProfiler.serverThread()) return false;
        if (x < -30000000 || z < -30000000 || x >= 30000000 || z >= 30000000) return false;
        int cx = x >> 4, cz = z >> 4;
        if (!world.getChunkProvider()
            .chunkExists(cx, cz)) return false;
        Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
        if (chunk.func_150802_k()) return false; // the client may have it: let vanilla sync it
        int lx = x & 15, lz = z & 15;
        int column = lz << 4 | lx;
        if (y >= chunk.heightMap[column] - 1) return false;
        ExtendedBlockStorage storage = chunk.getBlockStorageArray()[y >> 4];
        if (storage == null) return false;
        int ly = y & 15;
        Block old = storage.getBlockByExtId(lx, ly, lz);
        int oldMeta = storage.getExtBlockMetadata(lx, ly, lz);
        if (old == block && oldMeta == meta) return false; // vanilla returns false here, keep its result
        if (!isPlain(old, oldMeta) || !isPlain(block, meta)) return false;
        if (old.getLightOpacity(world, x, y, z) != 255 || old.getLightValue(world, x, y, z) != 0) return false;
        if (block.getLightOpacity(world, x, y, z) != 255 || block.getLightValue(world, x, y, z) != 0) return false;

        if (y >= chunk.precipitationHeightMap[column] - 1) chunk.precipitationHeightMap[column] = -999;
        storage.func_150818_a(lx, ly, lz, block);
        storage.setExtBlockMetadata(lx, ly, lz, meta);
        if (storage.getBlockByExtId(lx, ly, lz) != block) {
            // a storage hook refused the block: put the old one back and let vanilla handle it
            storage.func_150818_a(lx, ly, lz, old);
            storage.setExtBlockMetadata(lx, ly, lz, oldMeta);
            return false;
        }
        chunk.isModified = true;
        return true;
    }

    private static boolean isPlain(Block b, int meta) {
        return !b.hasTileEntity(meta) && PLAIN.get(b.getClass());
    }
}
