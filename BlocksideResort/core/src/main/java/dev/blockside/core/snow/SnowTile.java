package dev.blockside.core.snow;

/**
 * Snowpack column on managed terrain. Mass lives in {@link #swe} (snow water equivalent, m) and
 * {@link #liquid} (retained melt water, m). Visible depth is derived: swe * 1000 / density.
 * Grooming changes density (depth) but never mass, so compaction lowers the surface without losing snow.
 */
public final class SnowTile {
    public int x, z;
    /** Ground top (y of the top face of the terrain). */
    public int ground;
    /** Designed smooth surface height for this column (>= ground). */
    public double design;
    /** Sun exposure 0..1 from slope aspect (south-facing = more). */
    public double sun = 0.5;
    public double swe;
    public double density = 250;
    public double liquid;
    /** Fraction of swe that is refrozen ice crust. */
    public double ice;
    /** 1 = freshly groomed corduroy, decays toward 0. */
    public double groomed;
    /** Last game tick this tile was simulated (for catch-up after being unloaded). */
    public long lastTick;

    public SnowTile() {}
    public SnowTile(int x, int z, int ground, double design) { this.x = x; this.z = z; this.ground = ground; this.design = design; }

    public double depth() { return swe <= 0 ? 0 : swe * 1000.0 / density; }
    public double surface() { return ground + depth(); }
    /** Snow needed to reach the designed surface plus a minimum base. */
    public double neededDepth(SnowParams p) { return Math.max(0, design - ground) + p.minBase; }
    public boolean covered(SnowParams p) { return depth() >= neededDepth(p); }
    public double mass() { return swe + liquid; }

    public SurfaceKind kind() {
        if (depth() < 0.06) return SurfaceKind.BARE;
        if (ice > 0.45) return SurfaceKind.ICE;
        if (groomed > 0.35) return SurfaceKind.GROOMED;
        if (density >= 330) return SurfaceKind.PACKED;
        return SurfaceKind.POWDER;
    }

    /** Adds snow of the given density (mass-weighted density mix). */
    public void addSnow(double addSwe, double addDensity) {
        if (addSwe <= 0) return;
        double oldDepth = depth();
        double addDepth = addSwe * 1000.0 / addDensity;
        swe += addSwe;
        density = swe * 1000.0 / (oldDepth + addDepth);
        ice = ice * (swe - addSwe) / swe;
        if (addDensity < 200) groomed *= 0.3; // fresh powder covers the corduroy
    }

    /** Removes up to amount of SWE, keeping density. Returns the amount removed. */
    public double removeSnow(double amount) {
        double r = Math.min(Math.max(0, amount), swe);
        swe -= r;
        if (swe <= 1e-9) { swe = 0; ice = 0; groomed = 0; }
        return r;
    }
}
