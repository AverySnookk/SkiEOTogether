package dev.blockside.mod.client;

import dev.blockside.core.Snapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Compact resort line (cash · level/XP · reputation) plus the first alert, and the ski speedometer. */
public final class Hud {
    private Hud() {}

    static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font f = mc.font;
        Snapshot s = ClientState.snapshot();
        if (s != null && s.claimed) {
            String line = String.format("%s  $%,d  Lv %d (%d XP, %d to next)  Rep %.0f", s.name, s.cash, s.level, s.xp, s.xpToNext, s.reputation);
            g.text(f, line, 4, 4, 0xFFFFFFFF);
            if (!s.alerts.isEmpty()) g.text(f, "⚠ " + s.alerts.get(0), 4, 14, 0xFFFFAA33);
            else if (!s.jobs.isEmpty()) g.text(f, "⚒ " + s.jobs.get(0).name + ": " + s.jobs.get(0).status, 4, 14, 0xFFFFEE88);
        }
        if (SkiController.enabled()) {
            String ski = String.format("SKIS %s  %.0f km/h  %s", SkiController.active() ? "▶" : "·", SkiController.speed() * 3.6, SkiController.surfaceName());
            int w = f.width(ski);
            g.text(f, ski, (mc.getWindow().getGuiScaledWidth() - w) / 2, mc.getWindow().getGuiScaledHeight() - 60, 0xFF88DDFF);
        }
    }
}
