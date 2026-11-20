package dev.blockside.core.snow;

/** What the skier feels. Shared by the snow renderer (block choice), skier and groomer. */
public enum SurfaceKind {
    GROOMED(0.045, 7.0), PACKED(0.07, 5.5), POWDER(0.14, 4.0), ICE(0.03, 1.6), BARE(0.6, 3.0);
    /** Kinetic friction coefficient and lateral grip (1/s). */
    public final double friction, grip;
    SurfaceKind(double friction, double grip) { this.friction = friction; this.grip = grip; }
    public boolean snow() { return this != BARE; }
}
