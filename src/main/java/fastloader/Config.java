package fastloader;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static final String CAT_BIOMES = "biomes";
    private static final String CAT_NETHER = "nether";
    private static final String CAT_CLIENT = "client";
    private static final String CAT_OVERWORLD = "overworld";

    // nether biomes
    public static int netherWastesId = -1; // -1 = reuse vanilla "Hell"
    public static int crimsonForestId = 170;
    public static int warpedForestId = 171;
    public static int soulSandValleyId = 172;
    public static int basaltDeltasId = 173;

    // modern overworld biomes that 1.7.10 does not have
    public static int meadowId = 174;
    public static int groveId = 175;
    public static int snowySlopesId = 176;
    public static int jaggedPeaksId = 177;
    public static int frozenPeaksId = 178;
    public static int stonyPeaksId = 179;
    public static int cherryGroveId = 180;
    public static int warmOceanId = 181;
    public static int lukewarmOceanId = 182;
    public static int coldOceanId = 183;

    public static boolean netherFortresses = true;
    public static boolean netherOnlyInNewGenWorlds = true;

    public static boolean biomeFog = true;
    public static boolean biomeMusic = true;
    public static boolean biomeAmbience = true;
    public static boolean biomeParticles = true;

    public static boolean overworldCaves = true;
    public static boolean overworldAquifers = true;

    public static void synchronizeConfiguration(File configFile) {
        Configuration c = new Configuration(configFile);

        c.setCategoryComment(CAT_BIOMES, "Biome IDs used by the 'New Generation' world type. Must be unique (0-255).");
        netherWastesId = c.getInt(
            "netherWastes",
            CAT_BIOMES,
            netherWastesId,
            -1,
            255,
            "-1 = use the vanilla Hell biome as Nether Wastes (recommended for mod compatibility)");
        crimsonForestId = c.getInt("crimsonForest", CAT_BIOMES, crimsonForestId, 0, 255, "");
        warpedForestId = c.getInt("warpedForest", CAT_BIOMES, warpedForestId, 0, 255, "");
        soulSandValleyId = c.getInt("soulSandValley", CAT_BIOMES, soulSandValleyId, 0, 255, "");
        basaltDeltasId = c.getInt("basaltDeltas", CAT_BIOMES, basaltDeltasId, 0, 255, "");
        meadowId = c.getInt("meadow", CAT_BIOMES, meadowId, 0, 255, "");
        groveId = c.getInt("grove", CAT_BIOMES, groveId, 0, 255, "");
        snowySlopesId = c.getInt("snowySlopes", CAT_BIOMES, snowySlopesId, 0, 255, "");
        jaggedPeaksId = c.getInt("jaggedPeaks", CAT_BIOMES, jaggedPeaksId, 0, 255, "");
        frozenPeaksId = c.getInt("frozenPeaks", CAT_BIOMES, frozenPeaksId, 0, 255, "");
        stonyPeaksId = c.getInt("stonyPeaks", CAT_BIOMES, stonyPeaksId, 0, 255, "");
        cherryGroveId = c.getInt("cherryGrove", CAT_BIOMES, cherryGroveId, 0, 255, "");
        warmOceanId = c.getInt("warmOcean", CAT_BIOMES, warmOceanId, 0, 255, "");
        lukewarmOceanId = c.getInt("lukewarmOcean", CAT_BIOMES, lukewarmOceanId, 0, 255, "");
        coldOceanId = c.getInt("coldOcean", CAT_BIOMES, coldOceanId, 0, 255, "");

        netherFortresses = c.getBoolean("fortresses", CAT_NETHER, netherFortresses, "Generate nether fortresses");
        netherOnlyInNewGenWorlds = c.getBoolean(
            "onlyInNewGenWorlds",
            CAT_NETHER,
            netherOnlyInNewGenWorlds,
            "If false, the new Nether also replaces the Nether of every other world type");

        biomeFog = c.getBoolean("biomeFog", CAT_CLIENT, biomeFog, "Per-biome Nether fog colours");
        biomeMusic = c.getBoolean(
            "biomeMusic",
            CAT_CLIENT,
            biomeMusic,
            "Per-biome Nether music (sound files are provided by Et Futurum Requiem's asset downloader)");
        biomeAmbience = c.getBoolean("biomeAmbience", CAT_CLIENT, biomeAmbience, "Per-biome Nether ambient loops");
        biomeParticles = c
            .getBoolean("biomeParticles", CAT_CLIENT, biomeParticles, "Spores / ash particles in Nether biomes");

        overworldCaves = c
            .getBoolean("noiseCaves", CAT_OVERWORLD, overworldCaves, "Cheese, spaghetti and noodle caves");
        overworldAquifers = c
            .getBoolean("aquifers", CAT_OVERWORLD, overworldAquifers, "Local water / lava levels inside caves");

        if (c.hasChanged()) c.save();
    }
}
