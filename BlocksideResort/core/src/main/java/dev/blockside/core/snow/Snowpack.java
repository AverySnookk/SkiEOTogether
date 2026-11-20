package dev.blockside.core.snow;

import dev.blockside.core.Pos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All managed snow tiles plus the physics that acts on them. */
public final class Snowpack {
    private final Map<Long, SnowTile> tiles = new HashMap<>();
    /** Total water lost to runoff (keeps mass bookkeeping auditable). */
    public double runoff;

    public SnowTile get(int x, int z) { return tiles.get(Pos.columnKey(x, z)); }
    public SnowTile getOrCreate(int x, int z, int ground, double design) {
        return tiles.computeIfAbsent(Pos.columnKey(x, z), k -> new SnowTile(x, z, ground, design));
    }
    public void remove(int x, int z) { tiles.remove(Pos.columnKey(x, z)); }
    public Collection<SnowTile> all() { return tiles.values(); }
    public int size() { return tiles.size(); }

    public double totalMass() {
        double m = 0;
        for (SnowTile t : tiles.values()) m += t.mass();
        return m;
    }

    // ---- Physics (per tile) ----

    /** Advances one tile by dtHours at air temperature tempC with snowfall rate (m SWE / hour). */
    public static void step(SnowTile t, double tempC, double snowfallPerHour, double dtHours, SnowParams p, double[] runoffOut) {
        if (snowfallPerHour > 0 && tempC <= 1.0) t.addSnow(snowfallPerHour * dtHours, p.freshDensity);
        // Settling
        if (t.swe > 0 && t.density < p.maxDensity) t.density = Math.min(p.maxDensity, t.density + p.settlePerHour * dtHours);
        // Melt into retained liquid water.
        if (tempC > 0 && t.swe > 0) {
            double melt = p.meltPerDegreeHour * tempC * dtHours * (1 + p.sunMeltBonus * (t.sun - 0.5) * 2);
            double m = t.removeSnow(Math.max(0, melt));
            t.liquid += m;
            t.groomed = Math.max(0, t.groomed - 0.1 * dtHours);
        }
        // Liquid beyond what the pack can hold runs off (never negative, never more than present).
        double hold = t.swe * p.liquidHoldFraction;
        if (t.liquid > hold) {
            double r = t.liquid - hold;
            t.liquid = hold;
            runoffOut[0] += r;
        }
        // Refreeze overnight: liquid becomes ice crust, densifying the pack.
        if (tempC < 0 && t.liquid > 0) {
            double f = Math.min(t.liquid, p.refreezePerDegreeHour * -tempC * dtHours);
            double depthBefore = t.depth();
            t.liquid -= f;
            t.swe += f;
            t.ice = Math.min(1, (t.ice * (t.swe - f) + f) / t.swe);
            // Refreezing fills pore space: depth stays put, so density rises.
            t.density = Math.min(p.iceDensity, t.swe * 1000.0 / Math.max(1e-6, depthBefore));
        }
        t.groomed = Math.max(0, t.groomed - p.groomDecayPerHour * dtHours);
    }

    /**
     * One groomer pass over the given tiles: compacts to groomed density, breaks up ice, and moves a
     * limited amount of snow from tiles above their design surface into tiles below it. Mass is conserved.
     */
    public void groom(List<SnowTile> pass, SnowParams p) {
        if (pass.isEmpty()) return;
        List<SnowTile> donors = new ArrayList<>(), takers = new ArrayList<>();
        for (SnowTile t : pass) {
            double excess = t.surface() - t.design - p.minBase;
            if (excess > 0.05 && t.swe > 0) donors.add(t);
            else if (excess < -0.05) takers.add(t);
        }
        double pool = 0;
        for (SnowTile d : donors) {
            double excessDepth = d.surface() - d.design - p.minBase;
            double excessSwe = excessDepth * d.density / 1000.0;
            pool += d.removeSnow(Math.min(p.groomMovePerPass, excessSwe));
        }
        if (pool > 0 && !takers.isEmpty()) {
            double need = 0;
            double[] wants = new double[takers.size()];
            for (int i = 0; i < takers.size(); i++) {
                SnowTile t = takers.get(i);
                wants[i] = (t.design + p.minBase - t.surface()) * p.groomedDensity / 1000.0;
                need += wants[i];
            }
            double fraction = need <= 0 ? 0 : Math.min(1, pool / need);
            for (int i = 0; i < takers.size(); i++) takers.get(i).addSnow(wants[i] * fraction, p.groomedDensity);
            pool -= need * fraction;
        }
        // Whatever couldn't be placed goes back evenly so mass is conserved.
        if (pool > 1e-12) {
            List<SnowTile> back = donors.isEmpty() ? pass : donors;
            for (SnowTile t : back) t.addSnow(pool / back.size(), Math.max(t.density, p.groomedDensity));
        }
        for (SnowTile t : pass) {
            if (t.swe <= 0) continue;
            double depthBefore = t.depth();
            t.density = Math.max(t.density, p.groomedDensity);
            if (t.ice > 0) t.ice *= 0.4; // tiller chops the crust
            t.groomed = 1.0;
            assert t.depth() <= depthBefore + 1e-9;
        }
    }

    /** Snow gun deposit spread over the given tiles, weighted. */
    public static void deposit(List<SnowTile> footprint, double[] weights, double swe, SnowParams p) {
        double wsum = 0;
        for (double w : weights) wsum += w;
        if (wsum <= 0) return;
        for (int i = 0; i < footprint.size(); i++) footprint.get(i).addSnow(swe * weights[i] / wsum, p.machineDensity);
    }
}
