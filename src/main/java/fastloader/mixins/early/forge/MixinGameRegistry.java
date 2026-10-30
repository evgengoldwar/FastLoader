package fastloader.mixins.early.forge;

import java.util.Iterator;
import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.registry.GameRegistry;
import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Keys;
import fastloader.profiler.Section;

/**
 * Times every registered {@link IWorldGenerator} (GregTech ores, Thaumcraft, Bartworks, Twilight Forest, Witchery...)
 * separately. The generator is read from the loop local; if another mod changed the method the capture fails softly
 * and only the total (GameRegistry.generateWorld) is reported.
 */
@Mixin(value = GameRegistry.class, remap = false)
public class MixinGameRegistry {

    @Inject(
        method = "generateWorld",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/IWorldGenerator;generate(Ljava/util/Random;IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V"),
        locals = LocalCapture.CAPTURE_FAILSOFT,
        require = 0)
    private static void fastloader$generatorBefore(int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
        IChunkProvider chunkProvider, CallbackInfo ci, long worldSeed, Random fmlRandom, long xSeed, long zSeed,
        long chunkSeed, Iterator<?> iterator, IWorldGenerator generator) {
        if (ChunkProfiler.enabled) ChunkProfiler.push(Section.WORLDGEN, Keys.worldGenerator(generator));
    }

    @Inject(
        method = "generateWorld",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/IWorldGenerator;generate(Ljava/util/Random;IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V",
            shift = At.Shift.AFTER),
        require = 0)
    private static void fastloader$generatorAfter(int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
        IChunkProvider chunkProvider, CallbackInfo ci) {
        ChunkProfiler.pop(Section.WORLDGEN);
    }
}
