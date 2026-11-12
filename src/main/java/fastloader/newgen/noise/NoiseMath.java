package fastloader.newgen.noise;

public final class NoiseMath {

    private NoiseMath() {}

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    public static double lerp2(double tx, double ty, double a, double b, double c, double d) {
        return lerp(ty, lerp(tx, a, b), lerp(tx, c, d));
    }

    public static double lerp3(double tx, double ty, double tz, double a, double b, double c, double d, double e,
        double f, double g, double h) {
        return lerp(tz, lerp2(tx, ty, a, b, c, d), lerp2(tx, ty, e, f, g, h));
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** Linear map of v from [fromA, fromB] to [toA, toB], clamped to the target range. */
    public static double clampedMap(double v, double fromA, double fromB, double toA, double toB) {
        double t = clamp((v - fromA) / (fromB - fromA), 0.0, 1.0);
        return lerp(t, toA, toB);
    }

    public static double map(double v, double fromA, double fromB, double toA, double toB) {
        return lerp((v - fromA) / (fromB - fromA), toA, toB);
    }

    /** Keeps large coordinates precise for noise sampling. */
    public static double wrap(double d) {
        return d - (double) lfloor(d / 3.3554432E7 + 0.5) * 3.3554432E7;
    }

    public static long lfloor(double d) {
        long l = (long) d;
        return d < l ? l - 1L : l;
    }

    public static int floor(double d) {
        int i = (int) d;
        return d < i ? i - 1 : i;
    }

    /** Soft squash used by modern terrain to keep densities in a sane range. */
    public static double squeeze(double d) {
        double c = clamp(d, -1.0, 1.0);
        return c / 2.0 - c * c * c / 24.0;
    }

    public static double smoothstep(double t) {
        t = clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Positional hash used for deterministic per-block randomness. */
    public static long hash(long seed, int x, int y, int z) {
        long l = (x * 3129871L) ^ (z * 116129781L) ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return (l >> 16) ^ seed;
    }
}
