package fastloader.mixins.early.minecraft;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.MapGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Keys;
import fastloader.profiler.Section;

/** Caves, ravines and the terrain part of every structure (vanilla and modded MapGenBase subclasses). */
@Mixin(MapGenBase.class)
public class MixinMapGenBase {

    @Inject(method = "func_151539_a", at = @At("HEAD"), require = 0)
    private void fastloader$head(IChunkProvider provider, World world, int x, int z, Block[] blocks, CallbackInfo ci) {
        if (ChunkProfiler.enabled) ChunkProfiler.pushNested(Section.MAPGEN, Keys.mapGen(this));
    }

    @Inject(method = "func_151539_a", at = @At("RETURN"), require = 0)
    private void fastloader$return(IChunkProvider provider, World world, int x, int z, Block[] blocks,
        CallbackInfo ci) {
        ChunkProfiler.pop(Section.MAPGEN);
    }
}
