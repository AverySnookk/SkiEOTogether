package dev.blockside.core;

import dev.blockside.core.lift.LiftDesign;
import dev.blockside.core.lift.LiftLoop;
import dev.blockside.core.lift.LiftParams;
import dev.blockside.core.ski.SkiInput;
import dev.blockside.core.ski.SkiParams;
import dev.blockside.core.ski.SkiPhysics;
import dev.blockside.core.ski.SkiState;
import dev.blockside.core.ski.SkiSurface;
import dev.blockside.core.ski.SurfacePoint;
import dev.blockside.core.ski.SurfaceSampler;
import dev.blockside.core.ski.Vec3;
import dev.blockside.core.snow.SurfaceKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkiAndLiftTest {
    /** 1:3 staircase with snow-layer fill quantised to 1/8 (what the renderer shows). */
    static SurfaceSampler slope(SurfaceKind kind) {
        return new SurfaceSampler() {
            public double columnTop(int x, int z, double nearY) {
                double smooth = 100 - z / 3.0 + 0.25;
                return Math.round(smooth * 8) / 8.0;
            }
            public SurfaceKind kind(int x, int z, double nearY) { return kind; }
        };
    }

    static double runDownhill(SurfaceKind kind, boolean brake, int ticks) {
        SkiState st = new SkiState();
        SkiParams p = new SkiParams();
        SurfaceSampler s = slope(kind);
        double x = 0.5, z = 0.5;
        for (int i = 0; i < ticks; i++) {
            SurfacePoint sp = SkiSurface.sample(s, x, z, 100);
            SkiPhysics.step(st, new SkiInput(false, brake, false, false, false, 0f), sp, 0.05, p);
            x += st.velocity.x() * 0.05;
            z += st.velocity.z() * 0.05;
        }
        return st.velocity.length();
    }

    @Test void sharedSurfaceIsSmoothOnStairs() {
        SurfaceSampler s = slope(SurfaceKind.GROOMED);
        double prevH = Double.NaN;
        for (double z = 3; z < 30; z += 0.1) {
            SurfacePoint p = SkiSurface.sample(s, 0.5, z, 100);
            assertEquals(18.4, SkiSurface.gradeDeg(p.normal()), 6, "normal tracks the slope at z=" + z);
            if (!Double.isNaN(prevH)) assertTrue(Math.abs(p.height() - prevH) < 0.1, "no steps in the surface");
            prevH = p.height();
        }
    }

    @Test void acceleratesDownhillAndReachesTerminalSpeed() {
        double v1 = runDownhill(SurfaceKind.GROOMED, false, 20);
        double v2 = runDownhill(SurfaceKind.GROOMED, false, 200);
        double v3 = runDownhill(SurfaceKind.GROOMED, false, 600);
        assertTrue(v1 > 1 && v2 > v1, "accelerates: " + v1 + " " + v2);
        assertTrue(v3 < new SkiParams().maxSpeed && Math.abs(v3 - v2) < 4, "drag bounds speed: " + v2 + " " + v3);
    }

    @Test void groomedIsFasterThanPowderAndBrakingSlows() {
        double groomed = runDownhill(SurfaceKind.GROOMED, false, 300);
        double powder = runDownhill(SurfaceKind.POWDER, false, 300);
        double braking = runDownhill(SurfaceKind.GROOMED, true, 300);
        assertTrue(groomed > powder + 1, groomed + " vs " + powder);
        assertTrue(braking < 2, "snowplough on a beginner slope stops you: " + braking);
    }

    @Test void steeringTurnsAndIceGripsLess() {
        SkiParams p = new SkiParams();
        for (SurfaceKind k : new SurfaceKind[]{SurfaceKind.GROOMED, SurfaceKind.ICE}) {
            SkiState st = new SkiState();
            st.velocity = new Vec3(0, 0, 10);
            SurfacePoint flat = new SurfacePoint(64, Vec3.UP, k);
            for (int i = 0; i < 10; i++) SkiPhysics.step(st, new SkiInput(false, false, false, false, false, -90f), flat, 0.05, p);
            // Looking toward +x (yaw -90): velocity swings toward +x.
            assertTrue(st.velocity.x() > 0, k + " turns");
            if (k == SurfaceKind.ICE) assertTrue(st.velocity.x() < 7, "ice skids instead of carving: " + st.velocity);
            else assertTrue(st.velocity.x() > 3, "groomed carves: " + st.velocity);
        }
    }

    @Test void liftDesignPlansTowersWithClearance() {
        LiftParams p = new LiftParams();
        // Mountain: rises 1 block every 3 toward -z, with a bump in the middle.
        dev.blockside.core.terrain.HeightField h = (x, z) -> 64 + Math.max(0, -z) / 3 + ((z <= -28 && z >= -32) ? 4 : 0);
        LiftDesign d = LiftDesign.plan(new Pos(0, 64, 0), new Pos(0, 84, -60), h, p, 140);
        assertTrue(d.ok(), d.problems.toString());
        assertTrue(d.towerTops.size() >= 3);
        assertTrue(d.minClearanceFound >= p.minClearance);
        LiftDesign bad = LiftDesign.plan(new Pos(0, 64, 0), new Pos(0, 60, -40), h, p, 140);
        assertFalse(bad.ok(), "upper station lower than the bottom");
        dev.blockside.core.terrain.HeightField wall = (x, z) -> (z <= -20 && z >= -24) ? 110 : 64 + Math.max(0, -z) / 3;
        assertFalse(LiftDesign.plan(new Pos(0, 64, 0), new Pos(0, 84, -60), wall, p, 140).ok(), "ridge too tall to clear");
    }

    @Test void liftLoopIsContinuous() {
        LiftParams p = new LiftParams();
        LiftDesign d = LiftDesign.plan(new Pos(0, 64, 0), new Pos(10, 84, -60), (x, z) -> 64 + Math.max(0, -z) / 3, p, 140);
        LiftLoop loop = new LiftLoop(d.supportPoints(p), p.lineOffset);
        double[] prev = loop.at(0);
        for (double s = 0.25; s <= loop.length(); s += 0.25) {
            double[] c = loop.at(s);
            double dist = Math.sqrt(Math.pow(c[0] - prev[0], 2) + Math.pow(c[1] - prev[1], 2) + Math.pow(c[2] - prev[2], 2));
            assertTrue(dist < 0.6, "jump at s=" + s + ": " + dist);
            prev = c;
        }
        assertTrue(loop.ascending(1));
        assertFalse(loop.ascending(loop.upLength() + loop.length() / 4));
        double[] top = loop.at(loop.upLength() + Math.PI * p.lineOffset / 2);
        assertTrue(top[2] < -60, "bullwheel swing goes beyond the upper station, not back through it: " + top[2]);
    }

    @Test void liftJobBuildsStationsAndTowersThenRegistersLift() {
        Resort r = TestWorld.newResort();
        dev.blockside.core.terrain.HeightField h = (x, z) -> 64 + Math.max(0, -z) / 3;
        TestWorld w = new TestWorld(h);
        String kit = r.buy("beginner_lift", "alex", 0).ref();
        Pos lower = new Pos(0, 64, 0), upper = new Pos(0, h.top(0, -60), -60);
        var check = r.checkLift(kit, lower, upper, h, w);
        assertTrue(check.ok(), check.summary());
        ActionResult a = r.placeLift(kit, lower, upper, h, w, "alex", 0);
        assertTrue(a.ok(), a.message());
        var job = r.jobs.get(a.ref());
        assertEquals(HireResult.HIRED, r.hire(job.id, "alex", 0));
        ConstructionTest.runToCompletion(r, w);
        assertEquals(1, r.lifts.size());
        LiftRecord l = r.lifts.values().iterator().next();
        assertFalse(l.towers.isEmpty());
        assertEquals("blockside:tower_section", w.spec(l.towers.get(0).x(), l.towers.get(0).y() - 1, l.towers.get(0).z()));
        assertTrue(r.progression.xp() >= 125, "lift build XP + new building kind");
    }
}
