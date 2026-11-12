package fastloader.newgen.nether;

import net.minecraft.world.biome.BiomeGenBase;

import fastloader.Config;

public final class NetherBiomes {

    public static BiomeGenBase NETHER_WASTES;
    public static NetherBiome CRIMSON_FOREST, WARPED_FOREST, SOUL_SAND_VALLEY, BASALT_DELTAS;

    private NetherBiomes() {}

    public static void init() {
        if (Config.netherWastesId < 0) {
            NetherBiome.tweakVanillaHell();
            NETHER_WASTES = BiomeGenBase.hell;
        } else {
            NETHER_WASTES = NetherBiome.netherWastes(Config.netherWastesId);
        }
        CRIMSON_FOREST = NetherBiome.crimsonForest(Config.crimsonForestId);
        WARPED_FOREST = NetherBiome.warpedForest(Config.warpedForestId);
        SOUL_SAND_VALLEY = NetherBiome.soulSandValley(Config.soulSandValleyId);
        BASALT_DELTAS = NetherBiome.basaltDeltas(Config.basaltDeltasId);
    }

    public static String soundName(BiomeGenBase b) {
        if (b instanceof NetherBiome nb) return nb.soundName;
        return "nether_wastes";
    }

    public static int fogColor(BiomeGenBase b) {
        if (b instanceof NetherBiome nb) return nb.fogColor;
        return 0x330808;
    }
}
