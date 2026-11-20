package dev.blockside.core.ski;

public final class SkiParams {
    /** Blocks per second squared. */
    public double gravity = 14;
    public double airDrag = 0.012;
    public double tuckDragFactor = 0.6;
    public double brakeFriction = 0.55;
    /** Degrees per second the skis can swing toward the look direction. */
    public double turnRate = 160;
    /** Fraction of lateral speed recovered as forward speed when carving. */
    public double carveEfficiency = 0.82;
    public double edgeGripBonus = 1.6;
    public double maxSpeed = 32;
    /** Below this speed on a flat bit the skier skates to keep moving. */
    public double skatePush = 2.5;
    public double skateBelow = 4;
}
