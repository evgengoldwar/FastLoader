package fastloader.mixins.early;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.LoaderState;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLEvent;
import fastloader.profiler.LoadProfiler;
import fastloader.profiler.Section;

/** Times every FML lifecycle state, and every mod's handler for it. */
@Mixin(value = LoadController.class, remap = false)
public class MixinLoadController {

    private Section fastloader$state;
    private Section fastloader$modEvent;

    @Inject(method = "distributeStateMessage(Lcpw/mods/fml/common/LoaderState;[Ljava/lang/Object;)V", at = @At("HEAD"))
    private void fastloader$stateHead(LoaderState state, Object[] eventData, CallbackInfo ci) {
        LoadProfiler.onLoadingStarted();
        LoadProfiler.hook("FML LoadController states");
        LoadProfiler.end(fastloader$state);
        fastloader$state = LoadProfiler.begin("FML state " + state);
    }

    @Inject(method = "distributeStateMessage(Lcpw/mods/fml/common/LoaderState;[Ljava/lang/Object;)V", at = @At("RETURN"))
    private void fastloader$stateReturn(LoaderState state, Object[] eventData, CallbackInfo ci) {
        LoadProfiler.end(fastloader$state);
        fastloader$state = null;
    }

    @Inject(method = "sendEventToModContainer", at = @At("HEAD"))
    private void fastloader$modEventHead(FMLEvent event, ModContainer mc, CallbackInfo ci) {
        LoadProfiler.hook("FML per-mod event handlers");
        fastloader$modEvent = LoadProfiler.begin(
            mc.getName(),
            "mod",
            event.getClass()
                .getSimpleName());
    }

    @Inject(method = "sendEventToModContainer", at = @At("RETURN"))
    private void fastloader$modEventReturn(FMLEvent event, ModContainer mc, CallbackInfo ci) {
        LoadProfiler.end(fastloader$modEvent);
        fastloader$modEvent = null;
    }
}
