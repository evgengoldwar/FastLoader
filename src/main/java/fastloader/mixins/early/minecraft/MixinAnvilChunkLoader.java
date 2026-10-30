package fastloader.mixins.early.minecraft;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/** Region file IO and NBT (de)serialization. Runs on the server thread, the ChunkIO threads and the File IO thread. */
@Mixin(AnvilChunkLoader.class)
public class MixinAnvilChunkLoader {

    @Inject(method = "loadChunk__Async", at = @At("HEAD"), remap = false, require = 0)
    private void fastloader$asyncReadHead(World world, int x, int z, CallbackInfoReturnable<Object[]> cir) {
        ChunkProfiler.pushChunk(Section.ASYNC_READ, world.provider.dimensionId, x, z);
    }

    @Inject(method = "loadChunk__Async", at = @At("RETURN"), remap = false, require = 0)
    private void fastloader$asyncReadReturn(World world, int x, int z, CallbackInfoReturnable<Object[]> cir) {
        ChunkProfiler.pop(Section.ASYNC_READ);
    }

    @Inject(method = "readChunkFromNBT", at = @At("HEAD"), require = 0)
    private void fastloader$readNbtHead(World world, NBTTagCompound tag, CallbackInfoReturnable<Chunk> cir) {
        ChunkProfiler.push(Section.NBT_TO_CHUNK);
    }

    @Inject(method = "readChunkFromNBT", at = @At("RETURN"), require = 0)
    private void fastloader$readNbtReturn(World world, NBTTagCompound tag, CallbackInfoReturnable<Chunk> cir) {
        ChunkProfiler.pop(Section.NBT_TO_CHUNK);
    }

    @Inject(method = "loadEntities", at = @At("HEAD"), remap = false, require = 0)
    private void fastloader$entitiesHead(World world, NBTTagCompound tag, Chunk chunk, CallbackInfo ci) {
        ChunkProfiler.onLoadEntities(world.provider.dimensionId, chunk.xPosition, chunk.zPosition);
        ChunkProfiler.push(Section.LOAD_ENTITIES);
    }

    @Inject(method = "loadEntities", at = @At("RETURN"), remap = false, require = 0)
    private void fastloader$entitiesReturn(World world, NBTTagCompound tag, Chunk chunk, CallbackInfo ci) {
        ChunkProfiler.pop(Section.LOAD_ENTITIES);
    }

    @Inject(method = "saveChunk", at = @At("HEAD"), require = 0)
    private void fastloader$saveHead(World world, Chunk chunk, CallbackInfo ci) {
        ChunkProfiler.push(Section.CHUNK_TO_NBT);
    }

    @Inject(method = "saveChunk", at = @At("RETURN"), require = 0)
    private void fastloader$saveReturn(World world, Chunk chunk, CallbackInfo ci) {
        ChunkProfiler.pop(Section.CHUNK_TO_NBT);
    }

    @Inject(method = "writeNextIO", at = @At("HEAD"), require = 0)
    private void fastloader$ioHead(CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.push(Section.IO_WRITE);
    }

    @Inject(method = "writeNextIO", at = @At("RETURN"), require = 0)
    private void fastloader$ioReturn(CallbackInfoReturnable<Boolean> cir) {
        ChunkProfiler.pop(Section.IO_WRITE);
    }
}
