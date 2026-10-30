package fastloader.mixins.early.minecraft;

import java.util.Random;

import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/** Passive mobs spawned by population (entity construction + spawn checks). */
@Mixin(SpawnerAnimals.class)
public class MixinSpawnerAnimals {

    @Inject(method = "performWorldGenSpawning", at = @At("HEAD"), require = 0)
    private static void fastloader$head(World world, BiomeGenBase biome, int x, int z, int w, int h, Random random,
        CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.WORLDGEN_SPAWN);
    }

    @Inject(method = "performWorldGenSpawning", at = @At("RETURN"), require = 0)
    private static void fastloader$return(World world, BiomeGenBase biome, int x, int z, int w, int h, Random random,
        CallbackInfo ci) {
        ChunkProfiler.pop(Section.WORLDGEN_SPAWN);
    }
}
