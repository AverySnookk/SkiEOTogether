package dev.blockside.mod.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only wiring; only referenced when running on the physical client. */
public final class ClientInit {
    private ClientInit() {}

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientState.tick());
        NeoForge.EVENT_BUS.addListener((MovementInputUpdateEvent e) -> SkiController.onInput(e));
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> Hud.render(e.getGuiGraphics()));
    }
}
