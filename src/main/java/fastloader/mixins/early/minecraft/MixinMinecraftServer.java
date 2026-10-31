package fastloader.mixins.early.minecraft;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.optimize.FastWorldgenWrites;
import fastloader.optimize.TerrainPrefetcher;
import fastloader.profiler.ChunkProfiler;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void fastloader$tickStart(CallbackInfo ci) {
        // a populate that threw last tick never decremented the depth
        FastWorldgenWrites.populateDepth = 0;
        ChunkProfiler.onServerTickStart();
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void fastloader$tickEnd(CallbackInfo ci) {
        TerrainPrefetcher.tick();
        ChunkProfiler.onServerTickEnd();
    }
}
