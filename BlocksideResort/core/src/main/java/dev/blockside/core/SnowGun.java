package dev.blockside.core;

public final class SnowGun {
    public enum Status { OFF, RUNNING, TOO_WARM, NO_WATER, NO_POWER, NO_TERRAIN, NO_FUNDS }
    public Pos pos;
    /** 0 north, 1 east, 2 south, 3 west. */
    public int facing;
    public boolean on;
    public Status status = Status.OFF;
    public double tempC;
    public double madeSwe;

    public String describe() {
        return switch (status) {
            case OFF -> "Off. Right-click to start.";
            case RUNNING -> String.format("Making snow (%.1f°C)", tempC);
            case TOO_WARM -> String.format("Too warm: %.1f°C (needs ≤ cutoff)", tempC);
            case NO_WATER -> "No water: place a water source within 4 blocks (or a pump house later).";
            case NO_POWER -> "No power: give the gun a redstone signal.";
            case NO_TERRAIN -> "Nothing to cover: aim it at a marked trail.";
            case NO_FUNDS -> "Paused: the resort can't pay operating costs.";
        };
    }
}
