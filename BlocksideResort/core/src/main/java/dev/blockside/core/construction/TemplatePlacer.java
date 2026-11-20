package dev.blockside.core.construction;

import dev.blockside.core.Box;
import dev.blockside.core.catalog.Stage;
import dev.blockside.core.catalog.Template;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Turns a template + anchor + rotation into an ordered, deterministic placement list. */
public final class TemplatePlacer {
    private TemplatePlacer() {}

    /** World footprint volume of the template (foundation layer to roof). */
    public static Box footprint(Template t, int ax, int ay, int az, int rot) {
        int[] a = t.anchor();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] c : new int[][]{{0, 0}, {t.sx() - 1, 0}, {0, t.sz() - 1}, {t.sx() - 1, t.sz() - 1}}) {
            int[] r = Rotations.rotate(c[0] - a[0], c[1] - a[2], rot);
            minX = Math.min(minX, ax + r[0]); maxX = Math.max(maxX, ax + r[0]);
            minZ = Math.min(minZ, az + r[1]); maxZ = Math.max(maxZ, az + r[1]);
        }
        int y0 = ay - a[1];
        return new Box(minX, y0, minZ, maxX, y0 + t.sy() - 1, maxZ);
    }

    public static int[] toWorld(Template t, int[] cell, int ax, int ay, int az, int rot) {
        int[] a = t.anchor();
        int[] r = Rotations.rotate(cell[0] - a[0], cell[2] - a[2], rot);
        return new int[]{ax + r[0], ay + (cell[1] - a[1]), az + r[1]};
    }

    public static List<Placement> placements(Template t, int ax, int ay, int az, int rot) {
        List<Placement> out = new ArrayList<>();
        int[] a = t.anchor();
        Box fp = footprint(t, ax, ay, az, rot);
        // Site prep: clear the whole volume above the foundation (natural blocks / one layer of terrain).
        for (int y = 1; y < t.sy(); y++) for (int z = 0; z < t.sz(); z++) for (int x = 0; x < t.sx(); x++) {
            char c = t.at(x, y, z);
            if (c == ' ' || c == '@') continue;
            int[] w = toWorld(t, new int[]{x, y, z}, ax, ay, az, rot);
            out.add(new Placement(w[0], w[1], w[2], "@clear", Stage.SITE_PREP, rot));
        }
        // Support fill under the foundation where the ground dips.
        for (int z = 0; z < t.sz(); z++) for (int x = 0; x < t.sx(); x++) {
            if (t.at(x, 0, z) == ' ' || t.at(x, 0, z) == '.') continue;
            int[] w = toWorld(t, new int[]{x, 0, z}, ax, ay, az, rot);
            for (int d = 2; d >= 1; d--) out.add(new Placement(w[0], w[1] - d, w[2], "?minecraft:dirt", Stage.SITE_PREP, rot));
        }
        for (int y = 0; y < t.sy(); y++) for (int z = 0; z < t.sz(); z++) for (int x = 0; x < t.sx(); x++) {
            char c = t.at(x, y, z);
            if (c == ' ' || c == '.' || c == '@') continue;
            Template.PaletteEntry p = t.palette.get(String.valueOf(c));
            int[] w = toWorld(t, new int[]{x, y, z}, ax, ay, az, rot);
            Stage st = y == 0 ? Stage.FOUNDATION : (p.stage == Stage.FOUNDATION ? Stage.STRUCTURE : p.stage);
            if (p.stage == Stage.EQUIPMENT) st = Stage.EQUIPMENT;
            out.add(new Placement(w[0], w[1], w[2], p.block, st, rot));
        }
        // Stable order: by stage, then bottom-up, then z/x, so crews visibly build layer by layer.
        out.sort(Comparator.comparingInt((Placement p) -> p.stage().ordinal())
                .thenComparingInt(p -> p.stage() == Stage.SITE_PREP ? -p.y() : p.y())
                .thenComparingInt(Placement::z).thenComparingInt(Placement::x));
        assert out.stream().allMatch(p -> fp.grow(0, 2).contains(p.x(), p.y(), p.z()));
        return out;
    }
}
