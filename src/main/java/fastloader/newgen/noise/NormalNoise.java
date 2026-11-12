package fastloader.newgen.noise;

import java.util.Random;

/**
 * Two offset octave noises added together, normalised to roughly [-1, 1]. This is the building block of the
 * modern climate parameters (temperature, humidity, continentalness, erosion, weirdness) and of most surface
 * and cave noises.
 */
public final class NormalNoise {

    private static final double INPUT_FACTOR = 1.0181268882175227;

    private final PerlinNoise first;
    private final PerlinNoise second;
    private final double valueFactor;

    public NormalNoise(Random random, int firstOctave, double... amplitudes) {
        this.first = new PerlinNoise(random, firstOctave, amplitudes);
        this.second = new PerlinNoise(random, firstOctave, amplitudes);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = 0; i < amplitudes.length; i++) {
            if (amplitudes[i] != 0.0) {
                min = Math.min(min, i);
                max = Math.max(max, i);
            }
        }
        this.valueFactor = (1.0 / 6.0) / expectedDeviation(max - min);
    }

    /** Convenience: create from a world seed and a name so that every noise is independent but reproducible. */
    public static NormalNoise create(long seed, String name, int firstOctave, double... amplitudes) {
        return new NormalNoise(new Random(seed ^ (long) name.hashCode() * 0x5DEECE66DL), firstOctave, amplitudes);
    }

    private static double expectedDeviation(int octaves) {
        return 0.1 * (1.0 + 1.0 / (octaves + 1));
    }

    public double getValue(double x, double y, double z) {
        double x2 = x * INPUT_FACTOR, y2 = y * INPUT_FACTOR, z2 = z * INPUT_FACTOR;
        return (first.getValue(x, y, z) + second.getValue(x2, y2, z2)) * valueFactor;
    }

    public double getValue2D(double x, double z) {
        return getValue(x, 0.0, z);
    }
}
