package fastloader.mixins.early.minecraft;

import net.minecraft.block.Block;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

@Mixin(Chunk.class)
public class MixinChunk {

    @Inject(method = "onChunkLoad", at = @At("HEAD"), require = 0)
    private void fastloader$onLoadHead(CallbackInfo ci) {
        ChunkProfiler.push(Section.ON_LOAD);
    }

    @Inject(method = "onChunkLoad", at = @At("RETURN"), require = 0)
    private void fastloader$onLoadReturn(CallbackInfo ci) {
        ChunkProfiler.pop(Section.ON_LOAD);
    }

    @Inject(method = "onChunkUnload", at = @At("HEAD"), require = 0)
    private void fastloader$onUnloadHead(CallbackInfo ci) {
        ChunkProfiler.onChunkUnloaded();
        ChunkProfiler.push(Section.ON_UNLOAD);
    }

    @Inject(method = "onChunkUnload", at = @At("RETURN"), require = 0)
    private void fastloader$onUnloadReturn(CallbackInfo ci) {
        ChunkProfiler.pop(Section.ON_UNLOAD);
    }

    @Inject(method = "populateChunk", at = @At("HEAD"), require = 0)
    private void fastloader$populateChunkHead(IChunkProvider a, IChunkProvider b, int x, int z, CallbackInfo ci) {
        ChunkProfiler.push(Section.POPULATE_CHECK);
    }

    @Inject(method = "populateChunk", at = @At("RETURN"), require = 0)
    private void fastloader$populateChunkReturn(IChunkProvider a, IChunkProvider b, int x, int z, CallbackInfo ci) {
        ChunkProfiler.pop(Section.POPULATE_CHECK);
    }

    @Inject(method = "func_150809_p", at = @At("HEAD"), require = 0)
    private void fastloader$lightInitHead(CallbackInfo ci) {
        ChunkProfiler.push(Section.LIGHT_INIT);
    }

    @Inject(method = "func_150809_p", at = @At("RETURN"), require = 0)
    private void fastloader$lightInitReturn(CallbackInfo ci) {
        ChunkProfiler.pop(Section.LIGHT_INIT);
    }

    @Inject(method = "generateSkylightMap", at = @At("HEAD"), require = 0)
    private void fastloader$skylightHead(CallbackInfo ci) {
        ChunkProfiler.push(Section.SKYLIGHT);
    }

    @Inject(method = "generateSkylightMap", at = @At("RETURN"), require = 0)
    private void fastloader$skylightReturn(CallbackInfo ci) {
        ChunkProfiler.pop(Section.SKYLIGHT);
    }

    /** Called for every loaded chunk every tick (WorldServer.func_147456_g) - only timed when it actually works. */
    @Inject(method = "func_150804_b", at = @At("HEAD"), require = 0)
    private void fastloader$lightCheckHead(boolean flag, CallbackInfo ci) {
        ChunkProfiler.push(Section.LIGHT_CHECK);
    }

    @Inject(method = "func_150804_b", at = @At("RETURN"), require = 0)
    private void fastloader$lightCheckReturn(boolean flag, CallbackInfo ci) {
        ChunkProfiler.pop(Section.LIGHT_CHECK);
    }

    // ---- setBlock internals, only timed inside a setBlock sample

    @Inject(method = "func_150807_a", at = @At("HEAD"), require = 0)
    private void fastloader$storeHead(int x, int y, int z, Block block, int meta, CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pushInSample(Section.SB_CHUNK);
    }

    @Inject(method = "func_150807_a", at = @At("RETURN"), require = 0)
    private void fastloader$storeReturn(int x, int y, int z, Block block, int meta,
        CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pop(Section.SB_CHUNK);
    }

    @Inject(method = "relightBlock", at = @At("HEAD"), require = 0)
    private void fastloader$relightHead(int x, int y, int z, CallbackInfo ci) {
        ChunkProfiler.pushInSample(Section.SB_RELIGHT);
    }

    @Inject(method = "relightBlock", at = @At("RETURN"), require = 0)
    private void fastloader$relightReturn(int x, int y, int z, CallbackInfo ci) {
        ChunkProfiler.pop(Section.SB_RELIGHT);
    }
}
