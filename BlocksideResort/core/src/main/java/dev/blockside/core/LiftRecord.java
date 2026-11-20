package dev.blockside.core;

import dev.blockside.core.lift.LiftLoop;
import dev.blockside.core.lift.LiftParams;

import java.util.ArrayList;
import java.util.List;

/** A completed lift and its live operating state. */
public final class LiftRecord {
    public String id;
    public String catalogId;
    public String name;
    public Pos lower, upper;
    public List<Pos> towers = new ArrayList<>();
    public boolean open;
    public double speed = 2.5;
    public int seats = 2;
    public double spacing = 7;
    /** Loop phase in blocks (advances speed/20 per tick while open). */
    public double phase;
    public double guestQueue;
    public double waitMinutes;
    public double openHours, downHours;
    public long ridesToday;
    public transient LiftLoop loop;

    public LiftLoop loop(LiftParams p) {
        if (loop == null) {
            List<double[]> s = new ArrayList<>();
            s.add(new double[]{lower.x() + 0.5, lower.y() + p.stationCableHeight, lower.z() + 0.5});
            for (Pos t : towers) s.add(new double[]{t.x() + 0.5, t.y(), t.z() + 0.5});
            s.add(new double[]{upper.x() + 0.5, upper.y() + p.stationCableHeight, upper.z() + 0.5});
            loop = new LiftLoop(s, p.lineOffset);
        }
        return loop;
    }

    public int chairCount(LiftParams p) { return Math.max(2, (int) Math.floor(loop(p).length() / spacing)); }

    /** Simulated riders per game hour (catalog capacity; the aggregate guest model's scale). */
    public int capacity = 120;
}
