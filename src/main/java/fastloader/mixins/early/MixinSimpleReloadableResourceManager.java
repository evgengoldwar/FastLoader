package fastloader.mixins.early;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.SimpleReloadableResourceManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import fastloader.profiler.LoadProfiler;
import fastloader.profiler.Section;

/**
 * Times each resource reload listener (textures, sounds, fonts, models of mods...), both on registration (vanilla
 * reloads a listener as soon as it is registered) and on every full resource reload.
 */
@Mixin(SimpleReloadableResourceManager.class)
public class MixinSimpleReloadableResourceManager {

    @Redirect(
        method = { "registerReloadListener", "notifyReloadListeners" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resources/IResourceManagerReloadListener;onResourceManagerReload(Lnet/minecraft/client/resources/IResourceManager;)V"),
        require = 0)
    private void fastloader$timeListener(IResourceManagerReloadListener listener, IResourceManager manager) {
        LoadProfiler.hook("Resource reload listeners");
        String name = listener.getClass()
            .getName();
        Section s = LoadProfiler.begin("Reload listener " + name, "listener", name);
        try {
            listener.onResourceManagerReload(manager);
        } finally {
            LoadProfiler.end(s);
        }
    }
}
