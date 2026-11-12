package fastloader.newgen.overworld;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlower;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.feature.WorldGenAbstractTree;
import net.minecraft.world.gen.feature.WorldGenTaiga1;
import net.minecraft.world.gen.feature.WorldGenTaiga2;
import net.minecraft.world.gen.feature.WorldGenTrees;
import net.minecraftforge.common.BiomeDictionary;

import fastloader.newgen.block.BlockState;
import fastloader.newgen.block.NGBlocks;
import fastloader.newgen.overworld.feature.CherryTreeFeature;

/**
 * Overworld biomes that only exist in modern versions: meadow, grove, snowy slopes, the three peak biomes, cherry
 * grove and the warm/lukewarm/cold oceans.
 */
public class ModernBiome extends BiomeGenBase {

    public enum Kind {
        MEADOW,
        GROVE,
        SNOWY_SLOPES,
        JAGGED_PEAKS,
        FROZEN_PEAKS,
        STONY_PEAKS,
        CHERRY_GROVE,
        WARM_OCEAN,
        LUKEWARM_OCEAN,
        COLD_OCEAN
    }

    public final Kind kind;

    public ModernBiome(int id, Kind kind) {
        super(id, true);
        this.kind = kind;
        theBiomeDecorator.treesPerChunk = -999;
        theBiomeDecorator.grassPerChunk = 0;
        theBiomeDecorator.flowersPerChunk = 0;
        switch (kind) {
            case MEADOW -> {
                setBiomeName("Meadow");
                setColor(0x83BB6D);
                setTemperatureRainfall(0.5F, 0.8F);
                theBiomeDecorator.treesPerChunk = 0;
                theBiomeDecorator.grassPerChunk = 20;
                theBiomeDecorator.flowersPerChunk = 12;
                spawnableCreatureList.add(new SpawnListEntry(EntityHorse.class, 1, 2, 6));
                BiomeDictionary.registerBiomeType(this, BiomeDictionary.Type.PLAINS, BiomeDictionary.Type.MOUNTAIN);
            }
            case GROVE -> {
                setBiomeName("Grove");
                setColor(0x456D51);
                setTemperatureRainfall(-0.2F, 0.8F);
                setEnableSnow();
                theBiomeDecorator.treesPerChunk = 10;
                theBiomeDecorator.grassPerChunk = 1;
                spawnableCreatureList.add(new SpawnListEntry(EntityWolf.class, 8, 4, 4));
                BiomeDictionary.registerBiomeType(
                    this,
                    BiomeDictionary.Type.CONIFEROUS,
                    BiomeDictionary.Type.SNOWY,
                    BiomeDictionary.Type.MOUNTAIN,
                    BiomeDictionary.Type.FOREST);
            }
            case SNOWY_SLOPES -> {
                setBiomeName("Snowy Slopes");
                setColor(0xC4C4C4);
                setTemperatureRainfall(-0.3F, 0.9F);
                setEnableSnow();
                topBlock = Blocks.snow;
                fillerBlock = Blocks.snow;
                BiomeDictionary.registerBiomeType(this, BiomeDictionary.Type.SNOWY, BiomeDictionary.Type.MOUNTAIN);
            }
            case JAGGED_PEAKS -> {
                setBiomeName("Jagged Peaks");
                setColor(0xDCDCDC);
                setTemperatureRainfall(-0.7F, 0.9F);
                setEnableSnow();
                topBlock = Blocks.snow;
                fillerBlock = Blocks.stone;
                spawnableCreatureList.clear();
                BiomeDictionary.registerBiomeType(this, BiomeDictionary.Type.SNOWY, BiomeDictionary.Type.MOUNTAIN);
            }
            case FROZEN_PEAKS -> {
                setBiomeName("Frozen Peaks");
                setColor(0xB0C4DE);
                setTemperatureRainfall(-0.7F, 0.9F);
                setEnableSnow();
                topBlock = Blocks.snow;
                fillerBlock = Blocks.packed_ice;
                spawnableCreatureList.clear();
                BiomeDictionary.registerBiomeType(this, BiomeDictionary.Type.SNOWY, BiomeDictionary.Type.MOUNTAIN);
            }
            case STONY_PEAKS -> {
                setBiomeName("Stony Peaks");
                setColor(0x888888);
                setTemperatureRainfall(1.0F, 0.3F);
                topBlock = Blocks.stone;
                fillerBlock = Blocks.stone;
                spawnableCreatureList.clear();
                BiomeDictionary.registerBiomeType(this, BiomeDictionary.Type.MOUNTAIN);
            }
            case CHERRY_GROVE -> {
                setBiomeName("Cherry Grove");
                setColor(0xF5A6C9);
                setTemperatureRainfall(0.5F, 0.8F);
                theBiomeDecorator.treesPerChunk = 3;
                theBiomeDecorator.grassPerChunk = 10;
                theBiomeDecorator.flowersPerChunk = 6;
                spawnableCreatureList.add(new SpawnListEntry(EntityPig.class, 1, 1, 2));
                BiomeDictionary.registerBiomeType(this, BiomeDictionary.Type.FOREST, BiomeDictionary.Type.MOUNTAIN);
            }
            case WARM_OCEAN, LUKEWARM_OCEAN, COLD_OCEAN -> {
                setBiomeName(
                    kind == Kind.WARM_OCEAN ? "Warm Ocean"
                        : kind == Kind.LUKEWARM_OCEAN ? "Lukewarm Ocean" : "Cold Ocean");
                setColor(kind == Kind.WARM_OCEAN ? 0x43D5EE : kind == Kind.LUKEWARM_OCEAN ? 0x45ADF2 : 0x3D57D6);
                setTemperatureRainfall(
                    kind == Kind.WARM_OCEAN ? 0.8F : kind == Kind.LUKEWARM_OCEAN ? 0.6F : 0.4F,
                    0.5F);
                spawnableCreatureList.clear();
                if (kind != Kind.COLD_OCEAN) {
                    topBlock = Blocks.sand;
                    fillerBlock = Blocks.sand;
                } else {
                    topBlock = Blocks.gravel;
                    fillerBlock = Blocks.gravel;
                }
                BiomeDictionary.registerBiomeType(this, BiomeDictionary.Type.OCEAN, BiomeDictionary.Type.WATER);
            }
        }
        if (kind == Kind.MEADOW || kind == Kind.CHERRY_GROVE) {
            spawnableCreatureList.clear();
            spawnableCreatureList.add(new SpawnListEntry(EntitySheep.class, 12, 2, 4));
            spawnableCreatureList.add(new SpawnListEntry(EntityCow.class, 4, 2, 3));
            spawnableCreatureList.add(new SpawnListEntry(EntityHorse.class, 2, 2, 4));
        }
    }

    public boolean isOcean() {
        return kind == Kind.WARM_OCEAN || kind == Kind.LUKEWARM_OCEAN || kind == Kind.COLD_OCEAN;
    }

    @Override
    public WorldGenAbstractTree func_150567_a(Random rand) {
        return switch (kind) {
            case GROVE -> rand.nextInt(3) == 0 ? new WorldGenTaiga1() : new WorldGenTaiga2(false);
            case MEADOW -> new WorldGenTrees(false);
            case CHERRY_GROVE -> new CherryTreeFeature();
            default -> super.func_150567_a(rand);
        };
    }

    @Override
    public void decorate(World world, Random rand, int x, int z) {
        if (kind == Kind.MEADOW && rand.nextInt(10) == 0) {
            // meadows have a lone tree (often with a beehive in modern versions) every now and then
            int tx = x + rand.nextInt(16) + 8, tz = z + rand.nextInt(16) + 8;
            new WorldGenTrees(false).generate(world, rand, tx, world.getHeightValue(tx, tz), tz);
        }
        super.decorate(world, rand, x, z);
    }

    @Override
    public void plantFlower(World world, Random rand, int x, int y, int z) {
        if (kind == Kind.CHERRY_GROVE) {
            world.setBlock(x, y, z, Blocks.red_flower, 7, 2); // pink tulip
            return;
        }
        if (kind == Kind.MEADOW) {
            int r = rand.nextInt(8);
            if (r == 0 && NGBlocks.CORNFLOWER != null) {
                world.setBlock(x, y, z, NGBlocks.CORNFLOWER.block, NGBlocks.CORNFLOWER.meta, 2);
                return;
            }
            String[] options = { "allium", "houstonia", "oxeyeDaisy", "tulipPink", "tulipWhite", "blueOrchid" };
            Block f = Blocks.red_flower;
            int meta = BlockFlower.func_149856_f(options[rand.nextInt(options.length)]);
            world.setBlock(x, y, z, f, Math.max(meta, 0), 2);
            return;
        }
        super.plantFlower(world, rand, x, y, z);
    }

    @Override
    public int getBiomeGrassColor(int x, int y, int z) {
        return switch (kind) {
            case MEADOW -> 0x83BB6D;
            case CHERRY_GROVE -> 0xB6DB61;
            case GROVE -> 0x80B497;
            default -> super.getBiomeGrassColor(x, y, z);
        };
    }

    @Override
    public int getBiomeFoliageColor(int x, int y, int z) {
        return switch (kind) {
            case MEADOW -> 0x63A948;
            case CHERRY_GROVE -> 0xB6DB61;
            case GROVE -> 0x60A17B;
            default -> super.getBiomeFoliageColor(x, y, z);
        };
    }

    @Override
    public int getWaterColorMultiplier() {
        return switch (kind) {
            case WARM_OCEAN -> 0x43D5EE;
            case LUKEWARM_OCEAN -> 0x45ADF2;
            case COLD_OCEAN -> 0x3D57D6;
            case MEADOW -> 0x0E4ECF;
            case CHERRY_GROVE -> 0x5DB7EF;
            default -> super.getWaterColorMultiplier();
        };
    }

    /** Surface blocks: snow-capped peaks, ice in frozen peaks, calcite streaks in stony peaks. */
    @Override
    public void genTerrainBlocks(World world, Random rand, Block[] blocks, byte[] metas, int x, int z, double noise) {
        switch (kind) {
            case JAGGED_PEAKS, FROZEN_PEAKS, STONY_PEAKS, SNOWY_SLOPES -> mountainSurface(
                rand,
                blocks,
                metas,
                x,
                z,
                noise);
            default -> genBiomeTerrain(world, rand, blocks, metas, x, z, noise);
        }
    }

    private void mountainSurface(Random rand, Block[] blocks, byte[] metas, int x, int z, double noise) {
        int lx = x & 15, lz = z & 15;
        int h = blocks.length / 256;
        int depth = -1;
        int surfaceDepth = (int) (noise / 3.0 + 3.0 + rand.nextDouble() * 0.25);
        for (int y = 255; y >= 0; y--) {
            int i = (lz * 16 + lx) * h + y;
            Block b = blocks[i];
            if (b == null || b == Blocks.air) {
                depth = -1;
                continue;
            }
            if (b != Blocks.stone) {
                if (b == Blocks.water) depth = 0;
                continue;
            }
            depth++;
            if (depth > surfaceDepth) continue;
            BlockState put = null;
            switch (kind) {
                case STONY_PEAKS -> {
                    if (NGBlocks.CALCITE != null && Math.sin(x * 0.11 + z * 0.07 + noise) > 0.75)
                        put = NGBlocks.CALCITE;
                }
                case FROZEN_PEAKS -> put = depth == 0 ? BlockState.of(Blocks.snow)
                    : (noise > 0.5 ? BlockState.of(Blocks.packed_ice) : BlockState.of(Blocks.ice));
                case JAGGED_PEAKS, SNOWY_SLOPES -> put = depth <= (noise > 1 ? 1 : 2) ? BlockState.of(Blocks.snow)
                    : null;
                default -> {}
            }
            if (put != null) {
                blocks[i] = put.block;
                metas[i] = (byte) put.meta;
            }
        }
    }
}
