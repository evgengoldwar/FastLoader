package fastloader.mixins.late.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/**
 * GregTech oregen work unit: one per populated chunk, but GT only runs up to 5 queued containers per generate() call,
 * so the work of one chunk can happen while another chunk is populated. Covers the new (GTWorldgenerator) and the old
 * (GT_Worldgenerator) class names.
 */
@Pseudo
@Mixin(
    targets = { "gregtech.common.GTWorldgenerator$WorldGenContainer",
        "gregtech.common.GT_Worldgenerator$WorldGenContainer" },
    remap = false)
public class MixinGTWorldGenContainer {

    @Inject(method = "run", at = @At("HEAD"), require = 0)
    private void fastloader$runHead(CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.GT_CONTAINER);
    }

    @Inject(method = "run", at = @At("RETURN"), require = 0)
    private void fastloader$runReturn(CallbackInfo ci) {
        ChunkProfiler.pop(Section.GT_CONTAINER);
    }

    @Inject(method = { "generateVein", "worldGenFindVein" }, at = @At("HEAD"), require = 0)
    private void fastloader$veinHead(CallbackInfo ci) {
        ChunkProfiler.pushNested(Section.GT_VEIN_SEARCH);
    }

    @Inject(method = { "generateVein", "worldGenFindVein" }, at = @At("RETURN"), require = 0)
    private void fastloader$veinReturn(CallbackInfo ci) {
        ChunkProfiler.pop(Section.GT_VEIN_SEARCH);
    }
}
