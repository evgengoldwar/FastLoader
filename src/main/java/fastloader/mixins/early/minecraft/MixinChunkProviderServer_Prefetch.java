package fastloader.mixins.early.minecraft;

import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import fastloader.optimize.TerrainPrefetcher;

/** Lets the terrain prefetcher check whether a chunk already exists on disk. */
@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServer_Prefetch implements TerrainPrefetcher.ChunkLoaderAccess {

    @Shadow
    private IChunkLoader currentChunkLoader;

    @Override
    public IChunkLoader fastloader$chunkLoader() {
        return currentChunkLoader;
    }
}
