package fastloader.mixins.early.minecraft;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Keys;
import fastloader.profiler.Section;

/**
 * The heart of the chunk pipeline: loading (sync / async / from disk), generation, population, unloading and saving.
 * Methods added by Forge ({@code loadChunk(II Runnable)}, {@code originalLoadChunk}) are not obfuscated.
 */
@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServer {

    @Shadow
    public IChunkProvider currentChunkProvider;

    @Shadow
    public WorldServer worldObj;

    // ------------------------------------------------------------------ loadChunk (Forge entry point)

    @Inject(
        method = "loadChunk(IILjava/lang/Runnable;)Lnet/minecraft/world/chunk/Chunk;",
        at = @At("HEAD"),
        remap = false,
        require = 0)
    private void fastloader$loadHead(int x, int z, Runnable runnable, CallbackInfoReturnable<Chunk> cir) {
        ChunkProfiler.pushChunk(Section.LOAD_CHUNK, worldObj.provider.dimensionId, x, z);
    }

    @Inject(
        method = "loadChunk(IILjava/lang/Runnable;)Lnet/minecraft/world/chunk/Chunk;",
        at = @At("RETURN"),
        remap = false,
        require = 0)
    private void fastloader$loadReturn(int x, int z, Runnable runnable, CallbackInfoReturnable<Chunk> cir) {
        ChunkProfiler.pop(Section.LOAD_CHUNK);
    }

    // ------------------------------------------------------------------ originalLoadChunk (sync load / generate)

    @Inject(method = "originalLoadChunk", at = @At("HEAD"), remap = false, require = 0)
    private void fastloader$originalHead(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        ChunkProfiler.pushChunk(Section.LOAD_ORIGINAL, worldObj.provider.dimensionId, x, z);
    }

    @Inject(method = "originalLoadChunk", at = @At("RETURN"), remap = false, require = 0)
    private void fastloader$originalReturn(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        ChunkProfiler.pop(Section.LOAD_ORIGINAL);
    }

    @Inject(
        method = "originalLoadChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/IChunkProvider;provideChunk(II)Lnet/minecraft/world/chunk/Chunk;",
            remap = true),
        remap = false,
        require = 0)
    private void fastloader$terrainBefore(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        if (ChunkProfiler.enabled) ChunkProfiler.push(Section.TERRAIN, Keys.provide(currentChunkProvider));
    }

    @Inject(
        method = "originalLoadChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/IChunkProvider;provideChunk(II)Lnet/minecraft/world/chunk/Chunk;",
            shift = At.Shift.AFTER,
            remap = true),
        remap = false,
        require = 0)
    private void fastloader$terrainAfter(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        ChunkProfiler.onGenerated(worldObj, x, z);
        ChunkProfiler.pop(Section.TERRAIN);
    }

    // ------------------------------------------------------------------ disk

    @Inject(method = "safeLoadChunk", at = @At("HEAD"), require = 0)
    private void fastloader$diskHead(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        ChunkProfiler.push(Section.DISK_READ);
    }

    @Inject(method = "safeLoadChunk", at = @At("RETURN"), require = 0)
    private void fastloader$diskReturn(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        if (cir.getReturnValue() != null) ChunkProfiler.onDiskLoad(worldObj.provider.dimensionId);
        ChunkProfiler.pop(Section.DISK_READ);
    }

    @Inject(method = "safeSaveChunk", at = @At("HEAD"), require = 0)
    private void fastloader$saveHead(Chunk chunk, CallbackInfo ci) {
        ChunkProfiler.push(Section.SAVE_CHUNK);
    }

    @Inject(method = "safeSaveChunk", at = @At("RETURN"), require = 0)
    private void fastloader$saveReturn(Chunk chunk, CallbackInfo ci) {
        ChunkProfiler.onChunkSaved();
        ChunkProfiler.pop(Section.SAVE_CHUNK);
    }

    @Inject(method = "safeSaveExtraChunkData", at = @At("HEAD"), require = 0)
    private void fastloader$saveExtraHead(Chunk chunk, CallbackInfo ci) {
        ChunkProfiler.push(Section.SAVE_EXTRA);
    }

    @Inject(method = "safeSaveExtraChunkData", at = @At("RETURN"), require = 0)
    private void fastloader$saveExtraReturn(Chunk chunk, CallbackInfo ci) {
        ChunkProfiler.pop(Section.SAVE_EXTRA);
    }

    @Inject(method = "unloadQueuedChunks", at = @At("HEAD"), require = 0)
    private void fastloader$unloadHead(CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.push(Section.UNLOAD_QUEUE);
    }

    @Inject(method = "unloadQueuedChunks", at = @At("RETURN"), require = 0)
    private void fastloader$unloadReturn(CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pop(Section.UNLOAD_QUEUE);
    }

    @Inject(method = "saveChunks", at = @At("HEAD"), require = 0)
    private void fastloader$saveAllHead(CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.push(Section.SAVE_ALL);
    }

    @Inject(method = "saveChunks", at = @At("RETURN"), require = 0)
    private void fastloader$saveAllReturn(CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pop(Section.SAVE_ALL);
    }

    // ------------------------------------------------------------------ population

    @Inject(method = "populate", at = @At("HEAD"), require = 0)
    private void fastloader$populateHead(IChunkProvider provider, int x, int z, CallbackInfo ci) {
        ChunkProfiler.pushChunk(Section.POPULATE, worldObj.provider.dimensionId, x, z);
    }

    @Inject(method = "populate", at = @At("RETURN"), require = 0)
    private void fastloader$populateReturn(IChunkProvider provider, int x, int z, CallbackInfo ci) {
        ChunkProfiler.pop(Section.POPULATE);
    }

    @Inject(
        method = "populate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/IChunkProvider;populate(Lnet/minecraft/world/chunk/IChunkProvider;II)V"),
        require = 0)
    private void fastloader$providerPopulateBefore(IChunkProvider provider, int x, int z, CallbackInfo ci) {
        if (ChunkProfiler.enabled) ChunkProfiler.push(Section.PROVIDER_POPULATE, Keys.populate(currentChunkProvider));
    }

    @Inject(
        method = "populate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/IChunkProvider;populate(Lnet/minecraft/world/chunk/IChunkProvider;II)V",
            shift = At.Shift.AFTER),
        require = 0)
    private void fastloader$providerPopulateAfter(IChunkProvider provider, int x, int z, CallbackInfo ci) {
        ChunkProfiler.pop(Section.PROVIDER_POPULATE);
    }

    @Inject(
        method = "populate",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/registry/GameRegistry;generateWorld(IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V",
            remap = false),
        require = 0)
    private void fastloader$generateWorldBefore(IChunkProvider provider, int x, int z, CallbackInfo ci) {
        ChunkProfiler.push(Section.WORLDGEN_ALL);
    }

    @Inject(
        method = "populate",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/registry/GameRegistry;generateWorld(IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V",
            shift = At.Shift.AFTER,
            remap = false),
        require = 0)
    private void fastloader$generateWorldAfter(IChunkProvider provider, int x, int z, CallbackInfo ci) {
        ChunkProfiler.pop(Section.WORLDGEN_ALL);
    }
}
