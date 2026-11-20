package dev.blockside.core.ski;

public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 UP = new Vec3(0, 1, 0);
    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }
    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
    public Vec3 cross(Vec3 o) { return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public double length() { return Math.sqrt(dot(this)); }
    public double horizontalLength() { return Math.hypot(x, z); }
    public Vec3 normalize() { double l = length(); return l < 1e-9 ? ZERO : scale(1 / l); }
    /** Component lying in the plane with normal n. */
    public Vec3 projectOnPlane(Vec3 n) { return sub(n.scale(dot(n))); }
}
