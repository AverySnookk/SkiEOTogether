package dev.blockside.mod.server;

import dev.blockside.core.ActionResult;
import dev.blockside.core.Box;
import dev.blockside.core.Pos;
import dev.blockside.core.Resort;
import dev.blockside.core.catalog.CatalogEntry;
import dev.blockside.core.catalog.EntryKind;
import dev.blockside.core.catalog.Template;
import dev.blockside.core.construction.PlacementCheck;
import dev.blockside.core.construction.Purchase;
import dev.blockside.core.construction.Rotations;
import dev.blockside.core.construction.TemplatePlacer;
import dev.blockside.core.lift.LiftDesign;
import dev.blockside.core.lift.LiftLoop;
import dev.blockside.mod.Registry;
import dev.blockside.mod.item.ProjectKitItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Project kit preview → rotate → confirm flow, per player. */
public final class Placement {
    private static final class Pending {
        String purchaseId;
        CatalogEntry entry;
        Pos anchor, lower, upper;
        int rot;
        long expires;
        PlacementCheck check;
        LiftDesign design;
    }

    private static final Map<UUID, Pending> pending = new HashMap<>();
    private Placement() {}

    public static void kitUsed(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos clicked, Direction face) {
        Resort r = ResortServer.resort();
        if (r == null) return;
        String pid = ProjectKitItem.purchaseId(stack);
        Purchase pu = r.purchases.get(pid);
        if (pu == null || pu.state != Purchase.State.KIT) {
            player.sendSystemMessage(Ui.text("This kit isn't valid any more (already placed or refunded).", ChatFormatting.RED));
            return;
        }
        CatalogEntry e = r.catalog.get(pu.catalogId);
        BlockPos target = face == Direction.UP ? clicked : clicked.relative(face);
        int x = target.getX(), z = target.getZ();
        Pos at = new Pos(x, WorldAdapter.top(level, x, z), z);
        Pending p = pending.get(player.getUUID());
        if (p == null || !p.purchaseId.equals(pid)) { p = new Pending(); p.purchaseId = pid; p.entry = e; pending.put(player.getUUID(), p); }
        p.expires = level.getGameTime() + 1200;
        if (e.kind == EntryKind.LIFT) {
            if (p.lower == null || (p.upper == null && dist(p.lower, at) < 8)) {
                p.lower = at;
                p.upper = null;
                p.design = null;
                player.sendSystemMessage(Ui.text("Lower station set at " + at + ". Now right-click the spot for the upper station (uphill).", ChatFormatting.YELLOW));
                Template lt = r.catalog.template(e.template);
                p.check = new PlacementCheck();
                p.anchor = at;
                p.rot = Rotations.facing(player.getLookAngle().x, player.getLookAngle().z);
                return;
            }
            p.upper = at;
            evaluateLift(level, r, p);
            showLift(player, r, p);
            return;
        }
        if (at.equals(p.anchor)) p.rot = (p.rot + 1) % 4;
        else { p.anchor = at; p.rot = Rotations.facing(player.getLookAngle().x, player.getLookAngle().z); }
        p.check = r.checkBuilding(pid, p.anchor, p.rot, WorldAdapter.probe(level));
        showBuilding(player, r, p);
    }

    private static double dist(Pos a, Pos b) { return Math.hypot(a.x() - b.x(), a.z() - b.z()); }

    private static void evaluateLift(ServerLevel level, Resort r, Pending p) {
        p.design = r.planLift(p.purchaseId, p.lower, p.upper, (x, z) -> WorldAdapter.top(level, x, z));
        p.check = r.checkLift(p.purchaseId, p.lower, p.upper, (x, z) -> WorldAdapter.top(level, x, z), WorldAdapter.probe(level));
        p.rot = Rotations.facing(p.upper.x() - p.lower.x(), p.upper.z() - p.lower.z());
    }

    private static void showBuilding(ServerPlayer player, Resort r, Pending p) {
        Template t = r.catalog.template(p.entry.template);
        player.sendSystemMessage(Ui.title(p.entry.name + " preview"));
        player.sendSystemMessage(Ui.text(String.format("Footprint %d×%d, entrance facing %s. %s", t.sx(), t.sz(),
                Rotations.name(p.rot + 2), p.entry.description), ChatFormatting.GRAY));
        sendCheck(player, p.check);
        MutableComponent row = Ui.text("");
        row.append(p.check.ok() ? Ui.button("Confirm", "/resort place confirm", "Create the construction zone here")
                : Ui.disabled("Confirm", p.check.summary()));
        row.append(" ").append(Ui.button("Rotate", "/resort place rotate", "Turn 90°"))
                .append(" ").append(Ui.button("Cancel", "/resort place cancel", "Stop previewing"));
        player.sendSystemMessage(row);
    }

    private static void showLift(ServerPlayer player, Resort r, Pending p) {
        LiftDesign d = p.design;
        player.sendSystemMessage(Ui.title(p.entry.name + " line"));
        player.sendSystemMessage(Ui.text(String.format("Length %.0f m, rise %.0f m (%.0f°), %d towers, min chair clearance %.1f m",
                d.horizontalLength, d.rise, d.angleDeg, d.towerTops.size(), d.minClearanceFound == Double.MAX_VALUE ? 0 : d.minClearanceFound), ChatFormatting.GRAY));
        sendCheck(player, p.check);
        MutableComponent row = Ui.text("");
        row.append(p.check.ok() ? Ui.button("Confirm", "/resort place confirm", "Create the lift construction zone")
                : Ui.disabled("Confirm", p.check.summary()));
        row.append(" ").append(Ui.button("Reset", "/resort place reset", "Pick both stations again"))
                .append(" ").append(Ui.button("Cancel", "/resort place cancel", "Stop previewing"));
        player.sendSystemMessage(row);
    }

    private static void sendCheck(ServerPlayer player, PlacementCheck c) {
        if (c.ok()) player.sendSystemMessage(Ui.text("✔ Site OK. " + String.join(" ", c.notes), ChatFormatting.GREEN));
        else for (String reason : c.reasons) player.sendSystemMessage(Ui.text("✘ " + reason, ChatFormatting.RED));
    }

    public static void command(ServerPlayer player, String action) {
        Resort r = ResortServer.resort();
        Pending p = pending.get(player.getUUID());
        if (p == null) { player.sendSystemMessage(Ui.text("Right-click the ground with a project kit first.", ChatFormatting.RED)); return; }
        ServerLevel level = ResortServer.level();
        switch (action) {
            case "cancel" -> { pending.remove(player.getUUID()); player.sendSystemMessage(Ui.text("Preview cancelled.", ChatFormatting.GRAY)); }
            case "reset" -> { p.lower = null; p.upper = null; p.design = null; player.sendSystemMessage(Ui.text("Pick the lower station again.", ChatFormatting.GRAY)); }
            case "rotate" -> {
                if (p.entry.kind == EntryKind.LIFT) return;
                p.rot = (p.rot + 1) % 4;
                p.check = r.checkBuilding(p.purchaseId, p.anchor, p.rot, WorldAdapter.probe(level));
                showBuilding(player, r, p);
            }
            case "confirm" -> confirm(level, player, r, p);
            default -> {}
        }
    }

    private static void confirm(ServerLevel level, ServerPlayer player, Resort r, Pending p) {
        int slot = findKit(player, p.purchaseId);
        if (slot < 0) { player.sendSystemMessage(Ui.text("You need the kit in your inventory.", ChatFormatting.RED)); return; }
        ActionResult res;
        String who = ResortServer.who(player);
        if (p.entry.kind == EntryKind.LIFT) {
            if (p.upper == null) { player.sendSystemMessage(Ui.text("Pick the upper station first.", ChatFormatting.RED)); return; }
            res = r.placeLift(p.purchaseId, p.lower, p.upper, (x, z) -> WorldAdapter.top(level, x, z), WorldAdapter.probe(level), who, ResortServer.now());
        } else {
            res = r.placeBuilding(p.purchaseId, p.anchor, p.rot, WorldAdapter.probe(level), who, ResortServer.now());
        }
        ResortServer.reply(player, res);
        if (!res.ok()) return;
        player.getInventory().removeItem(slot, 1);
        var job = r.jobs.get(res.ref());
        setJobBlock(level, job.anchor);
        if (job.liftTop != null) setJobBlock(level, job.liftTop);
        pending.remove(player.getUUID());
        player.sendSystemMessage(Ui.text("Right-click the yellow center block to hire a crew. ", ChatFormatting.YELLOW)
                .append(Ui.button("Hire now", "/resort hire " + job.id, "Quote: " + Ui.money(r.hireQuote(job)))));
    }

    private static void setJobBlock(ServerLevel level, Pos p) {
        level.setBlock(new BlockPos(p.x(), p.y(), p.z()), Registry.JOB_BLOCK.get().defaultBlockState(), 3);
    }

    private static int findKit(ServerPlayer player, String pid) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Registry.PROJECT_KIT.get()) && pid.equals(ProjectKitItem.purchaseId(s))) return i;
        }
        return -1;
    }

    /** Redraws previews twice a second. */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 10 != 0 || pending.isEmpty()) return;
        Resort r = ResortServer.resort();
        pending.values().removeIf(p -> p.expires < level.getGameTime());
        for (Pending p : pending.values()) {
            if (p.entry.kind == EntryKind.LIFT) {
                if (p.lower != null) {
                    Template lt = r.catalog.template(p.entry.template);
                    int rot = p.upper == null ? p.rot : Rotations.facing(p.upper.x() - p.lower.x(), p.upper.z() - p.lower.z());
                    Particles.outline(level, ParticleTypes.HAPPY_VILLAGER, TemplatePlacer.footprint(lt, p.lower.x(), p.lower.y(), p.lower.z(), rot), p.lower.y() + 0.1);
                }
                if (p.design != null) {
                    var lp = r.config.lift;
                    LiftLoop loop = new LiftLoop(p.design.supportPoints(lp), lp.lineOffset);
                    for (double s = 0; s < loop.upLength(); s += 1.5) {
                        double[] c = loop.at(s);
                        Particles.at(level, ParticleTypes.END_ROD, c[0], c[1], c[2]);
                        Particles.at(level, p.check.ok() ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE, c[0], c[1] - lp.hang, c[2]);
                    }
                    for (Pos t : p.design.towerTops)
                        for (int y = WorldAdapter.top(level, t.x(), t.z()); y <= t.y(); y += 2) Particles.at(level, ParticleTypes.CLOUD, t.x() + 0.5, y, t.z() + 0.5);
                    Template ut = r.catalog.template(p.entry.upperTemplate);
                    Particles.outline(level, ParticleTypes.HAPPY_VILLAGER, TemplatePlacer.footprint(ut, p.upper.x(), p.upper.y(), p.upper.z(), p.rot), p.upper.y() + 0.1);
                }
            } else if (p.anchor != null) {
                Template t = r.catalog.template(p.entry.template);
                Box fp = TemplatePlacer.footprint(t, p.anchor.x(), p.anchor.y(), p.anchor.z(), p.rot);
                Particles.outline(level, p.check.ok() ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.FLAME, fp, p.anchor.y() + 0.1);
                if (t.entrance != null) {
                    int[] w = TemplatePlacer.toWorld(t, t.entrance, p.anchor.x(), p.anchor.y(), p.anchor.z(), p.rot);
                    for (int i = 0; i < 3; i++) Particles.at(level, ParticleTypes.SOUL_FIRE_FLAME, w[0] + 0.5, w[1] + i * 0.6, w[2] + 0.5);
                }
                Particles.at(level, ParticleTypes.END_ROD, p.anchor.x() + 0.5, p.anchor.y() + 0.5, p.anchor.z() + 0.5);
            }
            if (p.check != null) for (Pos b : p.check.blockedAt) Particles.at(level, ParticleTypes.FLAME, b.x() + 0.5, b.y() + 0.5, b.z() + 0.5);
        }
    }
}
