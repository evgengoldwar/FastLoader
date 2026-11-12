package fastloader.newgen.nether;

import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.feature.WorldGenFire;
import net.minecraft.world.gen.feature.WorldGenFlowers;
import net.minecraft.world.gen.feature.WorldGenGlowStone1;
import net.minecraft.world.gen.feature.WorldGenGlowStone2;
import net.minecraft.world.gen.feature.WorldGenHellLava;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraft.world.gen.feature.WorldGenerator;

import cpw.mods.fml.common.Loader;
import fastloader.newgen.block.BlockState;
import fastloader.newgen.block.NGBlocks;
import fastloader.newgen.nether.feature.BasaltFeatures;
import fastloader.newgen.nether.feature.FeatureUtil;
import fastloader.newgen.nether.feature.FossilFeature;
import fastloader.newgen.nether.feature.HugeFungusFeature;
import fastloader.newgen.nether.feature.VinesFeature;

/** Per-biome Nether features, modelled on the modern feature lists of each biome. */
public class NetherDecorator {

    private final WorldGenerator lavaOpen = new WorldGenHellLava(Blocks.flowing_lava, false);
    private final WorldGenerator lavaClosed = new WorldGenHellLava(Blocks.flowing_lava, true);
    private final WorldGenerator fire = new WorldGenFire();
    private final WorldGenerator glowA = new WorldGenGlowStone1();
    private final WorldGenerator glowB = new WorldGenGlowStone2();
    private final WorldGenerator brownMushroom = new WorldGenFlowers(Blocks.brown_mushroom);
    private final WorldGenerator redMushroom = new WorldGenFlowers(Blocks.red_mushroom);
    private final WorldGenerator quartz = new WorldGenMinable(Blocks.quartz_ore, 14, Blocks.netherrack);
    private final WorldGenerator soulSandOre = new WorldGenMinable(Blocks.soul_sand, 12, Blocks.netherrack);
    private final WorldGenerator gravelOre = new WorldGenMinable(Blocks.gravel, 33, Blocks.netherrack);
    private final WorldGenerator blackstoneOre;
    private final WorldGenerator magmaOre, goldOre, debrisOre;
    /** Et Futurum already places magma, nether gold and ancient debris in every WorldProviderHell. */
    private final boolean efrOres;

    public NetherDecorator(NetherChunkProviderNG provider) {
        efrOres = Loader.isModLoaded("etfuturum");
        blackstoneOre = new WorldGenMinable(NGBlocks.BLACKSTONE.block, NGBlocks.BLACKSTONE.meta, 33, Blocks.netherrack);
        magmaOre = new WorldGenMinable(NGBlocks.MAGMA.block, NGBlocks.MAGMA.meta, 33, Blocks.netherrack);
        goldOre = new WorldGenMinable(
            NGBlocks.NETHER_GOLD_ORE.block,
            NGBlocks.NETHER_GOLD_ORE.meta,
            10,
            Blocks.netherrack);
        debrisOre = NGBlocks.ANCIENT_DEBRIS == null ? null
            : new WorldGenMinable(NGBlocks.ANCIENT_DEBRIS.block, NGBlocks.ANCIENT_DEBRIS.meta, 3, Blocks.netherrack);
    }

    interface FloorConsumer {

        void accept(int x, int y, int z);
    }

    /**
     * Modern "count on every layer": for each of {@code count} random columns, every floor in that column is a
     * candidate. This is what makes nether forests and deltas cover every terrace, not just the top one.
     */
    private static void everyLayer(World w, Random r, int bx, int bz, int count, FloorConsumer c) {
        for (int i = 0; i < count; i++) {
            int x = bx + 8 + r.nextInt(16), z = bz + 8 + r.nextInt(16);
            for (int y = 125; y > NetherChunkProviderNG.LAVA_LEVEL - 4; y--) {
                if (FeatureUtil.isAir(w, x, y + 1, z) && FeatureUtil.isSolid(w, x, y, z)) {
                    c.accept(x, y + 1, z);
                    y -= 2;
                }
            }
        }
    }

    private static int rx(Random r, int b) {
        return b + 8 + r.nextInt(16);
    }

    private void ore(World w, Random r, int bx, int bz, WorldGenerator g, int count, int minY, int maxY) {
        for (int i = 0; i < count; i++) g.generate(w, r, rx(r, bx), minY + r.nextInt(maxY - minY), rx(r, bz));
    }

    private void common(World w, Random r, int bx, int bz, int glowCount, boolean mushrooms) {
        for (int i = 0; i < 8; i++) lavaOpen.generate(w, r, rx(r, bx), r.nextInt(120) + 4, rx(r, bz));
        int gExtra = r.nextInt(r.nextInt(glowCount) + 1);
        for (int i = 0; i < gExtra; i++) glowA.generate(w, r, rx(r, bx), r.nextInt(120) + 4, rx(r, bz));
        for (int i = 0; i < glowCount; i++) glowB.generate(w, r, rx(r, bx), r.nextInt(128), rx(r, bz));
        if (mushrooms) {
            if (r.nextBoolean()) brownMushroom.generate(w, r, rx(r, bx), r.nextInt(128), rx(r, bz));
            if (r.nextBoolean()) redMushroom.generate(w, r, rx(r, bx), r.nextInt(128), rx(r, bz));
        }
        ore(w, r, bx, bz, quartz, 16, 10, 118);
        if (!efrOres) {
            ore(w, r, bx, bz, magmaOre, 4, 27, 37);
            ore(w, r, bx, bz, goldOre, 10, 10, 118);
            if (debrisOre != null) ore(w, r, bx, bz, debrisOre, 1, 8, 24);
        }
        ore(w, r, bx, bz, gravelOre, 2, 5, 37);
        ore(w, r, bx, bz, blackstoneOre, 2, 5, 31);
        for (int i = 0; i < 16; i++) lavaClosed.generate(w, r, rx(r, bx), r.nextInt(108) + 10, rx(r, bz));
    }

    private void fire(World w, Random r, int bx, int bz) {
        int n = r.nextInt(r.nextInt(10) + 1) + 1;
        for (int i = 0; i < n; i++) fire.generate(w, r, rx(r, bx), r.nextInt(120) + 4, rx(r, bz));
    }

    public void decorate(World w, Random r, int bx, int bz, BiomeGenBase biome) {
        if (biome == NetherBiomes.CRIMSON_FOREST) crimson(w, r, bx, bz);
        else if (biome == NetherBiomes.WARPED_FOREST) warped(w, r, bx, bz);
        else if (biome == NetherBiomes.SOUL_SAND_VALLEY) soulSandValley(w, r, bx, bz);
        else if (biome == NetherBiomes.BASALT_DELTAS) deltas(w, r, bx, bz);
        else wastes(w, r, bx, bz);
    }

    private void wastes(World w, Random r, int bx, int bz) {
        fire(w, r, bx, bz);
        common(w, r, bx, bz, 10, true);
    }

    private void crimson(World w, Random r, int bx, int bz) {
        // weeping vines first so the fungi can still grow under them
        for (int i = 0; i < 10; i++) VinesFeature.weeping(w, r, rx(r, bx), 40 + r.nextInt(86), rx(r, bz));
        everyLayer(w, r, bx, bz, 8, (x, y, z) -> HugeFungusFeature.generate(w, r, x, y, z, true, false));
        everyLayer(w, r, bx, bz, 6, (x, y, z) -> vegetation(w, r, x, y, z, true));
        common(w, r, bx, bz, 10, false);
    }

    private void warped(World w, Random r, int bx, int bz) {
        everyLayer(w, r, bx, bz, 8, (x, y, z) -> HugeFungusFeature.generate(w, r, x, y, z, false, false));
        everyLayer(w, r, bx, bz, 5, (x, y, z) -> vegetation(w, r, x, y, z, false));
        everyLayer(w, r, bx, bz, 1, (x, y, z) -> sprouts(w, r, x, y, z));
        for (int i = 0; i < 10; i++) VinesFeature.twisting(w, r, rx(r, bx), 32 + r.nextInt(90), rx(r, bz));
        common(w, r, bx, bz, 10, false);
    }

    private void soulSandValley(World w, Random r, int bx, int bz) {
        for (int i = 0; i < 10; i++) BasaltFeatures.pillar(w, r, rx(r, bx), r.nextInt(128), rx(r, bz));
        if (r.nextInt(3) == 0) FossilFeature.generate(w, r, rx(r, bx), 30 + r.nextInt(80), rx(r, bz));
        ore(w, r, bx, bz, soulSandOre, 12, 0, 32);
        common(w, r, bx, bz, 8, false);
    }

    private void deltas(World w, Random r, int bx, int bz) {
        everyLayer(w, r, bx, bz, 40, (x, y, z) -> BasaltFeatures.delta(w, r, x, y - 1, z));
        everyLayer(w, r, bx, bz, 4, (x, y, z) -> BasaltFeatures.columns(w, r, x, y - 1, z, 1, 1, 1, 4));
        everyLayer(w, r, bx, bz, 2, (x, y, z) -> BasaltFeatures.columns(w, r, x, y - 1, z, 2, 3, 5, 10));
        for (int i = 0; i < 75; i++)
            BasaltFeatures.replaceBlob(w, r, rx(r, bx), r.nextInt(128), rx(r, bz), NGBlocks.BASALT, 3, 7);
        for (int i = 0; i < 25; i++)
            BasaltFeatures.replaceBlob(w, r, rx(r, bx), r.nextInt(128), rx(r, bz), NGBlocks.BLACKSTONE, 3, 7);
        fire(w, r, bx, bz);
        common(w, r, bx, bz, 6, false);
        // extra gold & quartz ("gold_deltas" / "quartz_deltas")
        ore(w, r, bx, bz, quartz, 16, 10, 118);
        if (!efrOres) ore(w, r, bx, bz, goldOre, 10, 10, 118);
    }

    private static void vegetation(World w, Random r, int x, int y, int z, boolean crimson) {
        BlockState ground = crimson ? NGBlocks.CRIMSON_NYLIUM : NGBlocks.WARPED_NYLIUM;
        for (int i = 0; i < 64; i++) {
            int px = x + r.nextInt(8) - r.nextInt(8);
            int py = y + r.nextInt(4) - r.nextInt(4);
            int pz = z + r.nextInt(8) - r.nextInt(8);
            if (!FeatureUtil.isAir(w, px, py, pz) || !FeatureUtil.is(w, px, py - 1, pz, ground)) continue;
            int roll = r.nextInt(100);
            BlockState s;
            if (crimson) {
                s = roll < 87 ? NGBlocks.CRIMSON_ROOTS : roll < 98 ? NGBlocks.CRIMSON_FUNGUS : NGBlocks.WARPED_FUNGUS;
            } else {
                s = roll < 85 ? NGBlocks.WARPED_ROOTS
                    : roll < 86 ? NGBlocks.CRIMSON_ROOTS : roll < 99 ? NGBlocks.WARPED_FUNGUS : NGBlocks.CRIMSON_FUNGUS;
            }
            FeatureUtil.set(w, px, py, pz, s);
        }
    }

    private static void sprouts(World w, Random r, int x, int y, int z) {
        for (int i = 0; i < 64; i++) {
            int px = x + r.nextInt(8) - r.nextInt(8);
            int py = y + r.nextInt(4) - r.nextInt(4);
            int pz = z + r.nextInt(8) - r.nextInt(8);
            if (FeatureUtil.isAir(w, px, py, pz) && FeatureUtil.is(w, px, py - 1, pz, NGBlocks.WARPED_NYLIUM)) {
                FeatureUtil.set(w, px, py, pz, NGBlocks.NETHER_SPROUTS);
            }
        }
    }
}
