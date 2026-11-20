package dev.blockside.mod.client;

import dev.blockside.core.Snapshot;
import dev.blockside.mod.ClientRequests;
import net.minecraft.client.Minecraft;

/** Holds the last snapshot from the server and handles item-triggered client actions. */
public final class ClientState {
    private static String lastJson;
    private static Snapshot snapshot;
    private ClientState() {}

    public static Snapshot snapshot() {
        String j = ClientRequests.snapshotJson;
        if (j != null && !j.equals(lastJson)) {
            lastJson = j;
            try { snapshot = Snapshot.fromJson(j); } catch (RuntimeException ignored) { }
        }
        return snapshot;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (ClientRequests.openDashboard) {
            ClientRequests.openDashboard = false;
            mc.gui.setScreen(new ResortScreen());
        }
        if (ClientRequests.toggleSkis) {
            ClientRequests.toggleSkis = false;
            SkiController.toggle();
        }
    }
}
