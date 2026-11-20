package dev.blockside.mod.client;

import dev.blockside.core.Snapshot;
import dev.blockside.core.economy.ReputationFactor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Resort management screen (Resort Tablet). Dashboard, Catalog, Jobs, Runs & Lifts. Buttons send
 * /resort commands; the server validates and the next snapshot updates the screen.
 */
public class ResortScreen extends Screen {
    private int tab;
    private int scroll;

    public ResortScreen() { super(Component.literal("Blockside Resort")); }

    @Override protected void init() {
        clearWidgets();
        String[] tabs = {"Dashboard", "Catalog", "Jobs", "Runs & Lifts"};
        for (int i = 0; i < tabs.length; i++) {
            final int t = i;
            addRenderableWidget(Button.builder(Component.literal(tabs[i]), b -> { tab = t; scroll = 0; init(); }).bounds(10 + i * 92, 8, 88, 20).build());
        }
        Snapshot s = ClientState.snapshot();
        if (s == null) return;
        if (!s.claimed) {
            addRenderableWidget(Button.builder(Component.literal("Claim resort here"), b -> cmd("resort claim")).bounds(10, 40, 160, 20).build());
            return;
        }
        int y = 40;
        if (tab == 1) {
            for (int i = scroll; i < s.catalog.size() && y < height - 30; i++, y += 22) {
                Snapshot.Row r = s.catalog.get(i);
                Button b = Button.builder(Component.literal("Buy"), x -> cmd("resort buy " + r.id)).bounds(width - 60, y, 50, 20).build();
                b.active = !r.locked && s.cash >= r.price;
                addRenderableWidget(b);
            }
            addScrollButtons(s.catalog.size());
        } else if (tab == 2) {
            for (int i = scroll; i < s.jobs.size() && y < height - 30; i++, y += 22) {
                Snapshot.Row r = s.jobs.get(i);
                if (r.status.startsWith("Waiting")) addRenderableWidget(Button.builder(Component.literal("Hire"), x -> cmd("resort hire " + r.id)).bounds(width - 60, y, 50, 20).build());
            }
        } else if (tab == 3) {
            for (Snapshot.Row r : s.runs) {
                boolean open = r.status.startsWith("Open");
                addRenderableWidget(Button.builder(Component.literal(open ? "Close" : "Open"), x -> cmd("resort trail " + (open ? "close " : "open ") + r.id)).bounds(width - 60, y, 50, 20).build());
                y += 22;
            }
            y += 14;
            for (Snapshot.Row r : s.lifts) {
                boolean open = r.status.startsWith("Open");
                addRenderableWidget(Button.builder(Component.literal(open ? "Close" : "Open"), x -> cmd("resort lift " + (open ? "close " : "open ") + r.id)).bounds(width - 60, y, 50, 20).build());
                y += 22;
            }
        }
    }

    private void addScrollButtons(int n) {
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 5); init(); }).bounds(width - 30, 8, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll = Math.min(Math.max(0, n - 1), scroll + 5); init(); }).bounds(width - 54, 8, 20, 20).build());
    }

    private void cmd(String c) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.sendCommand(c);
    }

    private int lastHash;

    @Override public void tick() {
        Snapshot s = ClientState.snapshot();
        int h = s == null ? 0 : (s.jobs.size() * 31 + s.runs.size() * 7 + s.lifts.size() + (int) s.cash % 997 + s.level * 13 + s.runs.hashCode());
        if (h != lastHash) { lastHash = h; init(); }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Snapshot s = ClientState.snapshot();
        if (s == null) { g.drawString(font, "Waiting for resort data…", 10, 40, 0xFFFFFFFF); return; }
        int y = 34;
        g.drawString(font, String.format("%s   Cash $%,d   Level %d (%d XP · %d to next)   Reputation %.1f", s.name, s.cash, s.level, s.xp, s.xpToNext, s.reputation), 10, y, 0xFFFFDD55);
        y += 14;
        switch (tab) {
            case 0 -> {
                g.drawString(font, "Reputation factors (helping ▲ / hurting ▼):", 10, y, 0xFFAAAAAA); y += 12;
                for (ReputationFactor f : s.factors) {
                    g.drawString(font, String.format("%s %-20s %3.0f   %s", f.impact() >= 0 ? "▲" : "▼", f.label(), f.score(), f.hint()), 16, y, f.impact() >= 0 ? 0xFF77FF77 : 0xFFFF7777);
                    y += 11;
                }
                y += 4;
                g.drawString(font, String.format("Open terrain: %d/%d runs, %d/%d lifts    Today: %d guests, $%,d    Tickets all-time: %d", s.openRuns, s.totalRuns, s.openLifts, s.totalLifts, s.guestsToday, s.revenueToday, s.ticketsSold), 10, y, 0xFFFFFFFF); y += 12;
                g.drawString(font, String.format("On the hill: %.0f   Lift wait: %.0f min   Guest revenue all-time: $%,d", s.guestsOnMountain, s.waitMinutes, s.lifetimeRevenue), 10, y, 0xFFFFFFFF); y += 12;
                g.drawString(font, s.weather, 10, y, 0xFF66CCFF); y += 14;
                for (String a : s.alerts) { g.drawString(font, "⚠ " + a, 10, y, 0xFFFFAA33); y += 11; }
                for (Snapshot.Row j : s.jobs) { g.drawString(font, "⚒ " + j.name + ": " + j.status, 10, y, 0xFFFFEE88); y += 11; }
                y += 4;
                g.drawString(font, "Milestones:", 10, y, 0xFFAAAAAA); y += 12;
                for (String m : s.milestones) { g.drawString(font, m, 16, y, m.startsWith("✔") ? 0xFF55CC55 : 0xFFCCCCCC); y += 11; }
            }
            case 1 -> {
                y = 40;
                for (int i = scroll; i < s.catalog.size() && y < height - 30; i++, y += 22) {
                    Snapshot.Row r = s.catalog.get(i);
                    g.drawString(font, String.format("%s  $%,d  (L%d)", r.name, r.price, r.level), 10, y + 1, r.locked ? 0xFF777777 : 0xFFFFFFFF);
                    String detail = r.locked ? r.status : r.detail;
                    g.drawString(font, font.plainSubstrByWidth(detail, width - 90), 10, y + 11, r.locked ? 0xFFAA6666 : 0xFF999999);
                }
            }
            case 2 -> {
                y = 40;
                if (s.jobs.isEmpty()) g.drawString(font, "No construction jobs. Buy a project in the Catalog.", 10, y, 0xFFAAAAAA);
                for (int i = scroll; i < s.jobs.size() && y < height - 30; i++, y += 22) {
                    Snapshot.Row r = s.jobs.get(i);
                    g.drawString(font, r.name + " — " + r.status, 10, y + 1, 0xFFFFFFFF);
                    int bw = 160;
                    g.fill(10, y + 12, 10 + bw, y + 17, 0xFF333333);
                    g.fill(10, y + 12, 10 + (int) (bw * r.progress), y + 17, 0xFF55CC55);
                    g.drawString(font, r.detail, 176, y + 11, 0xFF999999);
                }
            }
            case 3 -> {
                y = 40;
                for (Snapshot.Row r : s.runs) {
                    g.drawString(font, r.name + " — " + r.status, 10, y + 1, 0xFFFFFFFF);
                    g.drawString(font, font.plainSubstrByWidth(r.detail, width - 90), 10, y + 11, 0xFF999999);
                    y += 22;
                }
                y += 14;
                for (Snapshot.Row r : s.lifts) {
                    g.drawString(font, r.name + " — " + r.status, 10, y + 1, 0xFFFFFFFF);
                    g.drawString(font, r.detail, 10, y + 11, 0xFF999999);
                    y += 22;
                }
            }
            default -> {}
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
