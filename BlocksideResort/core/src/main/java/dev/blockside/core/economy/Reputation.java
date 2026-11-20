package dev.blockside.core.economy;

import java.util.ArrayList;
import java.util.List;

/**
 * Guest satisfaction 0..100. Each update blends the weighted factor score into the current value
 * so reputation moves gradually and the player can see which factors push it up or down.
 */
public final class Reputation {
    private double value;
    private List<ReputationFactor> lastFactors = new ArrayList<>();

    public Reputation(double initial) { this.value = initial; }

    public double value() { return value; }
    public List<ReputationFactor> factors() { return List.copyOf(lastFactors); }

    /** Target score from factors (weighted mean). */
    public static double target(List<ReputationFactor> factors) {
        double w = 0, s = 0;
        for (ReputationFactor f : factors) { w += f.weight(); s += f.score() * f.weight(); }
        return w <= 0 ? 50 : s / w;
    }

    /** @param blend 0..1 how far to move toward the target this step */
    public void update(List<ReputationFactor> factors, double blend) {
        lastFactors = new ArrayList<>(factors);
        double t = target(factors);
        value = clamp(value + (t - value) * clamp(blend, 0, 1), 0, 100);
    }

    public void set(double v) { value = clamp(v, 0, 100); }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
