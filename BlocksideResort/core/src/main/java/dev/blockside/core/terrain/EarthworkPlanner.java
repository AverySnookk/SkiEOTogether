package dev.blockside.core.terrain;

import dev.blockside.core.Pos;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out which blocks a trail job changes. The world adapter supplies what's there; the planner
 * stays bounded to the corridor columns and a fixed height window.
 */
public final class EarthworkPlanner {
    public enum Mode { CLEAR, FIX, GRADE }

    public interface World {
        /** Block spec at a position. */
        String spec(int x, int y, int z);
        boolean isTree(String spec);
        boolean isVegetation(String spec);
        boolean isAir(String spec);
    }

    public int clearHeight = 24;
    public String fillSpec = "minecraft:dirt";

    public EditJournal plan(TrailDesign.Result design, Mode mode, World w) {
        EditJournal j = new EditJournal();
        List<EditJournal.Edit> clears = new ArrayList<>(), cuts = new ArrayList<>(), fills = new ArrayList<>();
        // Linear grade profile for GRADE mode: fit design height vs station.
        double[] fit = fit(design);
        for (TrailDesign.Column c : design.columns.values()) {
            for (int y = c.ground; y < c.ground + clearHeight; y++) {
                String s = w.spec(c.x, y, c.z);
                if (w.isTree(s) || w.isVegetation(s)) clears.add(new EditJournal.Edit(c.x, y, c.z, s, "minecraft:air"));
            }
            int target = c.ground;
            if (mode == Mode.FIX) {
                if (c.design - c.ground > 1.25) target = (int) Math.ceil(c.design - 1.0);
            }
            if (mode == Mode.GRADE) target = (int) Math.round(fit[0] + fit[1] * c.station);
            if (mode == Mode.FIX || mode == Mode.GRADE) {
                for (int y = c.ground - 1; y >= target && y > c.ground - 12; y--) {
                    String s = w.spec(c.x, y, c.z);
                    if (!w.isAir(s)) cuts.add(new EditJournal.Edit(c.x, y, c.z, s, "minecraft:air"));
                }
                for (int y = c.ground; y < target && y < c.ground + 12; y++) {
                    String s = w.spec(c.x, y, c.z);
                    if (w.isAir(s) || w.isVegetation(s)) fills.add(new EditJournal.Edit(c.x, y, c.z, s, fillSpec));
                }
            }
        }
        // PROTRUDING flags in FIX mode: knock the column down to its neighbours.
        if (mode == Mode.FIX) for (TrailFlag f : design.flags) if (f.type() == TrailFlag.Type.PROTRUDING) {
            Pos p = f.at();
            TrailDesign.Column c = design.columns.get(Pos.columnKey(p.x(), p.z()));
            int target = (int) Math.ceil(c.design - 1.0);
            for (int y = c.ground - 1; y >= target; y--) {
                String s = w.spec(c.x, y, c.z);
                if (!w.isAir(s)) cuts.add(new EditJournal.Edit(c.x, y, c.z, s, "minecraft:air"));
            }
        }
        // Clear top-down, cut top-down, fill bottom-up.
        clears.sort((a, b) -> b.y() - a.y());
        cuts.sort((a, b) -> b.y() - a.y());
        fills.sort((a, b) -> a.y() - b.y());
        j.edits.addAll(clears);
        j.edits.addAll(cuts);
        j.edits.addAll(fills);
        return j;
    }

    private static double[] fit(TrailDesign.Result d) {
        double n = 0, sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (TrailDesign.Column c : d.columns.values()) { n++; sx += c.station; sy += c.ground; sxx += c.station * c.station; sxy += c.station * c.ground; }
        double den = n * sxx - sx * sx;
        double b = den == 0 ? 0 : (n * sxy - sx * sy) / den;
        double a = n == 0 ? 0 : (sy - b * sx) / n;
        return new double[]{a, b};
    }
}
