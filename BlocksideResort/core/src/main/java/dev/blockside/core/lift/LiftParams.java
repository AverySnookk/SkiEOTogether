package dev.blockside.core.lift;

public final class LiftParams {
    public int towerSpacing = 14;
    public int towerHeight = 7;
    public int maxTowerHeight = 15;
    /** Cable height above the station anchor (the job block sits on the ground). */
    public int stationCableHeight = 4;
    /** Distance from cable down to the seat. */
    public double hang = 2.5;
    /** Required gap between seat bottom and terrain. */
    public double minClearance = 1.2;
    /** Max cable angle in degrees. */
    public double maxAngle = 40;
    public int minLength = 12;
    /** Half the distance between up and down lines. */
    public double lineOffset = 1.5;
}
