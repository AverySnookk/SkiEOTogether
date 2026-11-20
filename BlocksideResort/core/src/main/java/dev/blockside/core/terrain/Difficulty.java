package dev.blockside.core.terrain;

public enum Difficulty {
    BEGINNER("Green circle", 15), INTERMEDIATE("Blue square", 25), ADVANCED("Black diamond", 38),
    EXPERT("Double black", 52), EXTREME("Extreme chute", 90);
    public final String label;
    public final double maxDeg;
    Difficulty(String label, double maxDeg) { this.label = label; this.maxDeg = maxDeg; }

    public static Difficulty forGrade(double deg) {
        for (Difficulty d : values()) if (deg <= d.maxDeg) return d;
        return EXTREME;
    }
}
