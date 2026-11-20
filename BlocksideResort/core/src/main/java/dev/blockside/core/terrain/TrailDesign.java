package dev.blockside.core.terrain;

import dev.blockside.core.Pos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a smooth design surface over a marked corridor from elevation samples along and across it.
 * The surface never dips below the ground (snow fills the steps of a block staircase) and stays within
 * {@link #maxFill} of it; anything else is flagged for earthwork.
 */
public final class TrailDesign {
    public double maxFill = 1.25;
    public double cliffGrade = Math.tan(Math.toRadians(58));
    public double protrudeHeight = 1.5;
    public int iterations = 80;

    public static final class Column {
        public final int x, z;
        public int ground;
        public double design;
        /** Distance along the centerline (for the profile view). */
        public double station;
        public double gradeDeg;
        public double sun = 0.5;
        Column(int x, int z, int ground, double station) { this.x = x; this.z = z; this.ground = ground; this.station = station; this.design = ground; }
    }

    public static final class Result {
        public final Map<Long, Column> columns = new HashMap<>();
        public final List<TrailFlag> flags = new ArrayList<>();
        public double length, drop, avgGradeDeg, maxGradeDeg;
        public Difficulty difficulty;
        /** Grade per centerline segment (degrees). */
        public final List<Double> segmentGrades = new ArrayList<>();
        public boolean skiable() { return flags.stream().noneMatch(f -> f.type() != TrailFlag.Type.CLIFF); }
    }

    /** Columns within width/2 of the polyline (x,z pairs). */
    public static List<int[]> corridor(List<int[]> centerline, double width) {
        double half = width / 2.0;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] p : centerline) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]); }
        int pad = (int) Math.ceil(half) + 1;
        List<int[]> out = new ArrayList<>();
        for (int x = minX - pad; x <= maxX + pad; x++) for (int z = minZ - pad; z <= maxZ + pad; z++) {
            double[] d = project(centerline, x + 0.5, z + 0.5);
            if (d[0] <= half) out.add(new int[]{x, z, (int) Math.round(d[1] * 1000)});
        }
        return out;
    }

    /** Returns {distance to polyline, station along it}. */
    static double[] project(List<int[]> line, double px, double pz) {
        double best = Double.MAX_VALUE, bestStation = 0, acc = 0;
        if (line.size() == 1) return new double[]{Math.hypot(px - line.get(0)[0] - 0.5, pz - line.get(0)[1] - 0.5), 0};
        for (int i = 0; i < line.size() - 1; i++) {
            double ax = line.get(i)[0] + 0.5, az = line.get(i)[1] + 0.5, bx = line.get(i + 1)[0] + 0.5, bz = line.get(i + 1)[1] + 0.5;
            double vx = bx - ax, vz = bz - az, len2 = vx * vx + vz * vz, len = Math.sqrt(len2);
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / len2));
            double d = Math.hypot(px - (ax + vx * t), pz - (az + vz * t));
            if (d < best) { best = d; bestStation = acc + t * len; }
            acc += len;
        }
        return new double[]{best, bestStation};
    }

    public Result design(List<int[]> centerline, double width, HeightField terrain) {
        Result r = new Result();
        for (int[] c : corridor(centerline, width)) {
            Column col = new Column(c[0], c[1], terrain.top(c[0], c[1]), c[2] / 1000.0);
            r.columns.put(Pos.columnKey(c[0], c[1]), col);
        }
        // Upper-envelope relaxation: design = max(ground, mean of neighbours). Planes are fixed points,
        // so a constant grade is preserved while the inside corners of each step get filled.
        for (int it = 0; it < iterations; it++) {
            Map<Long, Double> next = new HashMap<>();
            for (Column col : r.columns.values()) {
                // Missing neighbours (corridor edges/ends) are mirrored linearly so a plane stays a plane.
                double sum = 0; int n = 0;
                for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
                    Column a = r.columns.get(Pos.columnKey(col.x + d[0], col.z + d[1]));
                    Column b = r.columns.get(Pos.columnKey(col.x - d[0], col.z - d[1]));
                    if (a == null && b == null) continue;
                    double va = a != null ? a.design : 2 * col.design - b.design;
                    double vb = b != null ? b.design : 2 * col.design - a.design;
                    sum += va + vb; n += 2;
                }
                double avg = n == 0 ? col.design : sum / n;
                next.put(Pos.columnKey(col.x, col.z), Math.max(col.ground, avg));
            }
            for (Column col : r.columns.values()) col.design = next.get(Pos.columnKey(col.x, col.z));
        }
        // Grades, sun exposure and flags.
        for (Column col : r.columns.values()) {
            double gx = slope(r, col, 1, 0), gz = slope(r, col, 0, 1);
            double g = Math.hypot(gx, gz);
            col.gradeDeg = Math.toDegrees(Math.atan(g));
            // Downhill direction is -gradient; south (+z) facing slopes get more sun.
            col.sun = g < 1e-6 ? 0.5 : Math.max(0, Math.min(1, 0.5 + 0.5 * (-gz / g) * Math.min(1, g * 2)));
            if (col.design - col.ground > maxFill)
                r.flags.add(new TrailFlag(TrailFlag.Type.GAP, new Pos(col.x, col.ground, col.z),
                        String.format("%.1f m gap under the surface", col.design - col.ground)));
            if (g > cliffGrade)
                r.flags.add(new TrailFlag(TrailFlag.Type.CLIFF, new Pos(col.x, col.ground, col.z),
                        String.format("%.0f° pitch", col.gradeDeg)));
            double nbSum = 0; int nbN = 0;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Column nb = r.columns.get(Pos.columnKey(col.x + d[0], col.z + d[1]));
                if (nb != null) { nbSum += nb.ground; nbN++; }
            }
            if (nbN >= 3 && col.ground - nbSum / nbN >= protrudeHeight)
                r.flags.add(new TrailFlag(TrailFlag.Type.PROTRUDING, new Pos(col.x, col.ground, col.z),
                        String.format("sticks up %.1f m", col.ground - nbSum / nbN)));
        }
        // Profile along the centerline.
        double total = 0, maxG = 0;
        for (int i = 0; i < centerline.size() - 1; i++) {
            int[] a = centerline.get(i), b = centerline.get(i + 1);
            double run = Math.hypot(b[0] - a[0], b[1] - a[1]);
            Column ca = r.columns.get(Pos.columnKey(a[0], a[1])), cb = r.columns.get(Pos.columnKey(b[0], b[1]));
            double ha = ca != null ? ca.design : terrain.top(a[0], a[1]), hb = cb != null ? cb.design : terrain.top(b[0], b[1]);
            double grade = run == 0 ? 0 : Math.toDegrees(Math.atan(Math.abs(ha - hb) / run));
            r.segmentGrades.add(grade);
            total += run;
        }
        for (Column col : r.columns.values()) maxG = Math.max(maxG, col.gradeDeg);
        r.length = total;
        if (!centerline.isEmpty()) {
            int[] s = centerline.get(0), e = centerline.get(centerline.size() - 1);
            r.drop = terrain.top(s[0], s[1]) - terrain.top(e[0], e[1]);
        }
        r.avgGradeDeg = total == 0 ? 0 : Math.toDegrees(Math.atan(Math.abs(r.drop) / total));
        r.maxGradeDeg = maxG;
        r.difficulty = Difficulty.forGrade(Math.max(r.avgGradeDeg, percentile(r, 0.9)));
        return r;
    }

    private static double percentile(Result r, double q) {
        double[] g = r.columns.values().stream().mapToDouble(c -> c.gradeDeg).sorted().toArray();
        return g.length == 0 ? 0 : g[(int) Math.min(g.length - 1, Math.floor(q * g.length))];
    }

    /** Central-difference slope of the design surface (one-sided at corridor edges). */
    private static double slope(Result r, Column c, int dx, int dz) {
        Column p = r.columns.get(Pos.columnKey(c.x + dx, c.z + dz)), m = r.columns.get(Pos.columnKey(c.x - dx, c.z - dz));
        if (p != null && m != null) return (p.design - m.design) / 2.0;
        if (p != null) return p.design - c.design;
        if (m != null) return c.design - m.design;
        return 0;
    }
}
