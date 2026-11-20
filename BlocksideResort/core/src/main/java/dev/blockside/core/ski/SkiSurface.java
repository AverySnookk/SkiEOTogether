package dev.blockside.core.ski;

import dev.blockside.core.snow.SurfaceKind;

/**
 * The one shared smooth surface. Heights of the four column centres around a point are interpolated
 * bilinearly, so a staircase of snow-filled blocks becomes a continuous plane; real walls (height
 * jumps larger than {@link #MAX_BLEND_STEP}) are not smoothed over.
 */
public final class SkiSurface {
    public static final double MAX_BLEND_STEP = 1.1;
    private SkiSurface() {}

    public static SurfacePoint sample(SurfaceSampler s, double x, double z, double nearY) {
        int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
        double own = s.columnTop(cx, cz, nearY);
        if (Double.isNaN(own)) return null;
        double gx = x - 0.5, gz = z - 0.5;
        int x0 = (int) Math.floor(gx), z0 = (int) Math.floor(gz);
        double fx = gx - x0, fz = gz - z0;
        double h00 = corner(s, x0, z0, own, nearY), h10 = corner(s, x0 + 1, z0, own, nearY);
        double h01 = corner(s, x0, z0 + 1, own, nearY), h11 = corner(s, x0 + 1, z0 + 1, own, nearY);
        double h = lerp(lerp(h00, h10, fx), lerp(h01, h11, fx), fz);
        double dhdx = lerp(h10 - h00, h11 - h01, fz);
        double dhdz = lerp(h01 - h00, h11 - h10, fx);
        Vec3 n = new Vec3(-dhdx, 1, -dhdz).normalize();
        return new SurfacePoint(h, n, s.kind(cx, cz, nearY));
    }

    private static double corner(SurfaceSampler s, int x, int z, double own, double nearY) {
        double h = s.columnTop(x, z, nearY);
        if (Double.isNaN(h) || Math.abs(h - own) > MAX_BLEND_STEP) return own;
        return h;
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /** Grade in degrees from a surface normal. */
    public static double gradeDeg(Vec3 n) { return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, n.y())))); }

    public static SurfaceKind kindOrBare(SurfacePoint p) { return p == null ? SurfaceKind.BARE : p.kind(); }
}
