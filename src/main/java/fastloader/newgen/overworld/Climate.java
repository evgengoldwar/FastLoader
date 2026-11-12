package fastloader.newgen.overworld;

import fastloader.newgen.noise.NormalNoise;

/**
 * The six modern climate parameters sampled at a column. Noise parameters follow the modern overworld settings:
 * temperature (-10), vegetation (-8), continentalness (-9), erosion (-9), ridges/weirdness (-7) and the coordinate
 * shift noise (-3) that warps all of them.
 */
public final class Climate {

    public static final class Sample {

        public double temperature, humidity, continentalness, erosion, weirdness, pv;
    }

    private final NormalNoise temperature, vegetation, continentalness, erosion, ridges, shift;

    public Climate(long seed) {
        temperature = NormalNoise.create(seed, "temperature", -10, 1.5, 0.0, 1.0, 0.0, 0.0, 0.0);
        vegetation = NormalNoise.create(seed, "vegetation", -8, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        continentalness = NormalNoise.create(seed, "continentalness", -9, 1.0, 1.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0);
        erosion = NormalNoise.create(seed, "erosion", -9, 1.0, 1.0, 0.0, 1.0, 1.0);
        ridges = NormalNoise.create(seed, "ridge", -7, 1.0, 2.0, 1.0, 0.0, 0.0, 0.0);
        shift = NormalNoise.create(seed, "offset", -3, 1.0, 1.0, 1.0, 0.0);
    }

    /** Folded weirdness: "peaks and valleys". */
    public static double peaksAndValleys(double weirdness) {
        return -(Math.abs(Math.abs(weirdness) - 0.6666667) - 0.33333334) * 3.0;
    }

    public Sample sample(double x, double z, Sample out) {
        if (out == null) out = new Sample();
        // shift_a / shift_b: 4 * offset noise sampled at quarter resolution
        double sx = x * 0.25 + shift.getValue(x * 0.25, 0, z * 0.25) * 4.0;
        double sz = z * 0.25 + shift.getValue(z * 0.25, x * 0.25, 0) * 4.0;
        out.temperature = temperature.getValue(sx, 0, sz);
        out.humidity = vegetation.getValue(sx, 0, sz);
        out.continentalness = continentalness.getValue(sx, 0, sz);
        out.erosion = erosion.getValue(sx, 0, sz);
        out.weirdness = ridges.getValue(sx, 0, sz);
        out.pv = peaksAndValleys(out.weirdness);
        return out;
    }
}
