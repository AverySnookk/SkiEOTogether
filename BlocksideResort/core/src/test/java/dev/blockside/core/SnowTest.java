package dev.blockside.core;

import dev.blockside.core.snow.SnowParams;
import dev.blockside.core.snow.SnowTile;
import dev.blockside.core.snow.Snowpack;
import dev.blockside.core.snow.SurfaceKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SnowTest {
    final SnowParams p = new SnowParams();

    @Test void meltStopsAtZero() {
        SnowTile t = new SnowTile(0, 0, 64, 64);
        t.addSnow(0.01, 300);
        double[] runoff = {0};
        for (int h = 0; h < 500; h++) Snowpack.step(t, 15, 0, 1, p, runoff);
        assertEquals(0, t.swe, 0);
        assertEquals(0, t.depth(), 0);
        assertTrue(t.liquid >= 0);
        assertEquals(SurfaceKind.BARE, t.kind());
        assertEquals(0.01, t.mass() + runoff[0], 1e-9, "melt water is accounted for");
    }

    @Test void stormAddsPowderWarmDayMeltsColdNightRefreezes() {
        SnowTile t = new SnowTile(0, 0, 64, 64);
        t.addSnow(0.2, 350);
        double[] r = {0};
        double depth0 = t.depth();
        for (int h = 0; h < 6; h++) Snowpack.step(t, -8, 0.004, 1, p, r);
        assertTrue(t.depth() > depth0, "storm adds depth");
        assertTrue(t.density < 350, "fresh powder lowers bulk density");
        double swe = t.swe;
        for (int h = 0; h < 6; h++) Snowpack.step(t, 6, 0, 1, p, r);
        assertTrue(t.swe < swe, "warm day melts");
        assertTrue(t.liquid > 0, "some melt is retained");
        for (int h = 0; h < 10; h++) Snowpack.step(t, -10, 0, 1, p, r);
        assertTrue(t.ice > 0, "retained water refreezes as ice");
        assertEquals(0, t.liquid, 1e-9);
    }

    @Test void groomingConservesMassReducesDepthAndFillsSteps() {
        Snowpack pack = new Snowpack();
        // A step: tile 0 ground 65 design 65; tile 1 ground 64 design 64.67 (needs fill).
        SnowTile hi = pack.getOrCreate(0, 0, 65, 65.0);
        SnowTile lo = pack.getOrCreate(1, 0, 64, 64.67);
        hi.addSnow(0.3, 150); // 2 m of powder piled on the upper step
        lo.addSnow(0.03, 150); // 0.2 m
        double mass = pack.totalMass();
        double hiDepth = hi.depth();
        List<SnowTile> pass = new ArrayList<>(List.of(hi, lo));
        for (int i = 0; i < 10; i++) pack.groom(pass, p);
        assertEquals(mass, pack.totalMass(), 1e-12, "grooming never creates or destroys snow");
        assertTrue(hi.depth() < hiDepth, "compaction lowers the surface");
        double loMass0 = 0.03;
        assertTrue(lo.swe > loMass0 + 0.02, "snow moved into the step: " + lo.swe);
        assertTrue(lo.surface() > 64.3, "the step filled in: " + lo.surface());
        assertEquals(SurfaceKind.GROOMED, hi.kind());
    }

    @Test void unloadedTileCatchesUpLikeLoadedOne() {
        Resort a = TestWorld.newResort(), b = TestWorld.newResort();
        for (Resort r : new Resort[]{a, b}) {
            SnowTile t = r.snowpack.getOrCreate(0, 0, 70, 70);
            t.addSnow(0.1, 300);
            t.lastTick = 1;
        }
        long end = 30_000;
        for (long t = 1000; t <= end; t += 1000) a.snowStep(t, k -> true);   // always loaded, hourly
        b.snowStep(end, k -> true);                                         // unloaded the whole time
        assertEquals(a.snowpack.get(0, 0).swe, b.snowpack.get(0, 0).swe, 1e-9);
        assertEquals(a.snowpack.get(0, 0).density, b.snowpack.get(0, 0).density, 1e-9);
    }

    @Test void snowGunStatusAndDeposit() {
        Resort r = TestWorld.newResort();
        for (int x = -3; x <= 3; x++) for (int z = -12; z <= -1; z++) r.snowpack.getOrCreate(x, z, 64, 64);
        SnowGun g = new SnowGun();
        g.pos = new Pos(0, 64, 0); g.facing = 0; g.on = true;
        r.weather.override = "WARM"; r.weather.overrideUntil = Long.MAX_VALUE;
        assertTrue(r.runSnowGun(g, true, true, 1, 0).isEmpty());
        assertEquals(SnowGun.Status.TOO_WARM, g.status);
        r.weather.override = "COLD";
        r.runSnowGun(g, false, true, 1, 0);
        assertEquals(SnowGun.Status.NO_WATER, g.status);
        r.runSnowGun(g, true, false, 1, 0);
        assertEquals(SnowGun.Status.NO_POWER, g.status);
        double before = r.snowpack.totalMass();
        long cash = r.ledger.cash();
        var hit = r.runSnowGun(g, true, true, 1, 0);
        assertEquals(SnowGun.Status.RUNNING, g.status);
        assertFalse(hit.isEmpty());
        assertEquals(before + r.config.snowGunSwePerHour, r.snowpack.totalMass(), 1e-9);
        assertTrue(r.ledger.cash() < cash, "snowmaking costs money");
        for (SnowTile t : hit) assertTrue(t.z < 0, "throws forward (north)");
        g.facing = 2;
        r.runSnowGun(g, true, true, 1, 0);
        assertEquals(SnowGun.Status.NO_TERRAIN, g.status, "facing away from the trail");
    }

    @Test void groomPathTouchesSweptTiles() {
        Resort r = TestWorld.newResort();
        for (int x = 0; x < 10; x++) for (int z = 0; z < 5; z++) r.snowpack.getOrCreate(x, z, 64, 64).addSnow(0.05, 200);
        var touched = r.groomPath(0.5, 2.5, 9.5, 2.5, 1.5);
        assertTrue(touched.size() >= 30, "covers the whole swept strip, not just the end points: " + touched.size());
        for (SnowTile t : touched) assertEquals(1.0, t.groomed);
    }
}
