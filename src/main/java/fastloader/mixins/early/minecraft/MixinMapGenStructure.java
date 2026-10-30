package fastloader.mixins.early.minecraft;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.MapGenStructure;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Keys;
import fastloader.profiler.Section;

/** Structure pieces placed during population (villages, strongholds, mineshafts, temples, modded structures). */
@Mixin(MapGenStructure.class)
public class MixinMapGenStructure {

    @Inject(method = "generateStructuresInChunk", at = @At("HEAD"), require = 0)
    private void fastloader$head(World world, Random random, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        if (ChunkProfiler.enabled) ChunkProfiler.pushNested(Section.STRUCTURE, Keys.structure(this));
    }

    @Inject(method = "generateStructuresInChunk", at = @At("RETURN"), require = 0)
    private void fastloader$return(World world, Random random, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pop(Section.STRUCTURE);
    }
}
