package dev.blockside.core.economy;

/** A named input to reputation, scored 0..100 with a weight. */
public record ReputationFactor(String id, String label, double score, double weight, String hint) {
    /** Positive = helping (above neutral 50), negative = hurting. */
    public double impact() { return (score - 50.0) * weight; }
}
