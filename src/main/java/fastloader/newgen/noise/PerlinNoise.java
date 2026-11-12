package fastloader.newgen.noise;

import java.util.Random;

/**
 * Octave noise in the modern style: a first (lowest frequency) octave index and a list of per-octave
 * amplitudes. Octave i has frequency 2^(firstOctave + i).
 */
public final class PerlinNoise {

    private final ImprovedNoise[] levels;
    private final double[] amplitudes;
    private final double lowestFreqInputFactor;
    private final double lowestFreqValueFactor;
    private final double maxValue;

    public PerlinNoise(Random random, int firstOctave, double... amplitudes) {
        this.amplitudes = amplitudes;
        int n = amplitudes.length;
        this.levels = new ImprovedNoise[n];
        for (int i = 0; i < n; i++) {
            // each octave gets its own derived random so that adding octaves does not reshuffle the others
            Random r = new Random(random.nextLong() ^ (0x9E3779B97F4A7C15L * (i + 1)));
            levels[i] = amplitudes[i] != 0.0 ? new ImprovedNoise(r) : null;
        }
        this.lowestFreqInputFactor = Math.pow(2.0, firstOctave);
        this.lowestFreqValueFactor = Math.pow(2.0, n - 1) / (Math.pow(2.0, n) - 1.0);
        this.maxValue = edgeValue(2.0);
    }

    public double getValue(double x, double y, double z) {
        return getValue(x, y, z, 0.0, 0.0, false);
    }

    public double getValue(double x, double y, double z, double yScale, double yMax, boolean fixedY) {
        double total = 0.0;
        double input = lowestFreqInputFactor;
        double value = lowestFreqValueFactor;
        for (int i = 0; i < levels.length; i++) {
            ImprovedNoise noise = levels[i];
            if (noise != null) {
                double v = noise.noise(
                    NoiseMath.wrap(x * input),
                    fixedY ? -noise.yo : NoiseMath.wrap(y * input),
                    NoiseMath.wrap(z * input),
                    yScale * input,
                    yMax * input);
                total += amplitudes[i] * v * value;
            }
            input *= 2.0;
            value /= 2.0;
        }
        return total;
    }

    public double maxValue() {
        return maxValue;
    }

    private double edgeValue(double d) {
        double total = 0.0;
        double value = lowestFreqValueFactor;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] != null) total += amplitudes[i] * d * value;
            value /= 2.0;
        }
        return total;
    }

    public ImprovedNoise getOctave(int i) {
        return levels[levels.length - 1 - i];
    }

    public int octaves() {
        return levels.length;
    }
}
