package fastloader.mixins.late.rwg;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/** Splits RWG terrain and population into their real steps (height noise, per-biome mapgen, surface, decorators). */
@Pseudo
@Mixin(targets = "rwg.world.ChunkGeneratorRealistic", remap = false)
public class MixinChunkGeneratorRealistic {

    // owner + name only: the descriptor differs between RWG versions
    private static final String GENERATE_MAP_GEN = "Lrwg/biomes/realistic/RealisticBiomeBase;generateMapGen";
    private static final String R_DECORATE = "Lrwg/biomes/realistic/RealisticBiomeBase;rDecorate";

    @Inject(method = "generateTerrain", at = @At("HEAD"), require = 0)
    private void fastloader$terrainHead(CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.RWG_TERRAIN);
    }

    @Inject(method = "generateTerrain", at = @At("RETURN"), require = 0)
    private void fastloader$terrainReturn(CallbackInfo ci) {
        ChunkProfiler.pop(Section.RWG_TERRAIN);
    }

    @Inject(method = "replaceBlocksForBiome", at = @At("HEAD"), require = 0)
    private void fastloader$surfaceHead(CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.RWG_SURFACE);
    }

    @Inject(method = "replaceBlocksForBiome", at = @At("RETURN"), require = 0)
    private void fastloader$surfaceReturn(CallbackInfo ci) {
        ChunkProfiler.pop(Section.RWG_SURFACE);
    }

    @Inject(method = "provideChunk", at = @At(value = "INVOKE", target = GENERATE_MAP_GEN), require = 0)
    private void fastloader$mapGenBefore(CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.RWG_MAPGEN);
    }

    @Inject(
        method = "provideChunk",
        at = @At(value = "INVOKE", target = GENERATE_MAP_GEN, shift = At.Shift.AFTER),
        require = 0)
    private void fastloader$mapGenAfter(CallbackInfo ci) {
        ChunkProfiler.pop(Section.RWG_MAPGEN);
    }

    @Inject(method = "populate", at = @At(value = "INVOKE", target = R_DECORATE), require = 0)
    private void fastloader$decorateBefore(CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.RWG_DECORATE);
    }

    @Inject(method = "populate", at = @At(value = "INVOKE", target = R_DECORATE, shift = At.Shift.AFTER), require = 0)
    private void fastloader$decorateAfter(CallbackInfo ci) {
        ChunkProfiler.pop(Section.RWG_DECORATE);
    }
}
