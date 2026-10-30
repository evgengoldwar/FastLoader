package fastloader.mixins.early.minecraft;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/** GenLayer biome evaluation used by terrain generation. */
@Mixin(WorldChunkManager.class)
public class MixinWorldChunkManager {

    @Inject(method = "getBiomesForGeneration", at = @At("HEAD"), require = 0)
    private void fastloader$genHead(BiomeGenBase[] biomes, int x, int z, int w, int h,
        CallbackInfoReturnable<BiomeGenBase[]> cir) {
        ChunkProfiler.pushNested(Section.BIOME_GEN);
    }

    @Inject(method = "getBiomesForGeneration", at = @At("RETURN"), require = 0)
    private void fastloader$genReturn(BiomeGenBase[] biomes, int x, int z, int w, int h,
        CallbackInfoReturnable<BiomeGenBase[]> cir) {
        ChunkProfiler.pop(Section.BIOME_GEN);
    }

    @Inject(method = "loadBlockGeneratorData", at = @At("HEAD"), require = 0)
    private void fastloader$blockHead(BiomeGenBase[] biomes, int x, int z, int w, int h,
        CallbackInfoReturnable<BiomeGenBase[]> cir) {
        ChunkProfiler.pushNested(Section.BIOME_GEN);
    }

    @Inject(method = "loadBlockGeneratorData", at = @At("RETURN"), require = 0)
    private void fastloader$blockReturn(BiomeGenBase[] biomes, int x, int z, int w, int h,
        CallbackInfoReturnable<BiomeGenBase[]> cir) {
        ChunkProfiler.pop(Section.BIOME_GEN);
    }
}
