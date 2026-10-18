package fastloader.mixins.early;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cpw.mods.fml.common.ProgressManager;
import fastloader.profiler.LoadProfiler;

/**
 * FML reports every loading stage through ProgressManager bars (the ones drawn on the splash screen): mod
 * construction/preInit/init/postInit with one step per mod, resource reload with one step per listener, rendering
 * setup, and so on. Timing the bars and their steps gives a detailed load tree for free.
 */
@Mixin(value = ProgressManager.class, remap = false)
public class MixinProgressManager {

    @Inject(method = "push", at = @At("RETURN"))
    private static void fastloader$onPush(CallbackInfoReturnable<ProgressManager.ProgressBar> cir) {
        ProgressManager.ProgressBar bar = cir.getReturnValue();
        if (bar != null) LoadProfiler.onBarPush(bar, bar.getTitle(), bar.getSteps());
    }

    @Inject(method = "pop", at = @At("HEAD"))
    private static void fastloader$onPop(ProgressManager.ProgressBar bar, CallbackInfo ci) {
        LoadProfiler.onBarPop(bar);
    }
}
