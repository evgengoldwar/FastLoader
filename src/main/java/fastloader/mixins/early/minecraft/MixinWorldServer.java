package fastloader.mixins.early.minecraft;

import net.minecraft.block.Block;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/** World level timings, only used to explain slow ticks (not part of the chunk call tree). */
@Mixin(WorldServer.class)
public class MixinWorldServer {

    @Unique
    private long fastloader$tickStart;
    @Unique
    private long fastloader$entitiesStart;
    @Unique
    private long fastloader$blocksStart;

    @Unique
    private int fastloader$dim() {
        return ((WorldServer) (Object) this).provider.dimensionId;
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void fastloader$tickHead(CallbackInfo ci) {
        fastloader$tickStart = System.nanoTime();
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void fastloader$tickReturn(CallbackInfo ci) {
        ChunkProfiler.addTickTime(fastloader$dim(), 0, System.nanoTime() - fastloader$tickStart);
    }

    @Inject(method = "updateEntities", at = @At("HEAD"), require = 0)
    private void fastloader$entitiesHead(CallbackInfo ci) {
        fastloader$entitiesStart = System.nanoTime();
    }

    @Inject(method = "updateEntities", at = @At("RETURN"), require = 0)
    private void fastloader$entitiesReturn(CallbackInfo ci) {
        ChunkProfiler.addTickTime(fastloader$dim(), 1, System.nanoTime() - fastloader$entitiesStart);
    }

    @Inject(method = "func_147456_g", at = @At("HEAD"), require = 0)
    private void fastloader$blocksHead(CallbackInfo ci) {
        fastloader$blocksStart = System.nanoTime();
    }

    @Inject(method = "func_147456_g", at = @At("RETURN"), require = 0)
    private void fastloader$blocksReturn(CallbackInfo ci) {
        ChunkProfiler.addTickTime(fastloader$dim(), 2, System.nanoTime() - fastloader$blocksStart);
    }

    /** Block ticks scheduled by worldgen (falling gravel/sand, flowing liquids...), counted per section. */
    @Inject(method = "scheduleBlockUpdateWithPriority", at = @At("HEAD"), require = 0)
    private void fastloader$scheduledTick(int x, int y, int z, Block block, int delay, int priority, CallbackInfo ci) {
        ChunkProfiler.count(Section.SCHEDULED_TICK);
    }
}
