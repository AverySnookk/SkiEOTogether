package dev.blockside.core.lift;

import dev.blockside.core.Pos;
import dev.blockside.core.terrain.HeightField;

import java.util.ArrayList;
import java.util.List;

/** Plans tower positions/heights along a straight line and validates chair clearance. */
public final class LiftDesign {
    public final Pos lower, upper;
    public final List<Pos> towerTops = new ArrayList<>();
    public final List<Integer> towerBases = new ArrayList<>();
    public final List<Pos> clearanceViolations = new ArrayList<>();
    public final List<String> problems = new ArrayList<>();
    public double horizontalLength, rise, angleDeg, minClearanceFound = Double.MAX_VALUE;

    private LiftDesign(Pos lower, Pos upper) { this.lower = lower; this.upper = upper; }

    public boolean ok() { return problems.isEmpty(); }

    public static LiftDesign plan(Pos lower, Pos upper, HeightField terrain, LiftParams p, int maxLength) {
        LiftDesign d = new LiftDesign(lower, upper);
        double dx = upper.x() - lower.x(), dz = upper.z() - lower.z();
        d.horizontalLength = Math.hypot(dx, dz);
        d.rise = upper.y() - lower.y();
        d.angleDeg = Math.toDegrees(Math.atan2(d.rise, Math.max(1e-6, d.horizontalLength)));
        if (d.horizontalLength < p.minLength) d.problems.add("Line too short (" + (int) d.horizontalLength + " < " + p.minLength + " blocks)");
        if (d.horizontalLength > maxLength) d.problems.add("Line too long for this lift (" + (int) d.horizontalLength + " > " + maxLength + ")");
        if (d.rise <= 0) d.problems.add("Upper station must be higher than the lower station");
        if (d.angleDeg > p.maxAngle) d.problems.add(String.format("Line too steep (%.0f° > %.0f°)", d.angleDeg, p.maxAngle));
        if (!d.problems.isEmpty()) return d;

        int n = Math.max(1, (int) Math.ceil(d.horizontalLength / p.towerSpacing));
        int[] heights = new int[n + 1];
        Pos[] bases = new Pos[n + 1];
        for (int k = 1; k < n; k++) {
            double t = (double) k / n;
            int x = (int) Math.round(lower.x() + dx * t), z = (int) Math.round(lower.z() + dz * t);
            bases[k] = new Pos(x, terrain.top(x, z), z);
            heights[k] = p.towerHeight;
        }
        // Raise towers until clearance is met everywhere or towers are maxed out.
        for (int iter = 0; iter < 40; iter++) {
            List<double[]> supports = supports(d, bases, heights, n, p);
            int worstSpan = -1;
            double worst = Double.MAX_VALUE;
            d.clearanceViolations.clear();
            d.minClearanceFound = Double.MAX_VALUE;
            for (int s = 0; s < supports.size() - 1; s++) {
                double[] a = supports.get(s), b = supports.get(s + 1);
                double len = Math.hypot(b[0] - a[0], b[2] - a[2]);
                // Skip the boarding/unloading zone right at the stations.
                for (double u = 0; u <= len; u += 0.5) {
                    double t = len == 0 ? 0 : u / len;
                    double x = a[0] + (b[0] - a[0]) * t, z = a[2] + (b[2] - a[2]) * t;
                    double distFromEnds = Math.min(Math.hypot(x - lower.x() - 0.5, z - lower.z() - 0.5), Math.hypot(x - upper.x() - 0.5, z - upper.z() - 0.5));
                    if (distFromEnds < 3) continue;
                    double cable = a[1] + (b[1] - a[1]) * t;
                    int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
                    double clearance = cable - p.hang - terrain.top(bx, bz);
                    d.minClearanceFound = Math.min(d.minClearanceFound, clearance);
                    if (clearance < p.minClearance) {
                        if (d.clearanceViolations.size() < 64) d.clearanceViolations.add(new Pos(bx, terrain.top(bx, bz), bz));
                        if (clearance < worst) { worst = clearance; worstSpan = s; }
                    }
                }
            }
            if (worstSpan < 0) break;
            // Raise the lower-clearance interior tower adjacent to the worst span.
            int left = worstSpan, right = worstSpan + 1;
            boolean raised = false;
            for (int k : new int[]{left, right}) {
                if (k >= 1 && k <= n - 1 && heights[k] < p.maxTowerHeight) { heights[k]++; raised = true; }
            }
            if (!raised) break;
        }
        for (int k = 1; k < n; k++) {
            d.towerBases.add(bases[k].y());
            d.towerTops.add(new Pos(bases[k].x(), bases[k].y() + heights[k], bases[k].z()));
        }
        if (!d.clearanceViolations.isEmpty())
            d.problems.add(String.format("Chair clearance too low (%.1f m < %.1f m) — move a station or regrade under the line",
                    d.minClearanceFound, p.minClearance));
        return d;
    }

    /** Support points (x+0.5, cableY, z+0.5): lower station, towers, upper station. */
    private static List<double[]> supports(LiftDesign d, Pos[] bases, int[] heights, int n, LiftParams p) {
        List<double[]> s = new ArrayList<>();
        s.add(new double[]{d.lower.x() + 0.5, d.lower.y() + p.stationCableHeight, d.lower.z() + 0.5});
        for (int k = 1; k < n; k++) s.add(new double[]{bases[k].x() + 0.5, bases[k].y() + heights[k], bases[k].z() + 0.5});
        s.add(new double[]{d.upper.x() + 0.5, d.upper.y() + p.stationCableHeight, d.upper.z() + 0.5});
        return s;
    }

    public List<double[]> supportPoints(LiftParams p) {
        List<double[]> s = new ArrayList<>();
        s.add(new double[]{lower.x() + 0.5, lower.y() + p.stationCableHeight, lower.z() + 0.5});
        for (Pos t : towerTops) s.add(new double[]{t.x() + 0.5, t.y(), t.z() + 0.5});
        s.add(new double[]{upper.x() + 0.5, upper.y() + p.stationCableHeight, upper.z() + 0.5});
        return s;
    }
}
