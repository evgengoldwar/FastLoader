package fastloader.mixins.early.minecraft;

import net.minecraft.block.Block;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/**
 * Light propagation and block writes triggered by worldgen. Both are only counted inside chunk work; light updates are
 * additionally timed for 1 of every 32 calls to estimate their cost without distorting it.
 */
@Mixin(World.class)
public class MixinWorld {

    /** Light updates don't nest, so one flag per world is enough (worlds are only touched by their own thread). */
    @Unique
    private boolean fastloader$lightSampled;

    @Inject(method = "updateLightByType", at = @At("HEAD"), require = 0)
    private void fastloader$lightHead(EnumSkyBlock type, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (ChunkProfiler.profileLighting && ChunkProfiler.lightUpdate()) fastloader$lightSampled = true;
    }

    @Inject(method = "updateLightByType", at = @At("RETURN"), require = 0)
    private void fastloader$lightReturn(EnumSkyBlock type, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (fastloader$lightSampled) {
            fastloader$lightSampled = false;
            ChunkProfiler.pop(Section.LIGHT_SAMPLE);
        }
    }

    @Inject(method = "setBlock(IIILnet/minecraft/block/Block;II)Z", at = @At("HEAD"), require = 0)
    private void fastloader$setBlock(int x, int y, int z, Block block, int meta, int flags,
        CallbackInfoReturnable<Boolean> cir) {
        if (ChunkProfiler.countSetBlock) ChunkProfiler.count(Section.SET_BLOCK);
    }
}
