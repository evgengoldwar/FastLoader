package fastloader.newgen.noise;

import java.util.Random;

/**
 * The classic "min/max limit + selector" 3D terrain noise. Modern versions still use it as the base 3D noise of
 * the Nether (and blended into overworld terrain). Arguments mirror the modern parameters: xz/y scale, xz/y factor
 * and smear.
 */
public final class BlendedNoise {

    private final PerlinNoise minLimit;
    private final PerlinNoise maxLimit;
    private final PerlinNoise main;
    private final double xzMultiplier, yMultiplier, xzFactor, yFactor, smearScaleMultiplier;

    public BlendedNoise(Random random, double xzScale, double yScale, double xzFactor, double yFactor, double smear) {
        this.minLimit = new PerlinNoise(random, -15, ones(16));
        this.maxLimit = new PerlinNoise(random, -15, ones(16));
        this.main = new PerlinNoise(random, -7, ones(8));
        this.xzMultiplier = 684.412 * xzScale;
        this.yMultiplier = 684.412 * yScale;
        this.xzFactor = xzFactor;
        this.yFactor = yFactor;
        this.smearScaleMultiplier = smear;
    }

    private static double[] ones(int n) {
        double[] d = new double[n];
        java.util.Arrays.fill(d, 1.0);
        return d;
    }

    public double compute(double bx, double by, double bz) {
        double d0 = bx * xzMultiplier;
        double d1 = by * yMultiplier;
        double d2 = bz * xzMultiplier;
        double d3 = d0 / xzFactor;
        double d4 = d1 / yFactor;
        double d5 = d2 / xzFactor;
        double d6 = yMultiplier * smearScaleMultiplier;
        double d7 = d6 / yFactor;

        double sum = 0.0;
        double f = 1.0;
        for (int i = 0; i < 8; i++) {
            ImprovedNoise n = main.getOctave(i);
            if (n != null) {
                sum += n.noise(NoiseMath.wrap(d3 * f), NoiseMath.wrap(d4 * f), NoiseMath.wrap(d5 * f), d7 * f, d4 * f)
                    / f;
            }
            f /= 2.0;
        }
        double sel = (sum / 10.0 + 1.0) / 2.0;
        boolean onlyMax = sel >= 1.0;
        boolean onlyMin = sel <= 0.0;
        double min = 0.0, max = 0.0;
        f = 1.0;
        for (int i = 0; i < 16; i++) {
            double x = NoiseMath.wrap(d0 * f), y = NoiseMath.wrap(d1 * f), z = NoiseMath.wrap(d2 * f);
            double ys = d6 * f;
            if (!onlyMax) {
                ImprovedNoise n = minLimit.getOctave(i);
                if (n != null) min += n.noise(x, y, z, ys, d1 * f) / f;
            }
            if (!onlyMin) {
                ImprovedNoise n = maxLimit.getOctave(i);
                if (n != null) max += n.noise(x, y, z, ys, d1 * f) / f;
            }
            f /= 2.0;
        }
        return NoiseMath.lerp(NoiseMath.clamp(sel, 0.0, 1.0), min / 512.0, max / 512.0) / 128.0;
    }
}
