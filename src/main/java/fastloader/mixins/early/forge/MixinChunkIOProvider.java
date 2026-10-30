package fastloader.mixins.early.forge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/**
 * Forge's async chunk loading. Stage 2 runs on the main thread (from ChunkIOExecutor.tick for queued loads, or inline
 * for sync loads) and registers entities, fires ChunkDataEvent.Load, onChunkLoad and population.
 * The class and its QueuedChunk argument are package private, hence the string target and the argument-less handlers.
 * The loaded chunk itself is counted in {@link fastloader.profiler.ChunkProfiler#onLoadEntities}.
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.common.chunkio.ChunkIOProvider", remap = false)
public class MixinChunkIOProvider {

    @Inject(
        method = "callStage2(Lnet/minecraftforge/common/chunkio/QueuedChunk;Lnet/minecraft/world/chunk/Chunk;)V",
        at = @At("HEAD"),
        require = 0)
    private void fastloader$stage2Head(CallbackInfo ci) {
        ChunkProfiler.push(Section.ASYNC_FINISH);
    }

    @Inject(
        method = "callStage2(Lnet/minecraftforge/common/chunkio/QueuedChunk;Lnet/minecraft/world/chunk/Chunk;)V",
        at = @At("RETURN"),
        require = 0)
    private void fastloader$stage2Return(CallbackInfo ci) {
        ChunkProfiler.pop(Section.ASYNC_FINISH);
    }
}
