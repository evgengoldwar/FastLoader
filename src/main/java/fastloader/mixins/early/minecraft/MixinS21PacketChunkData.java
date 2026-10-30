package fastloader.mixins.early.minecraft;

import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/** Copying chunk sections into packet buffers for players (server thread; compression happens on netty threads). */
@Mixin(S21PacketChunkData.class)
public class MixinS21PacketChunkData {

    @Inject(method = "func_149269_a", at = @At("HEAD"), require = 0)
    private static void fastloader$head(Chunk chunk, boolean full, int mask, CallbackInfoReturnable<?> cir) {
        ChunkProfiler.onPacketBuilt();
        ChunkProfiler.push(Section.PACKET_BUILD);
    }

    @Inject(method = "func_149269_a", at = @At("RETURN"), require = 0)
    private static void fastloader$return(Chunk chunk, boolean full, int mask, CallbackInfoReturnable<?> cir) {
        ChunkProfiler.pop(Section.PACKET_BUILD);
    }
}
