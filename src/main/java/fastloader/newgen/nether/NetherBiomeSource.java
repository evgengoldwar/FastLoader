package fastloader.newgen.nether;

import net.minecraft.world.biome.BiomeGenBase;

import fastloader.newgen.noise.NormalNoise;
import fastloader.newgen.world.NoiseBiomeSource;

/**
 * Modern multi-noise Nether biome source: two climate noises (temperature and vegetation) and five biome points.
 * The closest point (squared distance, plus squared "offset") wins - this is how modern versions place Nether
 * biomes, which gives big organic regions instead of the blocky layer-stack look.
 */
public class NetherBiomeSource extends NoiseBiomeSource {

    private final NormalNoise temperature;
    private final NormalNoise vegetation;

    private static final class Point {

        final double t, h, offset;
        final BiomeGenBase biome;

        Point(double t, double h, double offset, BiomeGenBase biome) {
            this.t = t;
            this.h = h;
            this.offset = offset;
            this.biome = biome;
        }
    }

    private final Point[] points;

    public NetherBiomeSource(long seed) {
        temperature = NormalNoise.create(seed, "nether_temperature", -7, 1.0, 1.0);
        vegetation = NormalNoise.create(seed, "nether_vegetation", -7, 1.0, 1.0);
        points = new Point[] { new Point(0.0, 0.0, 0.0, NetherBiomes.NETHER_WASTES),
            new Point(0.0, -0.5, 0.0, NetherBiomes.SOUL_SAND_VALLEY),
            new Point(0.4, 0.0, 0.0, NetherBiomes.CRIMSON_FOREST),
            new Point(0.0, 0.5, 0.375, NetherBiomes.WARPED_FOREST),
            new Point(-0.5, 0.0, 0.175, NetherBiomes.BASALT_DELTAS) };
    }

    @Override
    protected BiomeGenBase computeBiome(int qx, int qz) {
        double bx = qx * 4 + 2, bz = qz * 4 + 2;
        double t = temperature.getValue(bx * 0.25, 0, bz * 0.25);
        double h = vegetation.getValue(bx * 0.25, 0, bz * 0.25);
        Point best = points[0];
        double bestD = Double.MAX_VALUE;
        for (Point p : points) {
            double d = (t - p.t) * (t - p.t) + (h - p.h) * (h - p.h) + p.offset * p.offset;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best.biome;
    }
}
