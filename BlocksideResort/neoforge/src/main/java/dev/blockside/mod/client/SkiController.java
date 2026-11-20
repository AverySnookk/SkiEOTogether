package dev.blockside.mod.client;

import dev.blockside.core.ski.SkiInput;
import dev.blockside.core.ski.SkiParams;
import dev.blockside.core.ski.SkiPhysics;
import dev.blockside.core.ski.SkiState;
import dev.blockside.core.ski.SkiSurface;
import dev.blockside.core.ski.SurfacePoint;
import dev.blockside.core.ski.SurfaceSampler;
import dev.blockside.core.ski.Vec3;
import dev.blockside.core.snow.SurfaceKind;
import dev.blockside.mod.Compat;
import dev.blockside.mod.net.Net;
import dev.blockside.mod.server.WorldAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side skiing for responsive control. Each tick (right before vanilla movement) it samples the
 * shared smooth surface from the rendered snow blocks, steps the core ski physics and hands the result
 * to vanilla's collision-swept move(), so skis follow what the player sees and can't pass through blocks.
 * W tuck · S snowplough brake · A/D carve · Sprint (Ctrl) dig edges in · Space hop.
 */
public final class SkiController {
    private static boolean enabled;
    private static boolean active;
    private static final SkiState state = new SkiState();
    private static final SkiParams params = new SkiParams();
    private static SurfaceKind lastKind = SurfaceKind.BARE;
    private SkiController() {}

    public static boolean enabled() { return enabled; }
    public static boolean active() { return active; }
    public static double speed() { return state.velocity.length(); }
    public static String surfaceName() { return lastKind.name().toLowerCase(); }

    static void toggle() {
        enabled = !enabled;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            state.velocity = new Vec3(p.getDeltaMovement().x * 20, 0, p.getDeltaMovement().z * 20);
            state.headingDeg = p.getYRot();
            p.displayClientMessage(Component.literal(enabled ? "Skis on. W tuck, S brake, A/D carve, Ctrl dig edges, Space hop." : "Skis off."), true);
        }
        PacketDistributor.sendToServer(new Net.SkiMode(enabled));
    }

    /** The shared surface, read from the blocks the client renders. */
    static SurfaceSampler sampler(Level level) {
        return new SurfaceSampler() {
            public double columnTop(int x, int z, double nearY) {
                int start = (int) Math.floor(nearY) + 1;
                for (int y = start; y >= start - 4; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(pos);
                    int layers = WorldAdapter.snowLayers(s);
                    if (layers > 0) return y + layers / 8.0;
                    VoxelShape shape = s.getCollisionShape(level, pos);
                    if (!shape.isEmpty()) return y + shape.max(net.minecraft.core.Direction.Axis.Y);
                }
                return Double.NaN;
            }
            public SurfaceKind kind(int x, int z, double nearY) {
                int start = (int) Math.floor(nearY) + 1;
                for (int y = start; y >= start - 4; y--) {
                    BlockState s = level.getBlockState(new BlockPos(x, y, z));
                    if (!s.isAir()) return WorldAdapter.surfaceKind(s);
                }
                return SurfaceKind.BARE;
            }
        };
    }

    static void onInput(MovementInputUpdateEvent e) {
        LocalPlayer p = (LocalPlayer) e.getEntity();
        if (!enabled || p.isPassenger() || p.getAbilities().flying || p.isInWater()) { active = false; return; }
        boolean[] k = Compat.clientKeys(e.getInput());
        SurfacePoint sp = SkiSurface.sample(sampler(p.level()), p.getX(), p.getZ(), p.getY() + 0.3);
        boolean grounded = sp != null && p.getY() - sp.height() < 0.35;
        lastKind = sp == null ? SurfaceKind.BARE : sp.kind();
        double speed = state.velocity.length();
        // Walk normally when standing still on bare ground; ski as soon as there's snow or momentum.
        boolean ski = (grounded && lastKind.snow()) || (!grounded && active) || speed > 1.5;
        if (!ski) { active = false; state.velocity = Vec3.ZERO; return; }
        if (!active) {
            state.velocity = new Vec3(p.getDeltaMovement().x * 20, 0, p.getDeltaMovement().z * 20);
            state.headingDeg = p.getYRot();
        }
        active = true;
        if (p.horizontalCollision) state.velocity = state.velocity.scale(0.3); // hit something: lose most speed
        SkiInput in = new SkiInput(k[0], k[1], k[2], k[3], k[6], p.getYRot());
        SkiPhysics.step(state, in, grounded ? sp : null, 0.05, params);
        Compat.zeroMoveVector(e.getInput());
        p.setSprinting(false);
        Vec3 v = state.velocity;
        double dy;
        if (grounded) {
            dy = v.y() / 20.0 - 0.02; // hug the surface; vanilla collision stops us at the snow
            if (k[4] && p.onGround()) dy = 0.42; // hop
        } else {
            dy = p.getDeltaMovement().y; // ballistic: vanilla gravity
        }
        p.setDeltaMovement(v.x() / 20.0, dy, v.z() / 20.0);
        if (lastKind == SurfaceKind.BARE && grounded && speed < 0.6) { active = false; state.velocity = Vec3.ZERO; }
    }
}
