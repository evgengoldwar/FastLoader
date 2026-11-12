package fastloader.mixins.early.common;

import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.newgen.NewGen;
import fastloader.newgen.nether.NetherBiomeSource;
import fastloader.newgen.nether.NetherChunkProviderNG;

/** Swaps the Nether biome source and chunk generator for the modern ones in "New Generation" worlds. */
@Mixin(WorldProviderHell.class)
public abstract class MixinWorldProviderHell extends WorldProvider {

    @Inject(method = "registerWorldChunkManager", at = @At("TAIL"))
    private void newgen$biomeSource(CallbackInfo ci) {
        if (NewGen.useNewNether(this.terrainType)) {
            this.worldChunkMgr = new NetherBiomeSource(this.worldObj.getSeed());
        }
    }

    @Inject(method = "createChunkGenerator", at = @At("HEAD"), cancellable = true)
    private void newgen$generator(CallbackInfoReturnable<IChunkProvider> cir) {
        if (NewGen.useNewNether(this.terrainType)) {
            cir.setReturnValue(new NetherChunkProviderNG(this.worldObj, this.worldObj.getSeed()));
        }
    }
}
