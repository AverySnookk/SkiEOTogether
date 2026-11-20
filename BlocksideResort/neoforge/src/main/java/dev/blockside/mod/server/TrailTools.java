package dev.blockside.mod.server;

import dev.blockside.core.Resort;
import dev.blockside.core.Trail;
import dev.blockside.core.terrain.EarthworkPlanner;
import dev.blockside.core.terrain.EditJournal;
import dev.blockside.core.terrain.HeightField;
import dev.blockside.core.terrain.TrailDesign;
import dev.blockside.core.terrain.TrailFlag;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Survey stakes → corridor preview → confirm (tree clearing / cut-and-fill, batched and undoable). */
public final class TrailTools {
    private static final Map<UUID, List<int[]>> drafts = new HashMap<>();
    private static final Map<UUID, TrailDesign.Result> previews = new HashMap<>();
    private static final Map<UUID, Long> previewUntil = new HashMap<>();
    private TrailTools() {}

    public static HeightField terrain(ServerLevel level) { return (x, z) -> WorldAdapter.top(level, x, z); }

    public static void stakeUsed(ServerLevel level, ServerPlayer p, BlockPos pos) {
        List<int[]> pts = drafts.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        if (p.isShiftKeyDown()) {
            pts.clear();
            previews.remove(p.getUUID());
            p.sendSystemMessage(Ui.text("Survey cleared.", ChatFormatting.GRAY));
            return;
        }
        int[] pt = {pos.getX(), pos.getZ()};
        String grade = "";
        if (!pts.isEmpty()) {
            int[] a = pts.get(pts.size() - 1);
            double run = Math.hypot(pt[0] - a[0], pt[1] - a[1]);
            int ha = WorldAdapter.top(level, a[0], a[1]), hb = WorldAdapter.top(level, pt[0], pt[1]);
            grade = String.format(" · %.0f° from the last stake (%.0f m, %+d m)", Math.toDegrees(Math.atan2(Math.abs(ha - hb), Math.max(0.01, run))), run, hb - ha);
        }
        pts.add(pt);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 10, 0.1, 0.5, 0.1, 0.01);
        p.sendSystemMessage(Ui.text("Stake " + pts.size() + " at " + pt[0] + " " + pt[1] + grade, ChatFormatting.YELLOW));
        if (pts.size() >= 2) p.sendSystemMessage(Ui.button("Preview corridor (width 9)", "/resort trail preview 9", "Show grade, obstacles and what will change"));
    }

    public static void preview(ServerPlayer p, double width) {
        ServerLevel level = ResortServer.level();
        List<int[]> pts = drafts.get(p.getUUID());
        if (pts == null || pts.size() < 2) { p.sendSystemMessage(Ui.text("Place at least 2 survey stakes (top to bottom).", ChatFormatting.RED)); return; }
        TrailDesign.Result d = new TrailDesign().design(pts, width, terrain(level));
        previews.put(p.getUUID(), d);
        previewUntil.put(p.getUUID(), level.getGameTime() + 400);
        int trees = 0;
        for (TrailDesign.Column c : d.columns.values())
            for (int y = c.ground; y < c.ground + 20; y++) if (level.getBlockState(new BlockPos(c.x, y, c.z)).is(BlockTags.LOGS)) trees++;
        p.sendSystemMessage(Ui.title("Corridor preview"));
        p.sendSystemMessage(Ui.text(String.format("%.0f m long, %.0f m drop, %.0f° average, %.0f° steepest → %s. %d columns, %d log blocks to clear.",
                d.length, d.drop, d.avgGradeDeg, d.maxGradeDeg, d.difficulty.label, d.columns.size(), trees), ChatFormatting.WHITE));
        StringBuilder seg = new StringBuilder("Segments:");
        for (double g : d.segmentGrades) seg.append(String.format(" %.0f°", g));
        p.sendSystemMessage(Ui.text(seg.toString(), ChatFormatting.GRAY));
        Map<TrailFlag.Type, Integer> counts = new HashMap<>();
        for (TrailFlag f : d.flags) counts.merge(f.type(), 1, Integer::sum);
        if (counts.isEmpty()) p.sendSystemMessage(Ui.text("✔ No earthwork needed: snow will smooth the steps.", ChatFormatting.GREEN));
        counts.forEach((t, n) -> p.sendSystemMessage(Ui.text("✘ " + n + " × " + t + (t == TrailFlag.Type.CLIFF ? " (hazard: steeper than 58°)" : t == TrailFlag.Type.GAP ? " (needs fill)" : " (needs cutting)"), ChatFormatting.RED)));
        String w = String.valueOf((int) width);
        MutableComponent row = Ui.button("Clear trees only", "/resort trail confirm clear " + w + " Run" + (ResortServer.resort().trails.size() + 1), "Fell trees and plants inside the corridor")
                .append(" ").append(Ui.button("Fix flagged spots", "/resort trail confirm fix " + w + " Run" + (ResortServer.resort().trails.size() + 1), "Clear + fill gaps + cut protrusions"))
                .append(" ").append(Ui.button("Grade evenly", "/resort trail confirm grade " + w + " Run" + (ResortServer.resort().trails.size() + 1), "Clear + cut-and-fill to one even pitch"));
        p.sendSystemMessage(row);
        p.sendSystemMessage(Ui.text("Name it yourself: /resort trail confirm <clear|fix|grade> <width> <name>", ChatFormatting.DARK_GRAY));
    }

    public static void confirm(ServerPlayer p, String mode, double width, String name) {
        ServerLevel level = ResortServer.level();
        Resort r = ResortServer.resort();
        List<int[]> pts = drafts.get(p.getUUID());
        if (pts == null || pts.size() < 2) { p.sendSystemMessage(Ui.text("Place at least 2 survey stakes first.", ChatFormatting.RED)); return; }
        if (!r.isMember(ResortServer.who(p))) { p.sendSystemMessage(Ui.text("Not a member of this resort.", ChatFormatting.RED)); return; }
        for (int[] pt : pts) if (!r.inClaim(pt[0], pt[1])) { p.sendSystemMessage(Ui.text("The corridor leaves the resort claim.", ChatFormatting.RED)); return; }
        TrailDesign.Result d = new TrailDesign().design(pts, width, terrain(level));
        Trail t = r.addTrail(name, pts, width, d);
        EarthworkPlanner.Mode m = switch (mode) { case "fix" -> EarthworkPlanner.Mode.FIX; case "grade" -> EarthworkPlanner.Mode.GRADE; default -> EarthworkPlanner.Mode.CLEAR; };
        EditJournal j = new EarthworkPlanner().plan(d, m, world(level));
        j.id = "E" + (++r.counter);
        j.label = name + " (" + mode + ")";
        j.trailId = t.id;
        j.owner = ResortServer.who(p);
        r.journals.put(j.id, j);
        drafts.remove(p.getUUID());
        previews.remove(p.getUUID());
        p.sendSystemMessage(Ui.text("Trail " + name + " registered; " + j.edits.size() + " block changes queued. ", ChatFormatting.GREEN)
                .append(Ui.button("Undo", "/resort trail undo " + j.id, "Restore every block this job changed")));
    }

    public static void undo(ServerPlayer p, String journalId) {
        Resort r = ResortServer.resort();
        EditJournal j = r.journals.get(journalId);
        if (j == null) { p.sendSystemMessage(Ui.text("No such earthwork job.", ChatFormatting.RED)); return; }
        if (j.undoing) { p.sendSystemMessage(Ui.text("Already undone.", ChatFormatting.GRAY)); return; }
        j.undoing = true;
        p.sendSystemMessage(Ui.text("Undoing " + j.label + " (" + j.applied + " changes)…", ChatFormatting.YELLOW));
    }

    static EarthworkPlanner.World world(ServerLevel level) {
        return new EarthworkPlanner.World() {
            public String spec(int x, int y, int z) { return WorldAdapter.spec(level, x, y, z); }
            public boolean isTree(String s) { return s.contains("_log") || s.contains("_leaves") || s.contains("_wood"); }
            public boolean isVegetation(String s) {
                return s.contains("grass") && !s.contains("grass_block") || s.contains("fern") || s.contains("bush") || s.contains("flower")
                        || s.contains("sapling") || s.startsWith("minecraft:snow[");
            }
            public boolean isAir(String s) { return s.equals("minecraft:air") || s.equals("minecraft:cave_air"); }
        };
    }

    public static void tick(ServerLevel level, Resort r) {
        int budget = r.config.earthworkBlocksPerTick;
        for (EditJournal j : r.journals.values()) {
            if (j.done() || budget <= 0) continue;
            List<EditJournal.Edit> batch = j.nextBatch(budget);
            int applied = 0;
            for (EditJournal.Edit e : batch) {
                BlockPos pos = new BlockPos(e.x(), e.y(), e.z());
                if (!level.isLoaded(pos)) break;
                WorldAdapter.setSpec(level, e.x(), e.y(), e.z(), e.after());
                applied++;
            }
            j.advance(applied);
            budget -= Math.max(1, applied);
            if (j.done()) {
                Trail t = r.trails.get(j.trailId);
                if (t != null) r.updateTrail(t, new TrailDesign().design(t.centerline, t.width, terrain(level)));
                r.notice((j.undoing ? "Undo of " : "Earthwork on ") + j.label + " finished.");
            }
        }
        // Preview particles.
        if (level.getGameTime() % 10 != 0) return;
        for (var e : previews.entrySet()) {
            if (previewUntil.getOrDefault(e.getKey(), 0L) < level.getGameTime()) continue;
            TrailDesign.Result d = e.getValue();
            for (TrailDesign.Column c : d.columns.values()) {
                boolean edge = !d.columns.containsKey(dev.blockside.core.Pos.columnKey(c.x + 1, c.z)) || !d.columns.containsKey(dev.blockside.core.Pos.columnKey(c.x - 1, c.z))
                        || !d.columns.containsKey(dev.blockside.core.Pos.columnKey(c.x, c.z + 1)) || !d.columns.containsKey(dev.blockside.core.Pos.columnKey(c.x, c.z - 1));
                if (edge && (c.x + c.z) % 2 == 0) Particles.at(level, ParticleTypes.HAPPY_VILLAGER, c.x + 0.5, c.design + 0.2, c.z + 0.5);
            }
            for (TrailFlag f : d.flags) Particles.at(level, ParticleTypes.FLAME, f.at().x() + 0.5, f.at().y() + 0.5, f.at().z() + 0.5);
        }
    }

    public static void list(ServerPlayer p) {
        Resort r = ResortServer.resort();
        p.sendSystemMessage(Ui.title("Runs"));
        if (r.trails.isEmpty()) p.sendSystemMessage(Ui.text("No runs yet. Use the Survey Stake, then /resort trail preview.", ChatFormatting.GRAY));
        r.refreshTrailStats();
        for (Trail t : r.trails.values()) {
            p.sendSystemMessage(Ui.text(String.format("%s — %s, %.0f°/%.0f°, %.0f%% covered, %.0f%% groomed ", t.name, t.difficulty.label,
                    t.avgGradeDeg, t.maxGradeDeg, t.coverage * 100, t.groomedFraction * 100), t.open ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                    .append(t.open ? Ui.button("Close", "/resort trail close " + t.id, "Close this run") : Ui.button("Open", "/resort trail open " + t.id, "Open this run")));
        }
    }
}
