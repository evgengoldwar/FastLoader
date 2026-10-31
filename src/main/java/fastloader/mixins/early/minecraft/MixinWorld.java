package fastloader.mixins.early.minecraft;

import net.minecraft.block.Block;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/**
 * Light propagation and block writes triggered by worldgen. Both are only counted inside chunk work; 1 of every 32
 * light
 * updates and 1 of every 64 setBlock calls (with its internals) are timed to estimate their cost without distorting it.
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
    private void fastloader$setBlockHead(int x, int y, int z, Block block, int meta, int flags,
        CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.setBlockEnter();
    }

    @Inject(method = "setBlock(IIILnet/minecraft/block/Block;II)Z", at = @At("RETURN"), require = 0)
    private void fastloader$setBlockReturn(int x, int y, int z, Block block, int meta, int flags,
        CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.setBlockExit();
    }

    @Inject(method = "func_147451_t", at = @At("HEAD"), require = 0)
    private void fastloader$lightCheckHead(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pushInSample(Section.SB_LIGHT);
    }

    @Inject(method = "func_147451_t", at = @At("RETURN"), require = 0)
    private void fastloader$lightCheckReturn(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pop(Section.SB_LIGHT);
    }

    @Inject(method = "markAndNotifyBlock", at = @At("HEAD"), remap = false, require = 0)
    private void fastloader$notifyHead(int x, int y, int z, Chunk chunk, Block oldBlock, Block newBlock, int flags,
        CallbackInfo ci) {
        ChunkProfiler.pushInSample(Section.SB_NOTIFY);
    }

    @Inject(method = "markAndNotifyBlock", at = @At("RETURN"), remap = false, require = 0)
    private void fastloader$notifyReturn(int x, int y, int z, Chunk chunk, Block oldBlock, Block newBlock, int flags,
        CallbackInfo ci) {
        ChunkProfiler.pop(Section.SB_NOTIFY);
    }
}
