package fastloader.mixins.late.gregtech;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/**
 * Per ore mix / stone / small ore timing. The name comes from GTWorldgen.mWorldGenName (read once by reflection, the
 * class is not on our compile classpath).
 */
@Pseudo
@Mixin(
    targets = { "gregtech.common.WorldgenGTOreLayer", "gregtech.common.GT_Worldgen_GT_Ore_Layer",
        "gregtech.common.WorldgenStone", "gregtech.common.GT_Worldgen_Stone",
        "gregtech.common.WorldgenGTOreSmallPieces", "gregtech.common.GT_Worldgen_GT_Ore_SmallPieces" },
    remap = false)
public class MixinGTWorldgenNamed {

    @Unique
    private String fastloader$key;

    @Unique
    private Section fastloader$section;

    @Unique
    private Section fastloader$section() {
        Section s = fastloader$section;
        if (s == null) {
            String cls = getClass().getName();
            if (cls.contains("Stone")) s = Section.GT_STONE;
            else if (cls.contains("Small")) s = Section.GT_SMALL_ORE;
            else s = Section.GT_VEIN;
            fastloader$section = s;
        }
        return s;
    }

    @Unique
    private String fastloader$key() {
        String k = fastloader$key;
        if (k == null) {
            String name = "?";
            try {
                Field f = getClass().getField("mWorldGenName");
                name = String.valueOf(f.get(this));
            } catch (Throwable ignored) {}
            k = fastloader$section().label + " " + name;
            fastloader$key = k;
        }
        return k;
    }

    // veins (placed chunk by chunk)
    @Inject(method = "executeWorldgenChunkified", at = @At("HEAD"), require = 0)
    private void fastloader$veinHead(CallbackInfoReturnable<Integer> cir) {
        if (ChunkProfiler.enabled) ChunkProfiler.pushNested(Section.GT_VEIN, fastloader$key());
    }

    @Inject(method = "executeWorldgenChunkified", at = @At("RETURN"), require = 0)
    private void fastloader$veinReturn(CallbackInfoReturnable<Integer> cir) {
        ChunkProfiler.pop(Section.GT_VEIN);
    }

    // stones and small ores
    @Inject(method = "executeWorldgen", at = @At("HEAD"), require = 0)
    private void fastloader$genHead(CallbackInfoReturnable<Boolean> cir) {
        if (ChunkProfiler.enabled) ChunkProfiler.pushNested(fastloader$section(), fastloader$key());
    }

    @Inject(method = "executeWorldgen", at = @At("RETURN"), require = 0)
    private void fastloader$genReturn(CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pop(fastloader$section());
    }
}
