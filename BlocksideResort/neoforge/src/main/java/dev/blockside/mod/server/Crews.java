package dev.blockside.mod.server;

import dev.blockside.core.Resort;
import dev.blockside.core.construction.Job;
import dev.blockside.core.construction.JobState;
import dev.blockside.core.construction.Placement;
import dev.blockside.mod.BlocksideMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/** The visible crew worker on each active job site, and the zone outline particles. */
public final class Crews {
    private static final Map<String, Villager> workers = new HashMap<>();
    private static final Map<String, BlockPos> lastWork = new HashMap<>();
    private Crews() {}

    public static void clear() {
        workers.values().forEach(v -> v.discard());
        workers.clear();
        lastWork.clear();
    }

    public static void worked(ServerLevel level, Placement p) {
        level.sendParticles(ParticleTypes.POOF, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
        Resort r = ResortServer.resort();
        for (Job j : r.jobs.values())
            if (j.state == JobState.ACTIVE && j.cursor < j.total() && j.placements.get(Math.max(0, j.cursor)) == p)
                lastWork.put(j.id, new BlockPos(p.x(), p.y(), p.z()));
    }

    public static void tick(ServerLevel level, Resort r) {
        for (Job j : r.jobs.values()) {
            boolean onSite = j.state == JobState.ACTIVE || j.state == JobState.BLOCKED;
            Villager v = workers.get(j.id);
            if (!onSite) {
                if (v != null) { v.discard(); workers.remove(j.id); }
                continue;
            }
            BlockPos anchor = new BlockPos(j.anchor.x(), j.anchor.y(), j.anchor.z());
            if (!level.isLoaded(anchor)) continue;
            if (v == null || v.isRemoved()) {
                v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
                if (v == null) continue;
                var crew = r.crews.get(j.crewId);
                v.setCustomName(Component.literal(crew == null ? "Crew" : crew.name));
                v.setCustomNameVisible(true);
                v.setNoAi(true);
                v.setPermanentlyInvulnerable(true);
                v.setSilent(true);
                v.addTag(BlocksideMod.TRANSIENT_TAG);
                v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
                v.setPos(anchor.getX() + 1.5, anchor.getY(), anchor.getZ() + 1.5);
                level.addFreshEntity(v);
                workers.put(j.id, v);
            }
            // Walk the worker to wherever the job is happening (cursor position).
            if (level.getGameTime() % 40 == 0 && j.placements != null && j.cursor < j.total()) {
                Placement p = j.placements.get(j.cursor);
                int gx = p.x() + 1, gz = p.z() + 1;
                double gy = WorldAdapter.top(level, gx, gz);
                if (Math.abs(gy - p.y()) > 6) gy = p.y();
                v.setPos(gx + 0.5, gy, gz + 0.5);
                v.setYRot((float) Math.toDegrees(Math.atan2(-(p.x() - gx), p.z() - gz)));
                v.setYHeadRot(v.getYRot());
            }
        }
        workers.entrySet().removeIf(e -> {
            if (!r.jobs.containsKey(e.getKey())) { e.getValue().discard(); return true; }
            return false;
        });
    }

    /** Planned/active zones are outlined so everyone can see the reserved footprint. */
    public static void drawZones(ServerLevel level, Resort r) {
        for (Job j : r.jobs.values()) {
            if (j.state == JobState.COMPLETE || j.zone == null) continue;
            if (level.getNearestPlayer(j.anchor.x(), j.anchor.y(), j.anchor.z(), 48, false) == null) continue;
            Particles.outline(level, j.state == JobState.BLOCKED ? ParticleTypes.FLAME : ParticleTypes.WAX_ON, j.zone, j.anchor.y() + 0.2);
        }
    }
}
