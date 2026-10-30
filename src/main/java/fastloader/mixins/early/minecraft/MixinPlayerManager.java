package fastloader.mixins.early.minecraft;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/** Chunk loading requested by players (view distance), incl. queueing async loads. */
@Mixin(PlayerManager.class)
public class MixinPlayerManager {

    @Inject(method = "updatePlayerInstances", at = @At("HEAD"), require = 0)
    private void fastloader$instancesHead(CallbackInfo ci) {
        ChunkProfiler.push(Section.PLAYER_INSTANCES);
    }

    @Inject(method = "updatePlayerInstances", at = @At("RETURN"), require = 0)
    private void fastloader$instancesReturn(CallbackInfo ci) {
        ChunkProfiler.pop(Section.PLAYER_INSTANCES);
    }

    @Inject(method = "updatePlayerPertinentChunks", at = @At("HEAD"), require = 0)
    private void fastloader$moveHead(EntityPlayerMP player, CallbackInfo ci) {
        ChunkProfiler.push(Section.PLAYER_MOVE);
    }

    @Inject(method = "updatePlayerPertinentChunks", at = @At("RETURN"), require = 0)
    private void fastloader$moveReturn(EntityPlayerMP player, CallbackInfo ci) {
        ChunkProfiler.pop(Section.PLAYER_MOVE);
    }
}
