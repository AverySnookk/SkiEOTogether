package dev.blockside.mod.server;

import dev.blockside.core.Facility;
import dev.blockside.core.Resort;
import dev.blockside.core.snow.Weather;
import dev.blockside.mod.BlocksideMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.List;

/** A handful of visible guests milling around the ticket booth while the aggregate model has people on the hill. */
public final class Guests {
    private record Guest(Villager v, long until) {}
    private static final List<Guest> visible = new ArrayList<>();
    private Guests() {}

    public static void clear() { visible.forEach(g -> g.v.discard()); visible.clear(); }

    public static void tick(ServerLevel level, Resort r) {
        long now = level.getGameTime();
        visible.removeIf(g -> { if (g.until < now || g.v.isRemoved()) { g.v.discard(); return true; } return false; });
        double hour = Weather.hourOfDay(level.getDayTime());
        if (r.guestsOnMountain < 1 || hour < r.config.openHour || hour >= r.config.closeHour) return;
        for (Facility f : r.facilities.values()) {
            if (!"ticket_booth".equals(f.catalogId) || f.entrance == null) continue;
            if (level.getNearestPlayer(f.anchor.x(), f.anchor.y(), f.anchor.z(), 48, false) == null) continue;
            int want = (int) Math.min(r.config.maxVisibleGuests, Math.ceil(r.guestsOnMountain / 10.0));
            if (visible.size() >= want) continue;
            Villager v = EntityType.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (v == null) continue;
            v.setCustomName(Component.literal("Guest"));
            v.addTag(BlocksideMod.TRANSIENT_TAG);
            double ox = level.getRandom().nextDouble() * 4 - 2, oz = level.getRandom().nextDouble() * 4 - 2;
            int gx = (int) Math.floor(f.entrance.x() + 0.5 + ox), gz = (int) Math.floor(f.entrance.z() + 0.5 + oz);
            v.setPos(gx + 0.5, WorldAdapter.surface(level, gx, gz), gz + 0.5);
            level.addFreshEntity(v);
            visible.add(new Guest(v, now + 1800));
        }
    }
}
