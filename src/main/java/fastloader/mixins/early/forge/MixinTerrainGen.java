package fastloader.mixins.early.forge;

import net.minecraft.world.gen.MapGenBase;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.terraingen.TerrainGen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.optimize.TerrainPrefetcher;

/**
 * While FastLoader builds the private generators of its terrain workers, InitMapGenEvent is not fired: mods must not
 * see (and possibly keep references to) these extra instances. The prefetcher refuses to run if this leaves a worker
 * with a different terrain map generator than the main generator.
 */
@Mixin(value = TerrainGen.class, remap = false)
public class MixinTerrainGen {

    @Inject(method = "getModdedMapGen", at = @At("HEAD"), cancellable = true, require = 0)
    private static void fastloader$noEventForWorkers(MapGenBase original, InitMapGenEvent.EventType type,
        CallbackInfoReturnable<MapGenBase> cir) {
        if (TerrainPrefetcher.suppressMapGenEvents) cir.setReturnValue(original);
    }
}
