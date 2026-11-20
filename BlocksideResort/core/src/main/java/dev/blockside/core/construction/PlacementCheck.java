package dev.blockside.core.construction;

import dev.blockside.core.Pos;

import java.util.ArrayList;
import java.util.List;

/** Result of validating a planned zone: blocking problems (with positions for the preview) and notes. */
public final class PlacementCheck {
    public final List<String> reasons = new ArrayList<>();
    public final List<Pos> blockedAt = new ArrayList<>();
    public final List<String> notes = new ArrayList<>();
    public int cutBlocks;
    public int clearBlocks;
    public int fillBlocks;

    public boolean ok() { return reasons.isEmpty(); }
    public void block(String reason) { if (!reasons.contains(reason)) reasons.add(reason); }
    public void blockAt(String reason, Pos p) { block(reason); if (blockedAt.size() < 64) blockedAt.add(p); }
    public String summary() { return ok() ? "OK" : String.join("; ", reasons); }
}
