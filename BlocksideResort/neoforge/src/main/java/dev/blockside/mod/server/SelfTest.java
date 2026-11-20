package dev.blockside.mod.server;

import dev.blockside.core.ActionResult;
import dev.blockside.core.HireResult;
import dev.blockside.core.LiftRecord;
import dev.blockside.core.Pos;
import dev.blockside.core.Resort;
import dev.blockside.core.SnowGun;
import dev.blockside.core.Snapshot;
import dev.blockside.core.Trail;
import dev.blockside.core.construction.Job;
import dev.blockside.core.snow.SnowTile;
import dev.blockside.mod.BlocksideMod;
import dev.blockside.mod.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Console-driven walkthrough of the core loop on a dedicated server, using the same code paths as a
 * player: test hill → buy/place/hire ticket booth, chairlift and garage → snow gun → open the lift.
 * Progress is visible with /resort status. Intended for test worlds only.
 */
public final class SelfTest {
    private SelfTest() {}

    public static void start(BlockPos origin) {
        TestHill.afterFinish = SelfTest::afterHill;
        TestHill.build(origin, null);
    }

    private static void afterHill() {
        ServerLevel level = ResortServer.level();
        Resort r = ResortServer.resort();
        int[] o = TestHill.origin();
        int x0 = o[0], y0 = o[1], z0 = o[2];
        String who = "selftest";
        long now = ResortServer.now();
        r.members.add(who);
        log("buy ticket booth", r.buy("ticket_booth", who, now));
        String booth = lastPurchase(r);
        ActionResult b = r.placeBuilding(booth, new Pos(x0 + 6, y0, z0 + 14), 0, WorldAdapter.probe(level), who, now);
        log("place ticket booth", b);
        if (b.ok()) jobBlock(level, r.jobs.get(b.ref()).anchor);

        log("buy groomer garage", r.buy("groomer_garage", who, now));
        String garage = lastPurchase(r);
        ActionResult g = r.placeBuilding(garage, new Pos(x0 - 3, y0, z0 + 12), 0, WorldAdapter.probe(level), who, now);
        log("place garage", g);
        if (g.ok()) jobBlock(level, r.jobs.get(g.ref()).anchor);

        log("buy lift", r.buy("beginner_lift", who, now));
        String lift = lastPurchase(r);
        Pos lower = new Pos(x0 - 14, y0, z0 + 6);
        Pos upper = new Pos(x0 - 14, WorldAdapter.top(level, x0 - 14, z0 - 66), z0 - 66);
        ActionResult l = r.placeLift(lift, lower, upper, TrailTools.terrain(level), WorldAdapter.probe(level), who, now);
        log("place lift", l);
        if (l.ok()) { jobBlock(level, lower); jobBlock(level, upper); }

        // Double-click hire on every job: second click must not charge.
        for (Job j : new ArrayList<>(r.jobs.values())) {
            long before = r.ledger.cash();
            HireResult h1 = r.hire(j.id, who, now), h2 = r.hire(j.id, who, now);
            BlocksideMod.LOG.info("[Blockside][selftest] hire {}: {} then {} (charged {})", j.name, h1, h2, before - r.ledger.cash());
        }

        // Snow gun at the top of Bunny Hill, facing downhill (south), with water and power.
        BlockPos gun = new BlockPos(x0 - 6, y0 + 20, z0 - 62);
        level.setBlock(gun, Registry.SNOW_GUN.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(gun.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        level.setBlock(gun.north().below(), Blocks.WATER.defaultBlockState(), 3);
        ResortServer.snowGunPlaced(level, gun, level.getBlockState(gun));
        SnowGun sg = r.snowGuns.get(ResortServer.key(new Pos(gun.getX(), gun.getY(), gun.getZ())));
        sg.on = true;
        r.weather.override = "COLD";
        r.weather.overrideUntil = now + 48_000;
        BlocksideMod.LOG.info("[Blockside][selftest] setup done; watch /resort status");
    }

    private static String lastPurchase(Resort r) {
        String id = null;
        for (String k : r.purchases.keySet()) id = k;
        return id;
    }

    private static void jobBlock(ServerLevel level, Pos p) {
        level.setBlock(new BlockPos(p.x(), p.y(), p.z()), Registry.JOB_BLOCK.get().defaultBlockState(), 3);
    }

    private static void log(String what, ActionResult a) {
        BlocksideMod.LOG.info("[Blockside][selftest] {}: {} {}", what, a.ok() ? "OK" : "FAILED", a.message());
    }

    /** One-line-per-item resort report for the console. */
    public static List<String> status() {
        Resort r = ResortServer.resort();
        ServerLevel level = ResortServer.level();
        Snapshot s = Snapshot.of(r, ResortServer.now());
        List<String> out = new ArrayList<>();
        out.add("modTicks=" + ResortServer.ticks() + " lastError=" + ResortServer.lastError);
        out.add(String.format("cash=%d level=%d xp=%d rep=%.1f tickets=%d guests=%.0f lifetimeRevenue=%d | %s",
                s.cash, s.level, s.xp, s.reputation, s.ticketsSold, s.guestsOnMountain, s.lifetimeRevenue, s.weather));
        for (Snapshot.Row j : s.jobs) out.add("job " + j.id + " " + j.name + ": " + j.status);
        for (LiftRecord l : r.lifts.values()) out.add("lift " + l.id + " " + l.name + " open=" + l.open + " towers=" + l.towers.size() + " rides=" + l.ridesToday);
        for (var f : r.facilities.values()) out.add("facility " + f.id + " " + f.name + " open=" + f.open);
        r.refreshTrailStats();
        for (Trail t : r.trails.values()) out.add(String.format("trail %s %s open=%s %s %.0f°avg coverage=%.0f%% groomed=%.0f%%",
                t.id, t.name, t.open, t.difficulty, t.avgGradeDeg, t.coverage * 100, t.groomedFraction * 100));
        for (SnowGun g : r.snowGuns.values()) out.add("snowgun " + g.pos + " " + g.status + String.format(" made=%.3f", g.madeSwe));
        for (String m : s.milestones) out.add("milestone " + m);
        for (String a : s.alerts) out.add("alert " + a);
        // Sample the Bunny Hill surface: rendered snow block vs snowpack.
        int[] o = TestHill.origin();
        SnowTile t = r.snowpack.get(o[0], o[2] - 30);
        if (t != null) out.add(String.format("tile(%d,%d) ground=%d design=%.2f depth=%.2f kind=%s rendered=%s", t.x, t.z, t.ground, t.design, t.depth(), t.kind(),
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(t.x, t.ground, t.z)).getBlock())
                        + "x" + WorldAdapter.snowLayers(level.getBlockState(new BlockPos(t.x, t.ground, t.z)))));
        return out;
    }

    /** Grooms the whole Bunny Hill in passes, like a snowcat driving up and down it. */
    public static int groomBunnyHill() {
        Resort r = ResortServer.resort();
        ServerLevel level = ResortServer.level();
        int[] o = TestHill.origin();
        int n = 0;
        for (int lane = -3; lane <= 3; lane += 3) {
            List<SnowTile> touched = r.groomPath(o[0] + 0.5 + lane, o[2] - 58, o[0] + 0.5 + lane, o[2] - 1, 1.5);
            for (SnowTile t : touched) SnowRenderer.render(level, t, r.config.snow);
            n += touched.size();
        }
        return n;
    }
}
