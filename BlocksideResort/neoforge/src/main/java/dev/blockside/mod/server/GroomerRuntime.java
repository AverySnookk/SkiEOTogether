package dev.blockside.mod.server;

import dev.blockside.core.Facility;
import dev.blockside.core.Resort;
import dev.blockside.core.snow.SnowTile;
import dev.blockside.mod.BlocksideMod;
import dev.blockside.mod.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drivable snowcat. Server-controlled vehicle (W/S throttle, A/D steer, Space toggles the tiller,
 * Shift to get out). It drives on the same visible snow surface the skier uses, and grooms every tile the
 * tiller swept since the previous simulation step.
 */
public final class GroomerRuntime {
    private static final class Cat {
        Display.BlockDisplay body, tiller;
        UUID driver;
        double x, y, z, yaw, speed, lastGroomX, lastGroomZ;
        boolean tillerDown, jumpWasDown;
        int sinceGroom;
    }

    private static final Map<UUID, Cat> cats = new HashMap<>();
    private GroomerRuntime() {}

    public static void clear() {
        cats.values().forEach(c -> { c.body.discard(); c.tiller.discard(); });
        cats.clear();
    }

    /** Take the snowcat out of the nearest garage (or spawn at the player in creative). */
    public static void dispatch(ServerLevel level, ServerPlayer p, Resort r) {
        if (cats.containsKey(p.getUUID())) { p.sendSystemMessage(Ui.text("You're already driving.", ChatFormatting.GRAY)); return; }
        Facility garage = null;
        double best = Double.MAX_VALUE;
        for (Facility f : r.facilities.values()) {
            if (!"groomer_garage".equals(f.catalogId) || !f.open) continue;
            double d = p.distanceToSqr(f.anchor.x(), f.anchor.y(), f.anchor.z());
            if (d < best) { best = d; garage = f; }
        }
        double sx, sz;
        if (garage != null && garage.entrance != null && best < 64 * 64) {
            sx = garage.entrance.x() + 0.5 + (garage.entrance.x() - garage.anchor.x()) * 0.5;
            sz = garage.entrance.z() + 0.5 + (garage.entrance.z() - garage.anchor.z()) * 0.5;
        } else if (p.isCreative()) {
            sx = p.getX(); sz = p.getZ();
        } else {
            p.sendSystemMessage(Ui.text("Build a Groomer Garage first (or stand within 64 blocks of one).", ChatFormatting.RED));
            return;
        }
        Cat c = new Cat();
        c.x = sx; c.z = sz; c.y = WorldAdapter.surface(level, (int) Math.floor(sx), (int) Math.floor(sz));
        c.yaw = p.getYRot();
        c.lastGroomX = c.x; c.lastGroomZ = c.z;
        c.driver = p.getUUID();
        c.body = display(level, Blocks.RED_CONCRETE.defaultBlockState(), -1.3F, -1.3F, -1.6F, 2.6F, 1.3F, 3.2F);
        c.tiller = display(level, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), -1.5F, 0F, -2.6F, 3.0F, 0.5F, 0.9F);
        if (c.body == null || c.tiller == null) return;
        place(c);
        if (!Compat.startRiding(p, c.body)) {
            c.body.discard(); c.tiller.discard();
            p.sendSystemMessage(Ui.text("Couldn't get into the groomer (vehicle riding unsupported on this version).", ChatFormatting.RED));
            return;
        }
        cats.put(p.getUUID(), c);
        p.sendSystemMessage(Ui.text("Snowcat: W/S throttle, A/D steer, SPACE lowers/raises the tiller, SHIFT to park.", ChatFormatting.AQUA));
    }

    private static Display.BlockDisplay display(ServerLevel level, net.minecraft.world.level.block.state.BlockState s,
                                                 float tx, float ty, float tz, float sx, float sy, float sz) {
        Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (d == null) return null;
        Compat.setDisplayBlock(d, s);
        Compat.setDisplayBox(d, tx, ty, tz, sx, sy, sz);
        Compat.setDisplayInterpolation(d, 2);
        d.addTag(BlocksideMod.TRANSIENT_TAG);
        level.addFreshEntity(d);
        return d;
    }

    private static void place(Cat c) {
        c.body.setPos(c.x, c.y + 1.3, c.z); // rider sits on the roof of the body box
        c.body.setYRot((float) c.yaw);
        c.tiller.setPos(c.x, c.y + (c.tillerDown ? 0.05 : 0.5), c.z);
        c.tiller.setYRot((float) c.yaw);
    }

    public static void tick(ServerLevel level, Resort r) {
        var it = cats.entrySet().iterator();
        while (it.hasNext()) {
            Cat c = it.next().getValue();
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(c.driver);
            if (p == null || p.getVehicle() != c.body) {
                c.body.discard(); c.tiller.discard();
                it.remove();
                if (p != null) p.sendSystemMessage(Ui.text("Snowcat parked.", ChatFormatting.GRAY));
                continue;
            }
            boolean[] in = Compat.lastInput(p);
            boolean fw, back, left, right, jump;
            if (in != null) { fw = in[0]; back = in[1]; left = in[2]; right = in[3]; jump = in[4]; }
            else { // Fallback: look down to drive, steer with the mouse.
                fw = p.getXRot() > 20; back = p.getXRot() < -40; jump = false;
                double d = Math.IEEEremainder(p.getYRot() - c.yaw, 360);
                left = d < -5; right = d > 5;
            }
            double dt = 0.05;
            if (fw) c.speed += 3.0 * dt; else if (back) c.speed -= 4.0 * dt; else c.speed *= 0.9;
            c.speed = Math.max(-2.5, Math.min(c.tillerDown ? 4.5 : 6.5, c.speed));
            double turn = 70 * dt * Math.min(1, Math.abs(c.speed) / 1.5 + 0.3);
            if (left) c.yaw -= turn;
            if (right) c.yaw += turn;
            if (jump && !c.jumpWasDown) {
                c.tillerDown = !c.tillerDown;
                p.sendSystemMessage(Ui.text(c.tillerDown ? "Tiller down: grooming." : "Tiller up.", ChatFormatting.AQUA), true);
            }
            c.jumpWasDown = jump;
            double r0 = Math.toRadians(c.yaw);
            double nx = c.x - Math.sin(r0) * c.speed * dt, nz = c.z + Math.cos(r0) * c.speed * dt;
            double ny = WorldAdapter.surface(level, (int) Math.floor(nx), (int) Math.floor(nz));
            if (ny - c.y > 1.1) c.speed = 0; // wall
            else { c.x = nx; c.z = nz; c.y = c.y + (ny - c.y) * 0.5; }
            place(c);
            if (c.tillerDown && ++c.sinceGroom >= 5) {
                c.sinceGroom = 0;
                // The tiller trails 2 blocks behind the cab.
                double tx = c.x + Math.sin(r0) * 2, tz = c.z - Math.cos(r0) * 2;
                List<SnowTile> touched = r.groomPath(c.lastGroomX, c.lastGroomZ, tx, tz, 1.5);
                c.lastGroomX = tx; c.lastGroomZ = tz;
                for (SnowTile t : touched) SnowRenderer.render(level, t, r.config.snow);
                if (!touched.isEmpty()) level.sendParticles(ParticleTypes.SNOWFLAKE, tx, c.y + 0.3, tz, 6, 1.2, 0.1, 1.2, 0.02);
            } else if (!c.tillerDown) {
                c.lastGroomX = c.x + Math.sin(r0) * 2; c.lastGroomZ = c.z - Math.cos(r0) * 2;
            }
        }
    }
}
