package dev.blockside.mod.server;

import dev.blockside.core.Pos;
import dev.blockside.core.Resort;
import dev.blockside.core.Trail;
import dev.blockside.core.snow.SnowTile;
import dev.blockside.core.terrain.EditJournal;
import dev.blockside.core.terrain.TrailDesign;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Builds a repeatable test mountain in front of the player (uphill = north):
 * <ul>
 * <li>Bunny Hill: 1 block down per 3 forward (≈18°), 9 wide, 60 long — the first playable trail.</li>
 * <li>Expert Wall: 1 down per 1 forward (45°).</li>
 * <li>The Chute: 2 down per 1 forward (≈63°) — flagged as a cliff hazard.</li>
 * <li>A flat snowy base area to the south for the ticket booth, garage and lift bottom.</li>
 * </ul>
 */
public final class TestHill {
    private static EditJournal job;
    private static int x0, y0, z0;
    private static UUID owner;
    private TestHill() {}

    static int height(int x, int z) {
        if (z >= z0) return y0;
        int d = z0 - z;
        if (x >= x0 + 12 && x <= x0 + 18) return y0 + Math.min(20, d);
        if (x >= x0 + 22 && x <= x0 + 26) return y0 + Math.min(20, 2 * d);
        return y0 + Math.min(20, (d + 2) / 3);
    }

    /** Runs once the hill and its trails exist (used by /resort selftest). */
    public static Runnable afterFinish;

    public static int[] origin() { return new int[]{x0, y0, z0}; }

    public static void build(ServerPlayer p) { build(p.blockPosition(), p); }

    public static void build(BlockPos at, ServerPlayer p) {
        if (job != null && !job.done()) { if (p != null) p.sendSystemMessage(Ui.text("A test hill is already being built.", ChatFormatting.RED)); return; }
        ServerLevel level = ResortServer.level();
        x0 = at.getX(); y0 = at.getY(); z0 = at.getZ();
        owner = p == null ? null : p.getUUID();
        job = new EditJournal();
        job.id = "testhill";
        job.label = "Test hill";
        for (int x = x0 - 30; x <= x0 + 30; x++) for (int z = z0 - 80; z <= z0 + 24; z++) {
            int h = height(x, z);
            for (int y = y0 - 5; y <= y0 + 45; y++) {
                String want = y < h - 1 ? "minecraft:stone" : y == h - 1 ? "minecraft:snow_block" : "minecraft:air";
                String cur = WorldAdapter.spec(level, x, y, z);
                if (!cur.equals(want)) job.edits.add(new EditJournal.Edit(x, y, z, cur, want));
            }
        }
        if (p != null) p.sendSystemMessage(Ui.text("Building the test hill: " + job.edits.size() + " block changes (a few seconds)…", ChatFormatting.YELLOW));
        dev.blockside.mod.BlocksideMod.LOG.info("[Blockside] test hill at {} {} {}: {} block changes", x0, y0, z0, job.edits.size());
    }

    public static void tick(ServerLevel level) {
        if (job == null || job.done()) return;
        List<EditJournal.Edit> batch = job.nextBatch(6000);
        for (EditJournal.Edit e : batch) WorldAdapter.setSpec(level, e.x(), e.y(), e.z(), e.after());
        job.advance(batch.size());
        if (job.done()) finish(level);
    }

    private static void finish(ServerLevel level) {
        Resort r = ResortServer.resort();
        ServerPlayer p = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        String who = p == null ? "server" : ResortServer.who(p);
        if (!r.claimed()) r.claim(who, new Pos(x0, y0, z0 - 20));
        Trail bunny = trail(level, r, "Bunny Hill", List.of(new int[]{x0, z0 - 58}, new int[]{x0, z0 - 1}), 9, 0.35, 330);
        bunny.open = true;
        Trail wall = trail(level, r, "Expert Wall", List.of(new int[]{x0 + 15, z0 - 24}, new int[]{x0 + 15, z0 - 1}), 5, 0.5, 150);
        wall.open = true;
        trail(level, r, "The Chute", List.of(new int[]{x0 + 24, z0 - 14}, new int[]{x0 + 24, z0 - 1}), 3, 0.5, 150);
        if (p != null) {
            p.sendSystemMessage(Ui.title("Test hill ready"));
            p.sendSystemMessage(Ui.text("Bunny Hill (≈18°) at x=" + x0 + " is open with a packed, ungroomed base. Expert Wall (45°) x=" + (x0 + 15)
                    + ", The Chute (63°, flagged) x=" + (x0 + 24) + ". Uphill is north.", ChatFormatting.WHITE));
            p.sendSystemMessage(Ui.text("Suggested spots: Ticket Booth " + (x0 + 6) + " " + y0 + " " + (z0 + 14)
                    + " · Groomer Garage " + (x0 - 3) + " " + y0 + " " + (z0 + 12)
                    + " · Lift bottom " + (x0 - 14) + " " + y0 + " " + (z0 + 6) + " → top " + (x0 - 14) + " " + (y0 + 20) + " " + (z0 - 66), ChatFormatting.AQUA));
            p.sendSystemMessage(Ui.button("Open the catalog", "/resort catalog", "Buy a ticket booth and a lift"));
        }
        dev.blockside.mod.BlocksideMod.LOG.info("[Blockside] test hill finished; {} trails", r.trails.size());
        if (afterFinish != null) { Runnable a = afterFinish; afterFinish = null; a.run(); }
    }

    private static Trail trail(ServerLevel level, Resort r, String name, List<int[]> line, double width, double extraDepth, double density) {
        TrailDesign.Result d = new TrailDesign().design(line, width, TrailTools.terrain(level));
        Trail t = r.addTrail(name, line, width, d);
        for (long k : t.tiles) {
            SnowTile s = r.snowpack.get(Pos.keyX(k), Pos.keyZ(k));
            // Enough snow to still cover the steps after a groomer compacts it to groomed density.
            if (s.swe <= 0) s.addSnow((s.neededDepth(r.config.snow) + extraDepth) * r.config.snow.groomedDensity / 1000.0, density);
            s.lastTick = level.getOverworldClockTime();
            SnowRenderer.render(level, s, r.config.snow);
        }
        return t;
    }
}
