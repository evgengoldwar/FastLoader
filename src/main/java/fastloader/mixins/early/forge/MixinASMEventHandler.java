package fastloader.mixins.early.forge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.ASMEventHandler;
import cpw.mods.fml.common.eventhandler.Event;
import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.Section;

/**
 * Times every Forge/FML event listener that runs inside chunk work (ChunkEvent.Load, ChunkDataEvent,
 * PopulateChunkEvent,
 * DecorateBiomeEvent, OreGenEvent, GT VeinGenerateEvent, ...), named by owning mod, handler class and method.
 */
@Mixin(value = ASMEventHandler.class, remap = false)
public class MixinASMEventHandler {

    @Shadow
    private ModContainer owner;

    @Shadow
    private String readable;

    @Unique
    private String fastloader$key;

    @Inject(method = "invoke", at = @At("HEAD"), require = 0)
    private void fastloader$invokeHead(Event event, CallbackInfo ci) {
        if (ChunkProfiler.profileEvents && ChunkProfiler.enabled) {
            String key = fastloader$key;
            if (key == null) {
                key = fastloader$buildKey();
                fastloader$key = key;
            }
            ChunkProfiler.pushNested(Section.EVENT, key);
        }
    }

    @Inject(method = "invoke", at = @At("RETURN"), require = 0)
    private void fastloader$invokeReturn(Event event, CallbackInfo ci) {
        ChunkProfiler.pop(Section.EVENT);
    }

    @Unique
    private String fastloader$buildKey() {
        // readable = "ASM: " + target + " " + methodName + descriptor
        String r = readable == null ? "?" : readable;
        if (r.startsWith("ASM: ")) r = r.substring(5);
        int space = r.lastIndexOf(' ');
        String target = space > 0 ? r.substring(0, space) : r;
        String method = space > 0 ? r.substring(space + 1) : "";
        String eventType = "?";
        int open = method.indexOf('(');
        int close = method.indexOf(')');
        if (open >= 0 && close > open) {
            // "(Lnet/minecraftforge/event/terraingen/PopulateChunkEvent$Pre;)V" -> "PopulateChunkEvent.Pre"
            eventType = method.substring(open + 1, close);
            if (eventType.endsWith(";")) eventType = eventType.substring(0, eventType.length() - 1);
            eventType = eventType.substring(eventType.lastIndexOf('/') + 1)
                .replace('$', '.');
        }
        if (open > 0) method = method.substring(0, open);
        // drop the identity hash of the handler instance ("x.y.Handler@1b2c3d")
        int at = target.lastIndexOf('@');
        if (at > 0) target = target.substring(0, at);
        if (target.startsWith("class ")) target = target.substring(6);
        String mod = owner == null ? "?" : owner.getModId();
        return "Event " + eventType + " -> " + target + "." + method + " [" + mod + "]";
    }
}
