package dev.blockside.mod.server;

import dev.blockside.core.LiftRecord;
import dev.blockside.core.Resort;
import dev.blockside.core.lift.LiftLoop;
import dev.blockside.core.lift.LiftParams;
import dev.blockside.mod.BlocksideMod;
import dev.blockside.mod.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chairs ride one server-controlled loop per lift: chair i sits at loop distance phase + i·spacing.
 * Players board by standing on the loading mark; the chair carries them up and lets them off at the top.
 */
public final class LiftRuntime {
    private record Chair(Display.BlockDisplay seat, Display.BlockDisplay hanger) {
        void discard() { seat.discard(); hanger.discard(); }
    }
    private record Ride(String liftId, int chair) {}

    private static final Map<String, List<Chair>> chairs = new HashMap<>();
    private static final Map<UUID, Ride> riders = new HashMap<>();
    private static final Map<UUID, Long> cooldown = new HashMap<>();
    private LiftRuntime() {}

    public static void clear() {
        chairs.values().forEach(l -> l.forEach(Chair::discard));
        chairs.clear();
        riders.clear();
    }

    public static void tick(ServerLevel level, Resort r) {
        LiftParams lp = r.config.lift;
        for (LiftRecord l : r.lifts.values()) {
            LiftLoop loop = l.loop(lp);
            boolean near = level.getNearestPlayer(l.lower.x(), l.lower.y(), l.lower.z(), 128, false) != null
                    || level.getNearestPlayer(l.upper.x(), l.upper.y(), l.upper.z(), 128, false) != null;
            if (!l.open || !near) {
                despawn(l.id);
                if (!l.open) ejectAll(level, l);
                continue;
            }
            double step = l.speed / 20.0;
            double prevPhase = l.phase;
            l.phase = (l.phase + step) % loop.length();
            int n = l.chairCount(lp);
            double spacing = loop.length() / n;
            List<Chair> list = chairs.computeIfAbsent(l.id, k -> new ArrayList<>());
            while (list.size() < n) { Chair c = spawnChair(level); if (c == null) break; list.add(c); }
            double[] dir = {l.upper.x() - l.lower.x(), l.upper.z() - l.lower.z()};
            float upYaw = (float) Math.toDegrees(Math.atan2(-dir[0], dir[1]));
            for (int i = 0; i < list.size(); i++) {
                double s = (l.phase + i * spacing) % loop.length();
                double[] c = loop.at(s);
                Chair ch = list.get(i);
                float yaw = loop.ascending(s) ? upYaw : upYaw + 180;
                ch.seat.setPos(c[0], c[1] - lp.hang, c[2]);
                ch.seat.setYRot(yaw);
                ch.hanger.setPos(c[0], c[1] - lp.hang, c[2]);
                ch.hanger.setYRot(yaw);
                double prevS = (prevPhase + i * spacing) % loop.length();
                // Loading: the chair just swung past the start of the up-line.
                if (prevS > s) boardAt(level, l, loop, i, ch);
                // Unloading: chair passes the top of the up-line.
                if (prevS < loop.upLength() && s >= loop.upLength()) unload(level, l, loop, i);
            }
            enforceSafetyBar(level, l, loop, list, spacing);
        }
        chairs.keySet().removeIf(id -> { if (!r.lifts.containsKey(id)) { chairs.get(id).forEach(Chair::discard); return true; } return false; });
    }

    private static Chair spawnChair(ServerLevel level) {
        Display.BlockDisplay seat = net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        Display.BlockDisplay hanger = net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (seat == null || hanger == null) return null;
        Compat.setDisplayBlock(seat, Blocks.DARK_OAK_SLAB.defaultBlockState());
        Compat.setDisplayBox(seat, -0.5F, -0.6F, -0.45F, 1.0F, 0.5F, 0.9F);
        Compat.setDisplayBlock(hanger, Blocks.IRON_BARS.defaultBlockState());
        Compat.setDisplayBox(hanger, -0.1F, -0.1F, -0.1F, 0.2F, 2.6F, 0.2F);
        for (Display d : new Display[]{seat, hanger}) {
            Compat.setDisplayInterpolation(d, 2);
            d.addTag(BlocksideMod.TRANSIENT_TAG);
            level.addFreshEntity(d);
        }
        return new Chair(seat, hanger);
    }

    private static void despawn(String id) {
        List<Chair> list = chairs.remove(id);
        if (list != null) list.forEach(Chair::discard);
    }

    private static void boardAt(ServerLevel level, LiftRecord l, LiftLoop loop, int i, Chair ch) {
        if (!ch.seat.getPassengers().isEmpty()) return;
        double[] start = loop.at(0);
        for (ServerPlayer p : level.players()) {
            if (p.isPassenger() || p.isShiftKeyDown() || p.isSpectator()) continue;
            if (cooldown.getOrDefault(p.getUUID(), 0L) > level.getGameTime()) continue;
            double dx = p.getX() - start[0], dz = p.getZ() - start[2];
            if (dx * dx + dz * dz > 2.6 || Math.abs(p.getY() - l.lower.y()) > 1.6) continue;
            if (Compat.startRiding(p, ch.seat)) {
                riders.put(p.getUUID(), new Ride(l.id, i));
                p.sendSystemMessage(Ui.text("Riding " + l.name + ". Safety bar down — you'll be let off at the top.", ChatFormatting.AQUA));
            }
            return;
        }
    }

    private static void unload(ServerLevel level, LiftRecord l, LiftLoop loop, int i) {
        for (var e : new ArrayList<>(riders.entrySet())) {
            if (!e.getValue().liftId().equals(l.id) || e.getValue().chair() != i) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            riders.remove(e.getKey());
            if (p == null) continue;
            p.stopRiding();
            double dx = l.upper.x() - l.lower.x(), dz = l.upper.z() - l.lower.z(), len = Math.hypot(dx, dz);
            double ux = l.upper.x() + 0.5 + dx / len * 3.5, uz = l.upper.z() + 0.5 + dz / len * 3.5;
            p.teleportTo(ux, WorldAdapter.surface(level, (int) Math.floor(ux), (int) Math.floor(uz)) + 0.05, uz);
            cooldown.put(p.getUUID(), level.getGameTime() + 100);
            p.sendSystemMessage(Ui.text("Summit! Clip in your skis (right-click) and pick a run.", ChatFormatting.AQUA));
            l.ridesToday++;
        }
    }

    private static void ejectAll(ServerLevel level, LiftRecord l) {
        for (var e : new ArrayList<>(riders.entrySet())) {
            if (!e.getValue().liftId().equals(l.id)) continue;
            riders.remove(e.getKey());
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            p.stopRiding();
            p.teleportTo(l.lower.x() + 0.5, l.lower.y(), l.lower.z() + 2.5);
            p.sendSystemMessage(Ui.text("The lift was closed; you've been brought back to the bottom.", ChatFormatting.YELLOW));
        }
    }

    private static void enforceSafetyBar(ServerLevel level, LiftRecord l, LiftLoop loop, List<Chair> list, double spacing) {
        for (var e : new ArrayList<>(riders.entrySet())) {
            if (!e.getValue().liftId().equals(l.id)) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null) { riders.remove(e.getKey()); continue; }
            int i = e.getValue().chair();
            if (i >= list.size()) { riders.remove(e.getKey()); continue; }
            Chair ch = list.get(i);
            if (p.getVehicle() != ch.seat) Compat.startRiding(p, ch.seat);
        }
    }
}
