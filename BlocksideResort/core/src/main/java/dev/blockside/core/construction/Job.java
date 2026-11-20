package dev.blockside.core.construction;

import dev.blockside.core.Box;
import dev.blockside.core.Pos;
import dev.blockside.core.catalog.Stage;

import java.util.List;

/** A construction zone and its progress. Placements are regenerated deterministically on load. */
public final class Job {
    public String id;
    public String purchaseId;
    public String catalogId;
    public String name;
    public Pos anchor;
    public int rotation;
    public Box zone;
    /** Lift jobs: upper station anchor and the planned tower positions (top of tower). */
    public Pos liftTop;
    public List<Pos> towers;
    public List<Integer> towerBases;
    public JobState state = JobState.PLANNED;
    public int cursor;
    public String crewId;
    public long laborQuote;
    public String blockedReason;
    public long createdAt;
    public long completedAt;

    public transient List<Placement> placements;

    public int total() { return placements == null ? 0 : placements.size(); }

    public double progress() { return total() == 0 ? 0 : (double) cursor / total(); }

    public Stage stage() {
        if (placements == null || placements.isEmpty()) return Stage.SITE_PREP;
        if (cursor >= placements.size()) return Stage.EQUIPMENT;
        return placements.get(cursor).stage();
    }
}
