package fastloader.newgen.client;

import java.lang.reflect.Field;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import fastloader.Config;
import fastloader.FastLoader;
import fastloader.newgen.NewGen;
import fastloader.newgen.nether.NetherBiomes;
import ganymedes01.etfuturum.client.particle.CustomParticles;
import ganymedes01.etfuturum.client.sound.AmbienceLoop;
import ganymedes01.etfuturum.configuration.configs.ConfigSounds;
import ganymedes01.etfuturum.lib.Reference;

/**
 * Client side of the modern Nether: biome-blended fog colour, biome music, ambient loops / additions / mood sounds
 * and spore / ash particles. Sound files come from Et Futurum Requiem's asset downloader (they are fetched from the
 * official Minecraft asset server into the launcher's asset folder - nothing copyrighted is shipped in this mod).
 */
public class NetherClientHandler {

    public static final NetherClientHandler INSTANCE = new NetherClientHandler();

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random rand = new Random();

    private float fogR = 0.2F, fogG = 0.03F, fogB = 0.03F;
    private float targetR = fogR, targetG = fogG, targetB = fogB;
    private boolean fogInitialised;

    private BiomeGenBase currentBiome, prevBiome;
    private AmbienceLoop loop;
    private ISound music;

    private Boolean efrAmbienceBackup;
    private Field efrLoopField;

    private NetherClientHandler() {}

    private boolean active() {
        World w = mc.theWorld;
        return w != null && mc.thePlayer != null
            && w.provider instanceof WorldProviderHell
            && NewGen.useNewNether(
                w.getWorldInfo()
                    .getTerrainType());
    }

    // ------------------------------------------------------------------ fog

    private void updateFogTarget(EntityPlayer p) {
        // blend biome fog colours over a 5x5 grid of 8-block cells around the player
        float r = 0, g = 0, b = 0;
        int n = 0;
        int px = MathHelper.floor_double(p.posX), pz = MathHelper.floor_double(p.posZ);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int c = NetherBiomes.fogColor(mc.theWorld.getBiomeGenForCoords(px + dx * 8, pz + dz * 8));
            float wgt = (dx == 0 && dz == 0) ? 3 : 1;
            r += ((c >> 16) & 0xFF) / 255.0F * wgt;
            g += ((c >> 8) & 0xFF) / 255.0F * wgt;
            b += (c & 0xFF) / 255.0F * wgt;
            n += wgt;
        }
        targetR = r / n;
        targetG = g / n;
        targetB = b / n;
        if (!fogInitialised) {
            fogR = targetR;
            fogG = targetG;
            fogB = targetB;
            fogInitialised = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onFogColors(EntityViewRenderEvent.FogColors event) {
        if (!Config.biomeFog || !active() || !(event.entity instanceof EntityPlayer player)) return;
        Block atEyes = ActiveRenderInfo
            .getBlockAtEntityViewpoint(mc.theWorld, event.entity, (float) event.renderPartialTicks);
        if (atEyes.getMaterial() == Material.lava || atEyes.getMaterial() == Material.water) return;
        float r = fogR, g = fogG, b = fogB;
        if (player.isPotionActive(Potion.blindness)) return;
        if (player.isPotionActive(Potion.nightVision)) {
            float scale = Math
                .min(1.0F / Math.max(r, 1e-3F), Math.min(1.0F / Math.max(g, 1e-3F), 1.0F / Math.max(b, 1e-3F)));
            float bright = 0.6F;
            r = r * (1 - bright) + r * scale * bright;
            g = g * (1 - bright) + g * scale * bright;
            b = b * (1 - bright) + b * scale * bright;
        }
        event.red = r;
        event.green = g;
        event.blue = b;
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.world.isRemote) return;
        fogInitialised = false;
        stopLoop(false);
        prevBiome = currentBiome = null;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.isGamePaused()) return;
        if (!active()) {
            restoreEfrAmbience();
            if (loop != null) stopLoop(true);
            currentBiome = prevBiome = null;
            fogInitialised = false;
            return;
        }
        suppressEfrAmbience();
        EntityPlayer p = mc.thePlayer;
        int x = MathHelper.floor_double(p.posX), z = MathHelper.floor_double(p.posZ);
        if (!mc.theWorld.getChunkFromBlockCoords(x, z).isChunkLoaded) return;

        currentBiome = mc.theWorld.getBiomeGenForCoords(x, z);
        if (p.ticksExisted % 10 == 0 || !fogInitialised) updateFogTarget(p);
        float k = 0.02F;
        fogR += (targetR - fogR) * k;
        fogG += (targetG - fogG) * k;
        fogB += (targetB - fogB) * k;

        if (Config.biomeParticles) spawnParticles(p);
        if (Config.biomeAmbience && p.ticksExisted % 5 == 4) {
            tickAmbience();
            prevBiome = currentBiome;
        }
    }

    private void spawnParticles(EntityPlayer p) {
        if (mc.gameSettings.particleSetting == 2) return;
        BiomeGenBase biome = currentBiome;
        int count;
        if (biome == NetherBiomes.CRIMSON_FOREST) count = 60;
        else if (biome == NetherBiomes.WARPED_FOREST) count = 45;
        else if (biome == NetherBiomes.BASALT_DELTAS) count = 120;
        else if (biome == NetherBiomes.SOUL_SAND_VALLEY) count = 6;
        else return;
        if (mc.gameSettings.particleSetting == 1) count /= 2;
        for (int i = 0; i < count; i++) {
            double x = p.posX + rand.nextGaussian() * 10;
            double y = p.posY + rand.nextGaussian() * 6;
            double z = p.posZ + rand.nextGaussian() * 10;
            if (!mc.theWorld
                .isAirBlock(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z))) {
                continue;
            }
            if (biome == NetherBiomes.CRIMSON_FOREST) CustomParticles.spawnCrimsonSpore(mc.theWorld, x, y, z);
            else if (biome == NetherBiomes.WARPED_FOREST) CustomParticles.spawnWarpedSpore(mc.theWorld, x, y, z);
            else if (biome == NetherBiomes.SOUL_SAND_VALLEY) {
                if (rand.nextInt(3) == 0) CustomParticles.spawnSoulFlame(mc.theWorld, x, y, z, 0, 0.01, 0);
                else CustomParticles.spawnAshParticle(mc.theWorld, x, y, z);
            } else CustomParticles.spawnAshParticle(mc.theWorld, x, y, z);
        }
    }

    // ------------------------------------------------------------------ sounds

    private AmbienceLoop newLoop(BiomeGenBase biome) {
        String name = NetherBiomes.soundName(biome);
        AmbienceLoop l = new AmbienceLoop(
            name,
            name.equals("nether_wastes") ? 40 : 50,
            name.equals("nether_wastes") ? 80 : 90);
        l.setHasMusicOverride(Config.biomeMusic);
        return l;
    }

    private void tickAmbience() {
        if (loop == null || !mc.getSoundHandler()
            .isSoundPlaying(loop)) {
            if (loop != null) mc.getSoundHandler()
                .stopSound(loop);
            loop = newLoop(currentBiome);
            loop.skipFadeIn();
            mc.getSoundHandler()
                .playSound(loop);
            return;
        }
        if (prevBiome != currentBiome && !NetherBiomes.soundName(prevBiome)
            .equals(NetherBiomes.soundName(currentBiome))) {
            loop.fadeOut();
            loop = newLoop(currentBiome);
            mc.getSoundHandler()
                .playSound(loop);
        }
    }

    private void stopLoop(boolean fade) {
        if (loop != null) {
            if (fade) loop.fadeOut();
            else mc.getSoundHandler()
                .stopSound(loop);
        }
        loop = null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPlaySound(PlaySoundEvent17 event) {
        if (event.result == null || !active()) return;
        String biomeName = NetherBiomes.soundName(currentBiome);
        if (Config.biomeMusic && event.name.startsWith("music.game")) {
            if (music != null && mc.getSoundHandler()
                .isSoundPlaying(music)) {
                event.result = null; // still playing the previous biome track
                return;
            }
            music = PositionedSoundRecord
                .func_147673_a(new ResourceLocation(Reference.MCAssetVer + ":music.nether." + biomeName));
            event.result = music;
            return;
        }
        if (Config.biomeAmbience && event.name.equals("ambient.cave.cave")) {
            event.result = new PositionedSoundRecord(
                new ResourceLocation(Reference.MCAssetVer + ":ambient." + biomeName + ".mood"),
                event.sound.getVolume(),
                event.sound.getPitch(),
                event.sound.getXPosF(),
                event.sound.getYPosF(),
                event.sound.getZPosF());
        }
    }

    // ------------------------------------------------------------------ Et Futurum coexistence

    /** EFR plays its own (single, biome-unaware for our biomes) Nether loop; keep it quiet while we handle it. */
    private void suppressEfrAmbience() {
        if (efrAmbienceBackup == null) {
            efrAmbienceBackup = ConfigSounds.netherAmbience;
            ConfigSounds.netherAmbience = false;
            try {
                if (efrLoopField == null) {
                    efrLoopField = ganymedes01.etfuturum.core.handlers.ClientEventHandler.class
                        .getDeclaredField("netherAmbienceLoop");
                    efrLoopField.setAccessible(true);
                }
                Object l = efrLoopField.get(ganymedes01.etfuturum.core.handlers.ClientEventHandler.INSTANCE);
                if (l instanceof ISound s) mc.getSoundHandler()
                    .stopSound(s);
                efrLoopField.set(ganymedes01.etfuturum.core.handlers.ClientEventHandler.INSTANCE, null);
            } catch (Throwable t) {
                FastLoader.LOG.debug("[NewGen] Could not reach EFR's nether ambience loop", t);
            }
        }
    }

    private void restoreEfrAmbience() {
        if (efrAmbienceBackup != null) {
            ConfigSounds.netherAmbience = efrAmbienceBackup;
            efrAmbienceBackup = null;
        }
    }
}
