package fastloader.mixins.early.minecraft;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Keys;
import fastloader.profiler.Section;

/** Trees, flowers, grass, vanilla ores... per biome (also for modded biomes that use a BiomeDecorator). */
@Mixin(BiomeDecorator.class)
public class MixinBiomeDecorator {

    @Inject(method = "decorateChunk", at = @At("HEAD"), require = 0)
    private void fastloader$decorateHead(World world, Random random, BiomeGenBase biome, int x, int z,
        CallbackInfo ci) {
        if (ChunkProfiler.enabled) ChunkProfiler.pushNested(Section.DECORATE, Keys.biomeDecorate(biome));
    }

    @Inject(method = "decorateChunk", at = @At("RETURN"), require = 0)
    private void fastloader$decorateReturn(World world, Random random, BiomeGenBase biome, int x, int z,
        CallbackInfo ci) {
        ChunkProfiler.pop(Section.DECORATE);
    }

    @Inject(method = "generateOres", at = @At("HEAD"), require = 0)
    private void fastloader$oresHead(CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.VANILLA_ORES);
    }

    @Inject(method = "generateOres", at = @At("RETURN"), require = 0)
    private void fastloader$oresReturn(CallbackInfo ci) {
        ChunkProfiler.pop(Section.VANILLA_ORES);
    }
}
