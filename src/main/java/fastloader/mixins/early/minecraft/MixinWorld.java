package fastloader.mixins.early.minecraft;

import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/** Light propagation triggered by worldgen block placement (only timed inside chunk work). */
@Mixin(World.class)
public class MixinWorld {

    @Inject(method = "updateLightByType", at = @At("HEAD"), require = 0)
    private void fastloader$lightHead(EnumSkyBlock type, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (ChunkProfiler.profileLighting) ChunkProfiler.pushNested(Section.LIGHT_UPDATE);
    }

    @Inject(method = "updateLightByType", at = @At("RETURN"), require = 0)
    private void fastloader$lightReturn(EnumSkyBlock type, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pop(Section.LIGHT_UPDATE);
    }
}
