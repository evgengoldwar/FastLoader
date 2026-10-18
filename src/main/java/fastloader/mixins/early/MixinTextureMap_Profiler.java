package fastloader.mixins.early;

import net.minecraft.client.renderer.texture.TextureMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.profiler.LoadProfiler;
import fastloader.profiler.ProfilerUtil;

/** Times icon registration + stitching + upload of each texture atlas (blocks, items). */
@Mixin(TextureMap.class)
public class MixinTextureMap_Profiler {

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void fastloader$loadAtlasHead(CallbackInfo ci) {
        LoadProfiler.begin(
            "atlas@" + System.identityHashCode(this),
            "Texture atlas " + ProfilerUtil.describeTextureMap(this));
    }

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void fastloader$loadAtlasReturn(CallbackInfo ci) {
        LoadProfiler.endNamed("atlas@" + System.identityHashCode(this));
    }
}
