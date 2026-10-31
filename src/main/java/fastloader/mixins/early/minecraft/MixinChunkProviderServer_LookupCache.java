package fastloader.mixins.early.minecraft;

import net.minecraft.util.LongHashMap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import fastloader.optimize.ChunkLookupCache;
import fastloader.profiler.ChunkProfiler;

/** Puts {@link ChunkLookupCache} in front of the loaded chunk map lookup in provideChunk. */
@Mixin(ChunkProviderServer.class)
public class MixinChunkProviderServer_LookupCache {

    @Unique
    private final Chunk[] fastloader$cache = new Chunk[4];

    @Redirect(
        method = "provideChunk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/LongHashMap;getValueByKey(J)Ljava/lang/Object;"),
        require = 0)
    private Object fastloader$cachedLookup(LongHashMap map, long key) {
        if (!ChunkLookupCache.enabled || Thread.currentThread() != ChunkProfiler.serverThread()) {
            return map.getValueByKey(key);
        }
        Chunk cached = ChunkLookupCache.get(fastloader$cache, key);
        if (cached != null) return cached;
        Object value = map.getValueByKey(key);
        if (value instanceof Chunk) fastloader$cache[ChunkLookupCache.slot(key)] = (Chunk) value;
        return value;
    }
}
