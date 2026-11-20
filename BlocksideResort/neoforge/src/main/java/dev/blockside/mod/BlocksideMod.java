package dev.blockside.mod;

import com.mojang.logging.LogUtils;
import dev.blockside.mod.net.Net;
import dev.blockside.mod.server.ResortCommands;
import dev.blockside.mod.server.ResortServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(BlocksideMod.MODID)
public final class BlocksideMod {
    public static final String MODID = "blockside";
    public static final Logger LOG = LogUtils.getLogger();
    /** Tag on every entity the mod spawns that should not survive a reload (chairs, groomers, crews, guests). */
    public static final String TRANSIENT_TAG = "blockside_transient";

    public BlocksideMod(IEventBus modBus, ModContainer container, Dist dist) {
        Registry.register(modBus);
        modBus.addListener(Net::register);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> ResortServer.start(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> ResortServer.stop());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> ResortServer.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((LevelEvent.Save e) -> ResortServer.onSave(e.getLevel()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ResortCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> ResortServer.onPlayerTick(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            // Chairs/groomers/crews are rebuilt from resort state; drop stale copies loaded from chunks.
            if (!e.loadedFromDisk()) return;
            if (e.getEntity().entityTags().contains(TRANSIENT_TAG)) e.setCanceled(true);
        });
        if (dist.isClient()) dev.blockside.mod.client.ClientInit.init(modBus);
        LOG.info("[Blockside] Blockside Resort loaded");
    }
}
