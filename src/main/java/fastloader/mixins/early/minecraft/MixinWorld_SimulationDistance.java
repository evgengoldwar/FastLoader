package fastloader.mixins.early.minecraft;

import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.optimize.SimulationDistance;

/** Simulation distance: random ticks and entity updates far from players, see {@link SimulationDistance}. */
@Mixin(World.class)
public abstract class MixinWorld_SimulationDistance {

    @Shadow
    protected Set<ChunkCoordIntPair> activeChunkSet;

    @Inject(method = "setActivePlayerChunksAndCheckLight", at = @At("RETURN"), require = 0)
    private void fastloader$limitActiveChunks(CallbackInfo ci) {
        SimulationDistance.filterActiveChunks((World) (Object) this, activeChunkSet);
    }

    @Inject(method = "updateEntityWithOptionalForce", at = @At("HEAD"), cancellable = true, require = 0)
    private void fastloader$farEntity(Entity entity, boolean force, CallbackInfo ci) {
        if (force && SimulationDistance.skipEntityUpdate((World) (Object) this, entity)) ci.cancel();
    }
}
