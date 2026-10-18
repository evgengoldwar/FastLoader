package fastloader.mixins.early;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.ProgressManager;
import fastloader.profiler.LoadProfiler;

@Mixin(value = ProgressManager.ProgressBar.class, remap = false)
public class MixinProgressBar {

    @Inject(method = "step", at = @At("RETURN"))
    private void fastloader$onStep(CallbackInfo ci) {
        ProgressManager.ProgressBar bar = (ProgressManager.ProgressBar) (Object) this;
        LoadProfiler.onBarStep(bar, bar.getStep(), bar.getMessage());
    }
}
