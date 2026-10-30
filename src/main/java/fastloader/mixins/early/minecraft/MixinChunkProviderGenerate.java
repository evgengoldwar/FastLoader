package fastloader.mixins.early.minecraft;

import net.minecraft.block.Block;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.ChunkProviderGenerate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/** Overworld terrain internals: noise density field and biome top/filler blocks. */
@Mixin(ChunkProviderGenerate.class)
public class MixinChunkProviderGenerate {

    @Inject(method = "func_147424_a", at = @At("HEAD"), require = 0)
    private void fastloader$noiseHead(int x, int z, Block[] blocks, CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.TERRAIN_NOISE);
    }

    @Inject(method = "func_147424_a", at = @At("RETURN"), require = 0)
    private void fastloader$noiseReturn(int x, int z, Block[] blocks, CallbackInfo ci) {
        ChunkProfiler.pop(Section.TERRAIN_NOISE);
    }

    @Inject(method = "replaceBlocksForBiome", at = @At("HEAD"), require = 0)
    private void fastloader$biomeBlocksHead(int x, int z, Block[] blocks, byte[] meta, BiomeGenBase[] biomes,
        CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.BIOME_BLOCKS);
    }

    @Inject(method = "replaceBlocksForBiome", at = @At("RETURN"), require = 0)
    private void fastloader$biomeBlocksReturn(int x, int z, Block[] blocks, byte[] meta, BiomeGenBase[] biomes,
        CallbackInfo ci) {
        ChunkProfiler.pop(Section.BIOME_BLOCKS);
    }
}
