package dev.blockside.mod.server;

import dev.blockside.core.ActionResult;
import dev.blockside.core.Json;
import dev.blockside.core.Pos;
import dev.blockside.core.Resort;
import dev.blockside.core.ResortConfig;
import dev.blockside.core.SnowGun;
import dev.blockside.core.Snapshot;
import dev.blockside.core.catalog.Catalog;
import dev.blockside.core.snow.SnowTile;
import dev.blockside.mod.BlocksideMod;
import dev.blockside.mod.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the one {@link Resort} for this world and drives it from the server tick. Everything that changes
 * resort state runs here on the server thread; clients only ever receive {@link Snapshot}s.
 */
public final class ResortServer {
    private static MinecraftServer server;
    private static Resort resort;
    private static Path dir;
    private static final Set<UUID> skiing = new HashSet<>();
    private static long ticks;
    public static volatile String lastError = "none";
    public static long ticks() { return ticks; }

    private ResortServer() {}

    public static Resort resort() { return resort; }
    public static MinecraftServer server() { return server; }
    public static ServerLevel level() { return server.overworld(); }
    public static long now() { return level().getOverworldClockTime(); }
    public static String who(Player p) { return p.getName().getString(); }

    /** Sets the overworld clock (26.x replaced level day time with world clocks). */
    public static void setClock(long ticks) {
        level().registryAccess().get(net.minecraft.world.clock.WorldClocks.OVERWORLD)
                .ifPresent(clock -> server.clockManager().setTotalTicks(clock, ticks));
    }

    // ------------------------------------------------------------------ lifecycle / persistence

    public static void start(MinecraftServer s) {
        server = s;
        dir = s.getWorldPath(LevelResource.ROOT).resolve("blockside");
        try {
            Files.createDirectories(dir);
            ResortConfig config = loadConfig();
            Catalog catalog = loadCatalog();
            Path save = dir.resolve("resort.json");
            if (Files.exists(save)) {
                resort = Resort.fromJson(Files.readString(save, StandardCharsets.UTF_8), config, catalog);
                BlocksideMod.LOG.info("[Blockside] Loaded resort: ${} cash, {} jobs, {} lifts, {} trails",
                        resort.ledger.cash(), resort.jobs.size(), resort.lifts.size(), resort.trails.size());
            } else {
                resort = new Resort(config, catalog);
                BlocksideMod.LOG.info("[Blockside] New resort with ${}", config.startingCash);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Blockside: cannot load resort", e);
        }
    }

    /** Server operators can tune by editing world/blockside/resort_config.json and catalog.json (written on first start). */
    private static ResortConfig loadConfig() throws IOException {
        Path p = dir.resolve("resort_config.json");
        if (!Files.exists(p)) copyDefault("blockside/resort_config.json", p);
        return Json.GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), ResortConfig.class);
    }

    private static Catalog loadCatalog() throws IOException {
        Path p = dir.resolve("catalog.json");
        if (!Files.exists(p)) copyDefault("blockside/catalog.json", p);
        Path tdir = dir.resolve("templates");
        return Catalog.load(path -> {
            try {
                if (path.equals("blockside/catalog.json")) return Files.newInputStream(p);
                Path override = tdir.resolve(path.substring(path.lastIndexOf('/') + 1));
                if (Files.exists(override)) return Files.newInputStream(override);
                return ResortServer.class.getClassLoader().getResourceAsStream(path);
            } catch (IOException e) { throw new IllegalStateException(e); }
        });
    }

    private static void copyDefault(String res, Path to) throws IOException {
        try (InputStream in = ResortServer.class.getClassLoader().getResourceAsStream(res)) {
            if (in != null) Files.copy(in, to);
        }
    }

    public static synchronized void save() {
        if (resort == null || dir == null) return;
        try {
            Path tmp = dir.resolve("resort.json.tmp");
            Files.writeString(tmp, resort.toJson(), StandardCharsets.UTF_8);
            Files.move(tmp, dir.resolve("resort.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            BlocksideMod.LOG.error("[Blockside] Failed to save resort", e);
        }
    }

    public static void onSave(LevelAccessor level) {
        if (server != null && level == server.overworld()) save();
    }

    public static void stop() {
        save();
        LiftRuntime.clear();
        GroomerRuntime.clear();
        Crews.clear();
        Guests.clear();
        resort = null;
        server = null;
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftServer s) {
        if (resort == null) return;
        ServerLevel level = s.overworld();
        long t = level.getOverworldClockTime();
        ticks++;
        try {
            if (ticks % resort.config.crewStepTicks == 0) {
                resort.workStep(p -> {
                    String err = WorldAdapter.apply(level, p);
                    if (err == null) Crews.worked(level, p);
                    return err;
                }, t);
                Crews.tick(level, resort);
            }
            resort.tickHours(t);
            if (ticks % resort.config.snowStepTicks == 0) snowStep(level, t);
            LiftRuntime.tick(level, resort);
            GroomerRuntime.tick(level, resort);
            TrailTools.tick(level, resort);
            TestHill.tick(level);
            Placement.tick(level);
            if (ticks % 20 == 0) {
                Crews.drawZones(level, resort);
                broadcastNotices();
                sync();
            }
            if (ticks % 40 == 0) Guests.tick(level, resort);
            if (ticks % 6000 == 0) save();
        } catch (Throwable e) {
            lastError = e.toString();
            BlocksideMod.LOG.error("[Blockside] tick error", e);
        }
    }

    private static void snowStep(ServerLevel level, long t) {
        double dtHours = resort.config.snowStepTicks / 1000.0;
        List<SnowTile> changed = resort.snowStep(t, key -> level.isLoaded(new BlockPos(Pos.keyX(key), 64, Pos.keyZ(key))));
        for (SnowGun g : resort.snowGuns.values().stream().toList()) {
            BlockPos pos = new BlockPos(g.pos.x(), g.pos.y(), g.pos.z());
            if (!level.isLoaded(pos)) continue;
            if (!level.getBlockState(pos).is(dev.blockside.mod.Registry.SNOW_GUN.get())) { resort.snowGuns.remove(key(g.pos)); continue; }
            boolean water = hasWater(level, pos), power = level.hasNeighborSignal(pos);
            List<SnowTile> hit = resort.runSnowGun(g, water, power, dtHours, t);
            if (!hit.isEmpty()) {
                changed.addAll(hit);
                int[] d = dev.blockside.core.construction.Rotations.rotate(0, -1, g.facing);
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5 + d[0] * 2, pos.getY() + 1.5, pos.getZ() + 0.5 + d[1] * 2,
                        40, d[0] * 2.5 + 0.6, 0.6, d[1] * 2.5 + 0.6, 0.15);
            }
        }
        for (SnowTile tile : changed) SnowRenderer.render(level, tile, resort.config.snow);
    }

    private static boolean hasWater(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -2, -4), pos.offset(4, 1, 4)))
            if (level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p).isSource()) return true;
        return false;
    }

    public static void broadcastNotices() {
        for (String n : resort.drainNotices())
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                p.sendSystemMessage(Ui.text("[Resort] ", ChatFormatting.AQUA).append(Ui.text(n, ChatFormatting.WHITE)));
    }

    public static void sync() {
        String json = Snapshot.of(resort, now()).toJson();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(p, new Net.Sync(json));
    }

    public static void onPlayerTick(Player p) {
        if (p instanceof ServerPlayer sp && skiing.contains(sp.getUUID())) sp.resetFallDistance();
    }

    public static void setSkiing(ServerPlayer p, boolean on) {
        if (on) skiing.add(p.getUUID()); else skiing.remove(p.getUUID());
    }

    // ------------------------------------------------------------------ block callbacks

    public static String key(Pos p) { return p.x() + "," + p.y() + "," + p.z(); }

    public static void snowGunPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        if (resort == null || level != level()) return;
        SnowGun g = new SnowGun();
        g.pos = new Pos(pos.getX(), pos.getY(), pos.getZ());
        g.facing = switch (state.getValue(HorizontalDirectionalBlock.FACING)) {
            case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0;
        };
        resort.snowGuns.put(key(g.pos), g);
    }

    public static void snowGunUsed(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (resort == null) return;
        Pos p = new Pos(pos.getX(), pos.getY(), pos.getZ());
        SnowGun g = resort.snowGuns.get(key(p));
        if (g == null) { snowGunPlaced(level, pos, state); g = resort.snowGuns.get(key(p)); }
        if (!resort.isMember(who(player))) { player.sendSystemMessage(Ui.text("Not a member of this resort.", ChatFormatting.RED)); return; }
        g.on = !g.on;
        resort.runSnowGun(g, hasWater(level, pos), level.hasNeighborSignal(pos), 0, now());
        player.sendSystemMessage(Ui.title("Snow gun").append(Ui.text(" " + (g.on ? "ON" : "OFF") + " — " + g.describe(),
                g.status == SnowGun.Status.RUNNING || !g.on ? ChatFormatting.WHITE : ChatFormatting.YELLOW)));
        player.sendSystemMessage(Ui.text(String.format("Cutoff %.0f°C · %.1f°C here · made %.2f m SWE so far",
                resort.config.climate.snowGunCutoff, g.tempC, g.madeSwe), ChatFormatting.GRAY));
    }

    public static void jobBlockUsed(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (resort == null) return;
        Panels.jobBlock(player, new Pos(pos.getX(), pos.getY(), pos.getZ()));
    }

    public static void reply(ServerPlayer p, ActionResult r) {
        p.sendSystemMessage(Ui.text(r.message(), r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
