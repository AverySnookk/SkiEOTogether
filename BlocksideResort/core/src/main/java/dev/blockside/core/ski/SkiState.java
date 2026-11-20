package dev.blockside.core.ski;

public final class SkiState {
    /** Velocity in blocks per second. */
    public Vec3 velocity = Vec3.ZERO;
    /** Ski heading (degrees, MC yaw convention: 0 = +z/south, 90 = -x/west). */
    public double headingDeg;
    public boolean airborne;
    public double airTime;
    public boolean crashed;
}
