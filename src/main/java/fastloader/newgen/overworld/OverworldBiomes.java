package fastloader.newgen.overworld;

import net.minecraft.world.biome.BiomeGenBase;

import fastloader.Config;
import fastloader.newgen.overworld.ModernBiome.Kind;

/** Maps the modern overworld biome names onto 1.7.10 biomes (or this mod's modern ones). */
public final class OverworldBiomes {

    public static ModernBiome MEADOW, GROVE, SNOWY_SLOPES, JAGGED_PEAKS, FROZEN_PEAKS, STONY_PEAKS, CHERRY_GROVE,
        WARM_OCEAN, LUKEWARM_OCEAN, COLD_OCEAN;

    // modern name -> biome
    public static BiomeGenBase SNOWY_PLAINS, ICE_SPIKES, SNOWY_TAIGA, TAIGA, OLD_GROWTH_SPRUCE_TAIGA,
        OLD_GROWTH_PINE_TAIGA, PLAINS, SUNFLOWER_PLAINS, FOREST, FLOWER_FOREST, BIRCH_FOREST, OLD_GROWTH_BIRCH_FOREST,
        DARK_FOREST, SAVANNA, SAVANNA_PLATEAU, WINDSWEPT_SAVANNA, JUNGLE, SPARSE_JUNGLE, BAMBOO_JUNGLE, DESERT,
        BADLANDS, WOODED_BADLANDS, ERODED_BADLANDS, SWAMP, RIVER, FROZEN_RIVER, BEACH, SNOWY_BEACH, STONY_SHORE,
        MUSHROOM_FIELDS, OCEAN, DEEP_OCEAN, FROZEN_OCEAN, DEEP_FROZEN_OCEAN, DEEP_COLD_OCEAN, DEEP_LUKEWARM_OCEAN,
        WINDSWEPT_HILLS, WINDSWEPT_GRAVELLY_HILLS, WINDSWEPT_FOREST;

    private OverworldBiomes() {}

    private static BiomeGenBase mutated(BiomeGenBase base) {
        BiomeGenBase[] all = BiomeGenBase.getBiomeGenArray();
        int id = base.biomeID + 128;
        return id < all.length && all[id] != null ? all[id] : base;
    }

    public static void init() {
        MEADOW = new ModernBiome(Config.meadowId, Kind.MEADOW);
        GROVE = new ModernBiome(Config.groveId, Kind.GROVE);
        SNOWY_SLOPES = new ModernBiome(Config.snowySlopesId, Kind.SNOWY_SLOPES);
        JAGGED_PEAKS = new ModernBiome(Config.jaggedPeaksId, Kind.JAGGED_PEAKS);
        FROZEN_PEAKS = new ModernBiome(Config.frozenPeaksId, Kind.FROZEN_PEAKS);
        STONY_PEAKS = new ModernBiome(Config.stonyPeaksId, Kind.STONY_PEAKS);
        CHERRY_GROVE = new ModernBiome(Config.cherryGroveId, Kind.CHERRY_GROVE);
        WARM_OCEAN = new ModernBiome(Config.warmOceanId, Kind.WARM_OCEAN);
        LUKEWARM_OCEAN = new ModernBiome(Config.lukewarmOceanId, Kind.LUKEWARM_OCEAN);
        COLD_OCEAN = new ModernBiome(Config.coldOceanId, Kind.COLD_OCEAN);

        SNOWY_PLAINS = BiomeGenBase.icePlains;
        ICE_SPIKES = mutated(BiomeGenBase.icePlains);
        SNOWY_TAIGA = BiomeGenBase.coldTaiga;
        TAIGA = BiomeGenBase.taiga;
        OLD_GROWTH_SPRUCE_TAIGA = mutated(BiomeGenBase.megaTaiga);
        OLD_GROWTH_PINE_TAIGA = BiomeGenBase.megaTaiga;
        PLAINS = BiomeGenBase.plains;
        SUNFLOWER_PLAINS = mutated(BiomeGenBase.plains);
        FOREST = BiomeGenBase.forest;
        FLOWER_FOREST = mutated(BiomeGenBase.forest);
        BIRCH_FOREST = BiomeGenBase.birchForest;
        OLD_GROWTH_BIRCH_FOREST = mutated(BiomeGenBase.birchForest);
        DARK_FOREST = BiomeGenBase.roofedForest;
        SAVANNA = BiomeGenBase.savanna;
        SAVANNA_PLATEAU = BiomeGenBase.savannaPlateau;
        WINDSWEPT_SAVANNA = mutated(BiomeGenBase.savanna);
        JUNGLE = BiomeGenBase.jungle;
        SPARSE_JUNGLE = BiomeGenBase.jungleEdge;
        BAMBOO_JUNGLE = mutated(BiomeGenBase.jungle);
        DESERT = BiomeGenBase.desert;
        BADLANDS = BiomeGenBase.mesa;
        WOODED_BADLANDS = BiomeGenBase.mesaPlateau_F;
        ERODED_BADLANDS = mutated(BiomeGenBase.mesa);
        SWAMP = BiomeGenBase.swampland;
        RIVER = BiomeGenBase.river;
        FROZEN_RIVER = BiomeGenBase.frozenRiver;
        BEACH = BiomeGenBase.beach;
        SNOWY_BEACH = BiomeGenBase.coldBeach;
        STONY_SHORE = BiomeGenBase.stoneBeach;
        MUSHROOM_FIELDS = BiomeGenBase.mushroomIsland;
        OCEAN = BiomeGenBase.ocean;
        DEEP_OCEAN = BiomeGenBase.deepOcean;
        FROZEN_OCEAN = BiomeGenBase.frozenOcean;
        DEEP_FROZEN_OCEAN = BiomeGenBase.frozenOcean;
        DEEP_COLD_OCEAN = BiomeGenBase.deepOcean;
        DEEP_LUKEWARM_OCEAN = BiomeGenBase.deepOcean;
        WINDSWEPT_HILLS = BiomeGenBase.extremeHills;
        WINDSWEPT_GRAVELLY_HILLS = mutated(BiomeGenBase.extremeHills);
        WINDSWEPT_FOREST = BiomeGenBase.extremeHillsPlus;
    }
}
