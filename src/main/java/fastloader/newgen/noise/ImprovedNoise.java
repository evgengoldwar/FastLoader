package fastloader.newgen.noise;

import java.util.Random;

/**
 * Classic improved Perlin noise (Ken Perlin, 2002) with a random origin offset and the optional
 * "y smear" quantisation used by modern terrain noise to stretch features vertically.
 */
public final class ImprovedNoise {

    private final byte[] p = new byte[256];
    public final double xo, yo, zo;

    public ImprovedNoise(Random random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        for (int i = 0; i < 256; i++) p[i] = (byte) i;
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i);
            byte b = p[i];
            p[i] = p[i + j];
            p[i + j] = b;
        }
    }

    public double noise(double x, double y, double z) {
        return noise(x, y, z, 0.0, 0.0);
    }

    public double noise(double x, double y, double z, double yScale, double yMax) {
        double dx = x + xo, dy = y + yo, dz = z + zo;
        int ix = floor(dx), iy = floor(dy), iz = floor(dz);
        double fx = dx - ix, fy = dy - iy, fz = dz - iz;
        double smear = 0.0;
        if (yScale != 0.0) {
            double t = yMax >= 0.0 && yMax < fy ? yMax : fy;
            smear = Math.floor(t / yScale + 1.0E-7) * yScale;
        }
        return sampleAndLerp(ix, iy, iz, fx, fy - smear, fz, fy);
    }

    private int perm(int i) {
        return p[i & 0xFF] & 0xFF;
    }

    private static double grad(int hash, double x, double y, double z) {
        switch (hash & 15) {
            case 0:
                return x + y;
            case 1:
                return -x + y;
            case 2:
                return x - y;
            case 3:
                return -x - y;
            case 4:
                return x + z;
            case 5:
                return -x + z;
            case 6:
                return x - z;
            case 7:
                return -x - z;
            case 8:
                return y + z;
            case 9:
                return -y + z;
            case 10:
                return y - z;
            case 11:
                return -y - z;
            case 12:
                return y + x;
            case 13:
                return -y + z;
            case 14:
                return y - x;
            default:
                return -y - z;
        }
    }

    private double sampleAndLerp(int x, int y, int z, double fx, double fy, double fz, double fyOrig) {
        int a = perm(x), b = perm(x + 1);
        int aa = perm(a + y), ab = perm(a + y + 1), ba = perm(b + y), bb = perm(b + y + 1);
        double g000 = grad(perm(aa + z), fx, fy, fz);
        double g100 = grad(perm(ba + z), fx - 1, fy, fz);
        double g010 = grad(perm(ab + z), fx, fy - 1, fz);
        double g110 = grad(perm(bb + z), fx - 1, fy - 1, fz);
        double g001 = grad(perm(aa + z + 1), fx, fy, fz - 1);
        double g101 = grad(perm(ba + z + 1), fx - 1, fy, fz - 1);
        double g011 = grad(perm(ab + z + 1), fx, fy - 1, fz - 1);
        double g111 = grad(perm(bb + z + 1), fx - 1, fy - 1, fz - 1);
        double sx = smooth(fx), sy = smooth(fyOrig), sz = smooth(fz);
        return NoiseMath.lerp3(sx, sy, sz, g000, g100, g010, g110, g001, g101, g011, g111);
    }

    private static double smooth(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    static int floor(double d) {
        int i = (int) d;
        return d < i ? i - 1 : i;
    }
}
