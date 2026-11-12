package fastloader.newgen.noise;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Cubic Hermite spline over a coordinate extracted from a context object. Points can hold constant values or nested
 * splines, exactly like the multi-dimensional terrain shaping splines of modern terrain generation.
 */
public final class Spline<C> implements ToDoubleFunction<C> {

    private final ToDoubleFunction<C> coordinate;
    private final double[] locations;
    private final List<ToDoubleFunction<C>> values;
    private final double[] derivatives;

    private Spline(ToDoubleFunction<C> coordinate, double[] locations, List<ToDoubleFunction<C>> values,
        double[] derivatives) {
        this.coordinate = coordinate;
        this.locations = locations;
        this.values = values;
        this.derivatives = derivatives;
    }

    public static <C> Builder<C> builder(ToDoubleFunction<C> coordinate) {
        return new Builder<>(coordinate);
    }

    @Override
    public double applyAsDouble(C ctx) {
        double x = coordinate.applyAsDouble(ctx);
        int n = locations.length;
        int i = findInterval(x);
        int last = n - 1;
        if (i < 0) {
            return values.get(0)
                .applyAsDouble(ctx) + derivatives[0] * (x - locations[0]);
        }
        if (i == last) {
            return values.get(last)
                .applyAsDouble(ctx) + derivatives[last] * (x - locations[last]);
        }
        double x0 = locations[i], x1 = locations[i + 1];
        double t = (x - x0) / (x1 - x0);
        double y0 = values.get(i)
            .applyAsDouble(ctx);
        double y1 = values.get(i + 1)
            .applyAsDouble(ctx);
        double d0 = derivatives[i], d1 = derivatives[i + 1];
        double a = d0 * (x1 - x0) - (y1 - y0);
        double b = -d1 * (x1 - x0) + (y1 - y0);
        return NoiseMath.lerp(t, y0, y1) + t * (1.0 - t) * NoiseMath.lerp(t, a, b);
    }

    private int findInterval(double x) {
        int lo = 0, hi = locations.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (x < locations[mid]) hi = mid;
            else lo = mid + 1;
        }
        return lo - 1;
    }

    public static final class Builder<C> {

        private final ToDoubleFunction<C> coordinate;
        private final List<Double> locs = new ArrayList<>();
        private final List<ToDoubleFunction<C>> vals = new ArrayList<>();
        private final List<Double> ders = new ArrayList<>();

        Builder(ToDoubleFunction<C> coordinate) {
            this.coordinate = coordinate;
        }

        public Builder<C> point(double location, double value) {
            return point(location, value, 0.0);
        }

        public Builder<C> point(double location, double value, double derivative) {
            final double v = value;
            return point(location, c -> v, derivative);
        }

        public Builder<C> point(double location, ToDoubleFunction<C> value) {
            return point(location, value, 0.0);
        }

        public Builder<C> point(double location, ToDoubleFunction<C> value, double derivative) {
            locs.add(location);
            vals.add(value);
            ders.add(derivative);
            return this;
        }

        public Spline<C> build() {
            double[] l = new double[locs.size()];
            double[] d = new double[ders.size()];
            for (int i = 0; i < l.length; i++) {
                l[i] = locs.get(i);
                d[i] = ders.get(i);
            }
            return new Spline<>(coordinate, l, new ArrayList<>(vals), d);
        }
    }
}
