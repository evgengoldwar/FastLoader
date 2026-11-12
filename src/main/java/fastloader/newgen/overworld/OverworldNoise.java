package fastloader.newgen.overworld;

import java.util.Random;

import fastloader.Config;
import fastloader.newgen.noise.BlendedNoise;
import fastloader.newgen.noise.NoiseMath;
import fastloader.newgen.noise.NormalNoise;

/**
 * The overworld density function ("noise router"): sloped terrain from the climate splines plus 3D noise, carved by
 * cheese caves, spaghetti caves, cave entrances and noodle caves.
 */
public final class OverworldNoise {

    public static final int MIN_Y = 0, MAX_Y = 256;

    private final BlendedNoise base;
    private final NormalNoise jagged;
    private final NormalNoise cheese, caveLayer, entrance, spaghetti1, spaghetti2, spaghettiRarity, spaghettiThickness,
        roughness, roughnessMod, noodle, noodleThickness, noodleA, noodleB, pillar, pillarRareness, pillarThickness;

    public OverworldNoise(long seed) {
        base = new BlendedNoise(new Random(seed ^ 0x0BADC0DEL), 0.25, 0.125, 80.0, 160.0, 8.0);
        jagged = NormalNoise.create(seed, "jagged", -16, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        cheese = NormalNoise.create(seed, "cave_cheese", -8, 0.5, 1, 2, 1, 2, 1, 0, 2, 0);
        caveLayer = NormalNoise.create(seed, "cave_layer", -8, 1);
        entrance = NormalNoise.create(seed, "cave_entrance", -7, 0.4, 0.5, 1);
        spaghetti1 = NormalNoise.create(seed, "spaghetti_3d_1", -7, 1);
        spaghetti2 = NormalNoise.create(seed, "spaghetti_3d_2", -7, 1);
        spaghettiRarity = NormalNoise.create(seed, "spaghetti_3d_rarity", -11, 1);
        spaghettiThickness = NormalNoise.create(seed, "spaghetti_3d_thickness", -8, 1);
        roughness = NormalNoise.create(seed, "spaghetti_roughness", -5, 1);
        roughnessMod = NormalNoise.create(seed, "spaghetti_roughness_modulator", -8, 1);
        noodle = NormalNoise.create(seed, "noodle", -8, 1);
        noodleThickness = NormalNoise.create(seed, "noodle_thickness", -8, 1);
        noodleA = NormalNoise.create(seed, "noodle_ridge_a", -7, 1);
        noodleB = NormalNoise.create(seed, "noodle_ridge_b", -7, 1);
        pillar = NormalNoise.create(seed, "pillar", -7, 1, 1);
        pillarRareness = NormalNoise.create(seed, "pillar_rareness", -8, 1);
        pillarThickness = NormalNoise.create(seed, "pillar_thickness", -8, 1);
    }

    /** Column terrain parameters, computed once per (x, z). */
    public static final class Column {

        public double offset, factor, jaggedness, jaggedNoise;
    }

    public void column(TerrainShaper shaper, Climate.Sample s, double x, double z, Column out) {
        out.offset = shaper.offset(s);
        out.factor = shaper.factor(s);
        out.jaggedness = shaper.jaggedness(s);
        out.jaggedNoise = out.jaggedness > 0 ? jagged.getValue(x * 1500.0, 0, z * 1500.0) : 0;
    }

    private static double quarterNegative(double d) {
        return d < 0 ? d * 0.25 : d;
    }

    private static double halfNegative(double d) {
        return d < 0 ? d * 0.5 : d;
    }

    /** Rarity scaling used by spaghetti caves: larger value = rarer and wider tunnels. */
    private static double rarity3d(double d) {
        if (d < -0.5) return 0.75;
        if (d < 0.0) return 1.0;
        if (d < 0.5) return 1.5;
        return 2.0;
    }

    public double slopedCheese(Column c, int x, int y, int z) {
        double depth = (128.0 - y) / 128.0 + c.offset;
        double jag = c.jaggedness * halfNegative(c.jaggedNoise);
        return 4.0 * quarterNegative((depth + jag) * c.factor) + base.compute(x, y, z);
    }

    public double density(Column c, int x, int y, int z) {
        double sloped = slopedCheese(c, x, y, z);
        double d;
        if (!Config.overworldCaves) {
            d = sloped;
        } else {
            double spaghettiRough = spaghettiRoughness(x, y, z);
            double entrances = Math.min(caveEntrance(x, y, z), spaghetti3d(x, y, z) + spaghettiRough);
            if (sloped < 1.5625) {
                d = Math.min(sloped, 5.0 * entrances);
            } else {
                double layer = caveLayer.getValue(x, y * 8.0, z);
                double cheeseD = 4.0 * layer * layer + NoiseMath.clamp(0.27 + cheese.getValue(x, y / 1.5, z), -1, 1)
                    + NoiseMath.clamp(1.5 - 0.64 * sloped, 0.0, 0.5);
                d = Math.min(cheeseD, entrances);
                d = Math.max(d, pillars(x, y, z));
            }
            d = Math.min(d, noodle(x, y, z));
        }
        // slides: soften the top of the world and seal the bottom
        d = NoiseMath.lerp(NoiseMath.clampedMap(y, MAX_Y - 16, MAX_Y, 1, 0), -0.078125, d);
        d = NoiseMath.lerp(NoiseMath.clampedMap(y, MIN_Y, MIN_Y + 8, 0, 1), 0.1171875, d);
        return d;
    }

    private double caveEntrance(int x, int y, int z) {
        double e = 0.37 + entrance.getValue(x * 0.75, y * 0.5, z * 0.75);
        double grad = NoiseMath.clampedMap(y, -10, 30, 0.3, 0.0);
        return e + grad;
    }

    private double spaghetti3d(int x, int y, int z) {
        double r = rarity3d(spaghettiRarity.getValue(x * 2.0, y, z * 2.0));
        double thick = NoiseMath.map(spaghettiThickness.getValue(x, y, z), -1, 1, -0.065, -0.088);
        double a = r * Math.abs(spaghetti1.getValue(x / r, y / r, z / r));
        double b = r * Math.abs(spaghetti2.getValue(x / r, y / r, z / r));
        return NoiseMath.clamp(Math.max(a, b) + thick, -1, 1);
    }

    private double spaghettiRoughness(int x, int y, int z) {
        double mod = NoiseMath.map(roughnessMod.getValue(x, y, z), -1, 1, 0, -0.1);
        return (Math.abs(roughness.getValue(x, y, z)) - 0.4) * mod;
    }

    private double noodle(int x, int y, int z) {
        if (y < MIN_Y + 4) return 64.0;
        if (noodle.getValue(x, y, z) < 0) return 64.0;
        double thick = NoiseMath.map(noodleThickness.getValue(x, y, z), -1, 1, -0.05, -0.1);
        double s = 2.6666666666666665;
        double a = Math.abs(noodleA.getValue(x * s, y * s, z * s));
        double b = Math.abs(noodleB.getValue(x * s, y * s, z * s));
        return thick + 1.5 * Math.max(a, b);
    }

    private double pillars(int x, int y, int z) {
        double p = pillar.getValue(x * 25.0, y * 0.3, z * 25.0);
        double rare = NoiseMath.map(pillarRareness.getValue(x, y, z), -1, 1, 0, -2);
        double thick = NoiseMath.map(pillarThickness.getValue(x, y, z), -1, 1, 0, 1.1);
        return (p * 2.0 + rare) * thick * thick * thick;
    }
}
