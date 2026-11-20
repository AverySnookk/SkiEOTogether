package dev.blockside.core.lift;

import java.util.List;

/**
 * The cable loop. s in [0, length): up-line from the lower station to the upper station, a half-turn
 * around the top bullwheel, the down-line, and a half-turn at the bottom. All chairs ride the same loop,
 * spaced evenly, advanced by one server-controlled phase.
 */
public final class LiftLoop {
    private final double[][] up;    // support points (x, y, z) along the up-line, offset to the right
    private final double[][] down;  // same supports reversed, offset to the other side
    private final double[] upCum, downCum;
    private final double upLen, turnLen, length;
    public final double offset;
    private final double fx, fz;

    public LiftLoop(List<double[]> supports, double offset) {
        this.offset = offset;
        int n = supports.size();
        double[] first = supports.get(0), last = supports.get(n - 1);
        double dx = last[0] - first[0], dz = last[2] - first[2];
        double h = Math.hypot(dx, dz);
        double px = h == 0 ? 1 : -dz / h, pz = h == 0 ? 0 : dx / h; // perpendicular (left of travel)
        fx = h == 0 ? 0 : dx / h; fz = h == 0 ? 1 : dz / h;
        up = new double[n][];
        down = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] s = supports.get(i);
            up[i] = new double[]{s[0] - px * offset, s[1], s[2] - pz * offset};
            double[] r = supports.get(n - 1 - i);
            down[i] = new double[]{r[0] + px * offset, r[1], r[2] + pz * offset};
        }
        upCum = new double[n];
        downCum = new double[n];
        for (int i = 1; i < n; i++) {
            upCum[i] = upCum[i - 1] + dist(up[i - 1], up[i]);
            downCum[i] = downCum[i - 1] + dist(down[i - 1], down[i]);
        }
        upLen = upCum[n - 1];
        turnLen = Math.PI * offset;
        length = 2 * upLen + 2 * turnLen;
    }

    public double length() { return length; }
    public double upLength() { return upLen; }

    /** Cable point (x, y, z) at loop distance s. */
    public double[] at(double s) {
        s = ((s % length) + length) % length;
        if (s < upLen) return along(up, upCum, s);
        s -= upLen;
        if (s < turnLen) return turn(up[up.length - 1], down[0], s / turnLen, fx, fz);
        s -= turnLen;
        if (s < upLen) return along(down, downCum, s);
        s -= upLen;
        return turn(down[down.length - 1], up[0], s / turnLen, -fx, -fz);
    }

    /** True when s is on the up-line (riders going up). */
    public boolean ascending(double s) {
        s = ((s % length) + length) % length;
        return s < upLen;
    }

    private static double[] along(double[][] pts, double[] cum, double s) {
        int i = 1;
        while (i < pts.length - 1 && cum[i] < s) i++;
        double segStart = cum[i - 1], segLen = cum[i] - segStart;
        double t = segLen == 0 ? 0 : (s - segStart) / segLen;
        double[] a = pts[i - 1], b = pts[i];
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static double[] turn(double[] a, double[] b, double t, double fx, double fz) {
        double cx = (a[0] + b[0]) / 2, cz = (a[2] + b[2]) / 2;
        double ang0 = Math.atan2(a[2] - cz, a[0] - cx);
        double r = Math.hypot(a[0] - cx, a[2] - cz);
        // Swing around the bullwheel on the outside (beyond the station, along the travel direction).
        double sign = Math.cos(ang0 - Math.PI / 2) * fx + Math.sin(ang0 - Math.PI / 2) * fz > 0 ? -1 : 1;
        double ang = ang0 + sign * Math.PI * t;
        return new double[]{cx + r * Math.cos(ang), a[1] + (b[1] - a[1]) * t, cz + r * Math.sin(ang)};
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }
}
