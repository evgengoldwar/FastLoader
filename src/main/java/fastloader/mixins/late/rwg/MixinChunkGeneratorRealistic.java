package fastloader.mixins.late.rwg;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.optimize.TerrainPrefetcher;
import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/**
 * Splits RWG terrain and population into their real steps (height noise, per-biome mapgen, surface, decorators) and
 * hands over chunks whose terrain was generated on a background thread. RWG ships reobfuscated, so the IChunkProvider
 * methods are listed with their SRG names too.
 */
@Pseudo
@Mixin(targets = "rwg.world.ChunkGeneratorRealistic", remap = false)
public class MixinChunkGeneratorRealistic {

    // owner + name only: the descriptor differs between RWG versions
    private static final String GENERATE_MAP_GEN = "Lrwg/biomes/realistic/RealisticBiomeBase;generateMapGen";
    private static final String R_DECORATE = "Lrwg/biomes/realistic/RealisticBiomeBase;rDecorate";

    /** Main thread: use the terrain a background worker already generated for this chunk. */
    @Inject(method = { "provideChunk", "func_73154_d" }, at = @At("HEAD"), cancellable = true, require = 0)
    private void fastloader$prefetched(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        IChunkProvider self = (IChunkProvider) (Object) this;
        Chunk chunk = TerrainPrefetcher.take(self, x, z);
        if (chunk == null) return;
        // what provideChunk would still have done on this thread: heightmap / skylight and structure starts
        chunk.generateSkylightMap();
        self.recreateStructures(x, z);
        cir.setReturnValue(chunk);
    }

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

    @Inject(
        method = { "provideChunk", "func_73154_d" },
        at = @At(value = "INVOKE", target = GENERATE_MAP_GEN),
        require = 0)
    private void fastloader$mapGenBefore(CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.RWG_MAPGEN);
    }

    @Inject(
        method = { "provideChunk", "func_73154_d" },
        at = @At(value = "INVOKE", target = GENERATE_MAP_GEN, shift = At.Shift.AFTER),
        require = 0)
    private void fastloader$mapGenAfter(CallbackInfo ci) {
        ChunkProfiler.pop(Section.RWG_MAPGEN);
    }

    @Inject(method = { "populate", "func_73153_a" }, at = @At(value = "INVOKE", target = R_DECORATE), require = 0)
    private void fastloader$decorateBefore(CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.RWG_DECORATE);
    }

    @Inject(
        method = { "populate", "func_73153_a" },
        at = @At(value = "INVOKE", target = R_DECORATE, shift = At.Shift.AFTER),
        require = 0)
    private void fastloader$decorateAfter(CallbackInfo ci) {
        ChunkProfiler.pop(Section.RWG_DECORATE);
    }
}
