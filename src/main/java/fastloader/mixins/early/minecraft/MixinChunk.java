package fastloader.mixins.early.minecraft;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
}
