package fastloader.mixins.early.minecraft;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenMinable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

@Mixin(WorldGenMinable.class)
public class MixinWorldGenMinable {

    @Inject(method = "generate", at = @At("HEAD"), require = 0)
    private void fastloader$head(World world, Random random, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pushNested(Section.MINABLE);
    }

    @Inject(method = "generate", at = @At("RETURN"), require = 0)
    private void fastloader$return(World world, Random random, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pop(Section.MINABLE);
    }
}
