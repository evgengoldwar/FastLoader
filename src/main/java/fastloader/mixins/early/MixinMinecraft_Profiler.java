package fastloader.mixins.early;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.core.FastLoaderCore;
import fastloader.profiler.LoadProfiler;

@Mixin(Minecraft.class)
public class MixinMinecraft_Profiler {

    @Inject(method = "startGame", at = @At("HEAD"))
    private void fastloader$startGameHead(CallbackInfo ci) {
        LoadProfiler.hook("Minecraft.startGame");
        LoadProfiler.onLoadingStarted();
        LoadProfiler.milestone("Minecraft.startGame (settings, window, OpenGL)");
    }

    @Inject(
        method = "startGame",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/client/FMLClientHandler;beginMinecraftLoading",
            remap = false),
        require = 0)
    private void fastloader$beforeBeginLoading(CallbackInfo ci) {
        LoadProfiler.milestone("Window ready -> FML: mod discovery, construction, preInit");
    }

    @Inject(
        method = "startGame",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/client/FMLClientHandler;beginMinecraftLoading",
            shift = At.Shift.AFTER,
            remap = false),
        require = 0)
    private void fastloader$afterBeginLoading(CallbackInfo ci) {
        LoadProfiler.milestone("preInit done -> vanilla setup: sounds, fonts, renderers, textures");
    }

    @Inject(
        method = "startGame",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/client/FMLClientHandler;finishMinecraftLoading",
            remap = false),
        require = 0)
    private void fastloader$beforeFinishLoading(CallbackInfo ci) {
        LoadProfiler.milestone("Vanilla setup done -> FML: init, postInit, loadComplete, resource reload");
    }

    @Inject(
        method = "startGame",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/client/FMLClientHandler;finishMinecraftLoading",
            shift = At.Shift.AFTER,
            remap = false),
        require = 0)
    private void fastloader$afterFinishLoading(CallbackInfo ci) {
        LoadProfiler.milestone("Mods loaded -> GUI setup, main menu, splash screen close");
    }

    @Inject(method = "startGame", at = @At("TAIL"))
    private void fastloader$startGameTail(CallbackInfo ci) {
        FastLoaderCore.onStartupFinished("Main menu ready");
    }

    @Inject(method = "refreshResources", at = @At("HEAD"))
    private void fastloader$refreshResourcesHead(CallbackInfo ci) {
        LoadProfiler.begin("refreshResources", "Minecraft.refreshResources");
    }

    @Inject(method = "refreshResources", at = @At("RETURN"))
    private void fastloader$refreshResourcesReturn(CallbackInfo ci) {
        LoadProfiler.endNamed("refreshResources");
    }
}
