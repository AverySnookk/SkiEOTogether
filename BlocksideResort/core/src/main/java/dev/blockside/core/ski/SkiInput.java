package dev.blockside.core.ski;

/**
 * forward = tuck, back = brake (snowplough), left/right = steer, edge = dig edges in for a hard carve.
 * yawDeg is where the skier is looking; skis turn toward it at a grip-limited rate.
 */
public record SkiInput(boolean forward, boolean back, boolean left, boolean right, boolean edge, float yawDeg) {}
