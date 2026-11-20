package dev.blockside.core.snow;

/** Gameplay-tunable snow physics. Units: metres (1 block = 1 m), kg/m3, °C, game hours. */
public final class SnowParams {
    public double freshDensity = 100;
    public double machineDensity = 380;
    public double groomedDensity = 450;
    public double maxDensity = 600;
    public double iceDensity = 900;
    /** Natural settling: density gain per hour toward maxDensity. */
    public double settlePerHour = 1.5;
    /** Degree-hour melt factor (m SWE per °C per hour). */
    public double meltPerDegreeHour = 0.0012;
    /** Extra melt multiplier for sun-facing slopes at full exposure. */
    public double sunMeltBonus = 0.8;
    /** Fraction of SWE that can be held as liquid water before it runs off. */
    public double liquidHoldFraction = 0.08;
    /** Refreeze rate (m water per °C below zero per hour). */
    public double refreezePerDegreeHour = 0.002;
    /** Grooming: how fast freshness decays per hour (1 → 0). */
    public double groomDecayPerHour = 0.04;
    /** Grooming: max SWE moved per tile per pass. */
    public double groomMovePerPass = 0.03;
    /** Base depth (m) required above the designed fill for a tile to count as covered. */
    public double minBase = 0.25;
    /** Deepest visible snow rendered as blocks above ground. */
    public double maxVisibleDepth = 2.0;
}
