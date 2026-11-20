package dev.blockside.mod.net;

import dev.blockside.mod.BlocksideMod;
import dev.blockside.mod.ClientRequests;
import dev.blockside.mod.server.ResortServer;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Two payloads. Everything else a client does goes through /resort commands, so the server validates
 * permission, range, cash and project state in exactly one place.
 */
public final class Net {
    private Net() {}

    /** Server → client: the shared resort snapshot (same JSON for every player). */
    public record Sync(String json) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BlocksideMod.MODID, "sync"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = ByteBufCodecs.stringUtf8(1 << 18).map(Sync::new, Sync::json);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client → server: the player clipped in/out of skis (server stops fall damage while skiing). */
    public record SkiMode(boolean on) implements CustomPacketPayload {
        public static final Type<SkiMode> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BlocksideMod.MODID, "ski_mode"));
        public static final StreamCodec<ByteBuf, SkiMode> CODEC = ByteBufCodecs.BOOL.map(SkiMode::new, SkiMode::on);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");
        r.playToClient(Sync.TYPE, Sync.CODEC, (Sync p, IPayloadContext ctx) -> ClientRequests.snapshotJson = p.json());
        r.playToServer(SkiMode.TYPE, SkiMode.CODEC, (SkiMode p, IPayloadContext ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) ResortServer.setSkiing(sp, p.on());
        }));
    }
}
