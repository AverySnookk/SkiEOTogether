package dev.blockside.core;

import dev.blockside.core.terrain.Difficulty;

import java.util.ArrayList;
import java.util.List;

public final class Trail {
    public String id;
    public String name;
    public List<int[]> centerline = new ArrayList<>();
    public double width;
    public boolean open;
    public Difficulty difficulty;
    public double avgGradeDeg, maxGradeDeg, length;
    public int flags;
    /** Fraction 0..1 of tiles with enough snow. */
    public double coverage;
    public double groomedFraction;
    public List<Long> tiles = new ArrayList<>();
}
