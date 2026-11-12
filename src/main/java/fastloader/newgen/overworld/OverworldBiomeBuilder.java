package fastloader.newgen.overworld;

import static fastloader.newgen.overworld.OverworldBiomes.*;

import net.minecraft.world.biome.BiomeGenBase;

/**
 * Chooses a biome from the climate the same way modern versions do: temperature/humidity pick the "flavour",
 * continentalness/erosion/peaks-and-valleys pick the landform (ocean, coast, valley, lowland, plateau, slope,
 * peak...). Tables follow the modern overworld biome builder.
 */
public final class OverworldBiomeBuilder {

    private OverworldBiomeBuilder() {}

    private static final double[] TEMP = { -0.45, -0.15, 0.2, 0.55 };
    private static final double[] HUMID = { -0.35, -0.1, 0.1, 0.3 };
    private static final double[] EROSION = { -0.78, -0.375, -0.2225, 0.05, 0.45, 0.55 };

    private static int level(double v, double[] bounds) {
        int i = 0;
        while (i < bounds.length && v >= bounds[i]) i++;
        return i;
    }

    private static BiomeGenBase[][] MIDDLE, MIDDLE_VARIANT, PLATEAU, PLATEAU_VARIANT, SHATTERED;

    private static void tables() {
        if (MIDDLE != null) return;
        MIDDLE = new BiomeGenBase[][] { { SNOWY_PLAINS, SNOWY_PLAINS, SNOWY_PLAINS, SNOWY_TAIGA, TAIGA },
            { PLAINS, PLAINS, FOREST, TAIGA, OLD_GROWTH_SPRUCE_TAIGA },
            { FLOWER_FOREST, PLAINS, FOREST, BIRCH_FOREST, DARK_FOREST }, { SAVANNA, SAVANNA, FOREST, JUNGLE, JUNGLE },
            { DESERT, DESERT, DESERT, DESERT, DESERT } };
        MIDDLE_VARIANT = new BiomeGenBase[][] { { ICE_SPIKES, null, SNOWY_TAIGA, null, null },
            { null, null, null, null, OLD_GROWTH_PINE_TAIGA },
            { SUNFLOWER_PLAINS, null, null, OLD_GROWTH_BIRCH_FOREST, null },
            { null, null, PLAINS, SPARSE_JUNGLE, BAMBOO_JUNGLE }, { null, null, null, null, null } };
        PLATEAU = new BiomeGenBase[][] { { SNOWY_PLAINS, SNOWY_PLAINS, SNOWY_PLAINS, SNOWY_TAIGA, SNOWY_TAIGA },
            { MEADOW, MEADOW, FOREST, TAIGA, OLD_GROWTH_SPRUCE_TAIGA }, { MEADOW, MEADOW, MEADOW, MEADOW, DARK_FOREST },
            { SAVANNA_PLATEAU, SAVANNA_PLATEAU, FOREST, FOREST, JUNGLE },
            { BADLANDS, BADLANDS, BADLANDS, WOODED_BADLANDS, WOODED_BADLANDS } };
        PLATEAU_VARIANT = new BiomeGenBase[][] { { ICE_SPIKES, null, null, null, null },
            { CHERRY_GROVE, null, MEADOW, MEADOW, OLD_GROWTH_PINE_TAIGA },
            { CHERRY_GROVE, CHERRY_GROVE, FOREST, BIRCH_FOREST, null }, { null, null, null, null, null },
            { ERODED_BADLANDS, ERODED_BADLANDS, null, null, null } };
        SHATTERED = new BiomeGenBase[][] {
            { WINDSWEPT_GRAVELLY_HILLS, WINDSWEPT_GRAVELLY_HILLS, WINDSWEPT_HILLS, WINDSWEPT_FOREST, WINDSWEPT_FOREST },
            { WINDSWEPT_GRAVELLY_HILLS, WINDSWEPT_GRAVELLY_HILLS, WINDSWEPT_HILLS, WINDSWEPT_FOREST, WINDSWEPT_FOREST },
            { WINDSWEPT_HILLS, WINDSWEPT_HILLS, WINDSWEPT_HILLS, WINDSWEPT_FOREST, WINDSWEPT_FOREST },
            { null, null, null, null, null }, { null, null, null, null, null } };
    }

    private static BiomeGenBase middle(int t, int h, double w) {
        if (w > 0 && MIDDLE_VARIANT[t][h] != null) return MIDDLE_VARIANT[t][h];
        return MIDDLE[t][h];
    }

    private static BiomeGenBase middleOrBadlands(int t, int h, double w) {
        return t == 4 ? badlands(h, w) : middle(t, h, w);
    }

    private static BiomeGenBase badlands(int h, double w) {
        if (h < 2) return w < 0 ? BADLANDS : ERODED_BADLANDS;
        return h < 3 ? BADLANDS : WOODED_BADLANDS;
    }

    private static BiomeGenBase plateau(int t, int h, double w) {
        if (w > 0 && PLATEAU_VARIANT[t][h] != null) return PLATEAU_VARIANT[t][h];
        return PLATEAU[t][h];
    }

    private static BiomeGenBase peak(int t, int h, double w) {
        if (t <= 2) return w < 0 ? JAGGED_PEAKS : FROZEN_PEAKS;
        return t == 3 ? STONY_PEAKS : badlands(h, w);
    }

    private static BiomeGenBase slope(int t, int h, double w) {
        if (t >= 3) return plateau(t, h, w);
        return h <= 1 ? SNOWY_SLOPES : GROVE;
    }

    private static BiomeGenBase shattered(int t, int h, double w) {
        BiomeGenBase b = SHATTERED[t][h];
        return b == null ? middle(t, h, w) : b;
    }

    private static BiomeGenBase beach(int t) {
        return t == 0 ? SNOWY_BEACH : t == 4 ? DESERT : BEACH;
    }

    private static BiomeGenBase river(int t) {
        return t == 0 ? FROZEN_RIVER : RIVER;
    }

    private static BiomeGenBase swampOrMiddle(int t, int h, double w) {
        return t == 0 ? middle(t, h, w) : SWAMP;
    }

    public static BiomeGenBase pick(Climate.Sample s) {
        tables();
        double c = s.continentalness, w = s.weirdness, pv = s.pv;
        int t = level(s.temperature, TEMP);
        int h = level(s.humidity, HUMID);
        int e = level(s.erosion, EROSION);

        if (c < -1.05) return MUSHROOM_FIELDS;
        if (c < -0.455) {
            return switch (t) {
                case 0 -> DEEP_FROZEN_OCEAN;
                case 1 -> COLD_OCEAN;
                case 2 -> DEEP_OCEAN;
                case 3 -> LUKEWARM_OCEAN;
                default -> WARM_OCEAN;
            };
        }
        if (c < -0.19) {
            return switch (t) {
                case 0 -> FROZEN_OCEAN;
                case 1 -> COLD_OCEAN;
                case 2 -> OCEAN;
                case 3 -> LUKEWARM_OCEAN;
                default -> WARM_OCEAN;
            };
        }
        // 0 coast, 1 near inland, 2 mid inland, 3 far inland
        int inland = c < -0.11 ? 0 : c < 0.03 ? 1 : c < 0.3 ? 2 : 3;

        if (pv < -0.85) { // valleys
            if (inland <= 1) return e == 6 ? swampOrMiddle(t, h, w) : river(t);
            if (e <= 1) return middleOrBadlands(t, h, w);
            if (e == 6) return swampOrMiddle(t, h, w);
            return river(t);
        }
        if (pv < -0.6) { // low slice
            if (inland == 0)
                return e <= 2 ? STONY_SHORE : e <= 4 ? beach(t) : e == 6 ? swampOrMiddle(t, h, w) : middle(t, h, w);
            if (e <= 1) return inland == 3 ? slope(t, h, w) : middleOrBadlands(t, h, w);
            if (e == 6) return swampOrMiddle(t, h, w);
            return middleOrBadlands(t, h, w);
        }
        if (pv < 0.2) { // mid slice
            if (inland == 0)
                return e <= 2 ? STONY_SHORE : e <= 4 ? beach(t) : e == 6 ? swampOrMiddle(t, h, w) : middle(t, h, w);
            if (e == 0) return inland >= 2 ? slope(t, h, w) : middleOrBadlands(t, h, w);
            if (e == 1) return inland == 3 ? plateau(t, h, w) : middleOrBadlands(t, h, w);
            if (e == 2) return inland >= 2 ? plateau(t, h, w) : middle(t, h, w);
            if (e == 5) return inland >= 2 && w > 0 ? shattered(t, h, w) : middleOrBadlands(t, h, w);
            if (e == 6) return swampOrMiddle(t, h, w);
            return middleOrBadlands(t, h, w);
        }
        if (pv < 0.7) { // high slice
            if (inland == 0) return middle(t, h, w);
            if (e <= 1) return inland == 3 ? peak(t, h, w) : slope(t, h, w);
            if (e == 2) return inland >= 2 ? plateau(t, h, w) : middle(t, h, w);
            if (e == 5) return shattered(t, h, w);
            return middleOrBadlands(t, h, w);
        }
        // peaks
        if (inland == 0) return e == 0 ? peak(t, h, w) : middle(t, h, w);
        if (e == 0) return peak(t, h, w);
        if (e == 1) return inland >= 2 ? peak(t, h, w) : slope(t, h, w);
        if (e <= 3) return plateau(t, h, w);
        if (e == 5) return shattered(t, h, w);
        return middleOrBadlands(t, h, w);
    }
}
