package fastloader.newgen.overworld;

import java.util.function.ToDoubleFunction;

import fastloader.newgen.noise.NoiseMath;
import fastloader.newgen.noise.Spline;

/**
 * Terrain shaping splines: continentalness x erosion x peaks-and-valleys -> terrain offset (base height), factor
 * (how strongly the 3D noise is squashed - low factor = rugged, overhanging terrain) and jaggedness (sharp peaks).
 * <p>
 * Height of the terrain surface is roughly {@code 128 + (offset - 0.50375) * 128}, so an offset of 0 sits at the sea
 * level (63), 0.5 at ~127 and 1.0 at ~191.
 */
public final class TerrainShaper {

    public static final double GLOBAL_OFFSET = -0.50375;

    private final Spline<Climate.Sample> offset, factor, jaggedness;

    private static final ToDoubleFunction<Climate.Sample> C = s -> s.continentalness;
    private static final ToDoubleFunction<Climate.Sample> E = s -> s.erosion;
    private static final ToDoubleFunction<Climate.Sample> PV = s -> s.pv;

    public TerrainShaper() {
        offset = Spline.<Climate.Sample>builder(C)
            .point(-1.1, 0.044)
            .point(-1.02, -0.2222)
            .point(-0.51, -0.2222)
            .point(-0.44, -0.12)
            .point(-0.18, -0.12)
            .point(-0.16, inlandOffset(-0.15, 0.0))
            .point(-0.15, inlandOffset(-0.15, 0.0))
            .point(-0.10, inlandOffset(-0.1, 0.1))
            .point(0.25, inlandOffset(-0.1, 0.7))
            .point(1.0, inlandOffset(-0.05, 1.0))
            .build();

        factor = Spline.<Climate.Sample>builder(C)
            .point(-0.19, 3.95)
            .point(-0.15, inlandFactor(6.25, 3.0))
            .point(-0.10, inlandFactor(5.47, 3.6))
            .point(0.03, inlandFactor(5.08, 3.8))
            .point(0.06, inlandFactor(4.69, 4.0))
            .build();

        jaggedness = Spline.<Climate.Sample>builder(C)
            .point(-0.11, 0.0)
            .point(0.03, inlandJagged(0.0, 0.0))
            .point(0.65, inlandJagged(0.5, 0.25))
            .point(1.0, inlandJagged(0.63, 0.3))
            .build();
    }

    /** Highest offset the world can hold (keeps peaks under the 256 block limit, before jaggedness). */
    private static final double CAP = 1.32;

    /** Mountain ridge: valleys between the peaks stay high, peaks reach {@code max}. */
    private static Spline<Climate.Sample> ridge(double max) {
        max = Math.min(max, CAP);
        return Spline.<Climate.Sample>builder(PV)
            .point(-1.0, Math.min(0.05, max))
            .point(-0.85, 0.15 * max + 0.04)
            .point(-0.4, 0.45 * max)
            .point(0.0, 0.65 * max, 0.5)
            .point(0.4, 0.85 * max, 0.5)
            .point(1.0, max)
            .build();
    }

    private static Spline<Climate.Sample> rolling(double valley, double bank, double mid, double peak) {
        return Spline.<Climate.Sample>builder(PV)
            .point(-1.0, valley)
            .point(-0.85, bank)
            .point(-0.4, (bank + mid) * 0.5)
            .point(0.0, mid)
            .point(0.4, (mid + peak) * 0.5)
            .point(1.0, peak)
            .build();
    }

    /**
     * Erosion spline for one continentalness band, structured like the modern one.
     *
     * @param valley offset of river valleys
     * @param peak   "f3": how mountainous this band is (0 coast ... 1 far inland)
     */
    private static Spline<Climate.Sample> inlandOffset(double valley, double peak) {
        return Spline.<Climate.Sample>builder(E)
            .point(-0.8, ridge(NoiseMath.lerp(peak, 0.6, 1.5)))
            .point(-0.55, ridge(NoiseMath.lerp(peak, 0.6, 1.0)))
            .point(-0.35, ridge(Math.max(peak, 0.05)))
            .point(-0.25, rolling(valley - 0.15 * 0.3, 0.01, 0.35 * peak + 0.05, 0.65 * peak + 0.08))
            .point(-0.1, rolling(valley, 0.01, 0.08 + 0.14 * peak, 0.12 + 0.32 * peak))
            .point(0.2, rolling(valley, 0.008, 0.04 + 0.03 * peak, 0.08 + 0.06 * peak))
            .point(0.4, rolling(valley, 0.006, 0.02, 0.04))
            .point(0.45, rolling(valley, 0.006, 0.02, 0.04))
            .point(0.55, rolling(valley * 0.5, 0.01, 0.1 + 0.1 * peak, 0.15 + 0.25 * peak))
            .point(0.58, rolling(valley * 0.5, 0.01, 0.1 + 0.1 * peak, 0.15 + 0.25 * peak))
            .point(0.7, rolling(valley, -0.005, -0.004, 0.01))
            .build();
    }

    private static Spline<Climate.Sample> inlandFactor(double flat, double mountain) {
        return Spline.<Climate.Sample>builder(E)
            .point(-0.6, mountain)
            .point(-0.5, mountain + 0.4)
            .point(-0.35, (mountain + flat) * 0.5)
            .point(-0.25, (mountain + flat) * 0.5)
            .point(-0.1, flat - 0.6)
            .point(0.03, flat - 0.3)
            .point(0.35, flat)
            .point(0.45, flat)
            .point(0.55, 2.8)
            .point(0.58, 2.8)
            .point(0.7, flat)
            .build();
    }

    private static Spline<Climate.Sample> inlandJagged(double high, double mid) {
        Spline<Climate.Sample> peaks = Spline.<Climate.Sample>builder(PV)
            .point(0.19, 0.0)
            .point(0.45, mid * 0.5)
            .point(0.7, mid)
            .point(1.0, high)
            .build();
        return Spline.<Climate.Sample>builder(E)
            .point(-1.0, peaks)
            .point(-0.78, peaks)
            .point(
                -0.5775,
                Spline.<Climate.Sample>builder(PV)
                    .point(0.45, 0.0)
                    .point(1.0, mid * 0.4)
                    .build())
            .point(-0.375, 0.0)
            .build();
    }

    public double offset(Climate.Sample s) {
        return offset.applyAsDouble(s) + GLOBAL_OFFSET;
    }

    public double factor(Climate.Sample s) {
        return factor.applyAsDouble(s);
    }

    public double jaggedness(Climate.Sample s) {
        return Math.max(0.0, jaggedness.applyAsDouble(s));
    }

    /** Approximate terrain height for a column (ignores 3D noise and caves). */
    public static double surfaceHeight(double offset) {
        return 128.0 + offset * 128.0;
    }
}
