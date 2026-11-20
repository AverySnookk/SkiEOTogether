package dev.blockside.mod.server;

import dev.blockside.core.Facility;
import dev.blockside.core.LiftRecord;
import dev.blockside.core.Pos;
import dev.blockside.core.Resort;
import dev.blockside.core.Snapshot;
import dev.blockside.core.catalog.CatalogEntry;
import dev.blockside.core.construction.Crew;
import dev.blockside.core.construction.Job;
import dev.blockside.core.economy.ReputationFactor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Chat versions of the management UI (the tablet screen shows the same data graphically). */
public final class Panels {
    private Panels() {}

    public static void dashboard(ServerPlayer p) {
        Resort r = ResortServer.resort();
        Snapshot s = Snapshot.of(r, ResortServer.now());
        p.sendSystemMessage(Ui.title(s.name));
        if (!s.claimed) {
            p.sendSystemMessage(Ui.text("No resort claimed yet. ").append(Ui.button("Claim here", "/resort claim", "Claim a resort area centered on you")));
            return;
        }
        p.sendSystemMessage(Ui.text("Cash ", ChatFormatting.GRAY).append(Ui.text(Ui.money(s.cash), s.cash < 0 ? ChatFormatting.RED : ChatFormatting.GREEN))
                .append(Ui.text("   Level ", ChatFormatting.GRAY)).append(Ui.text(s.level + " (" + s.xp + " XP, " + s.xpToNext + " to next)", ChatFormatting.AQUA))
                .append(Ui.text("   Reputation ", ChatFormatting.GRAY)).append(Ui.text(String.valueOf(s.reputation), ChatFormatting.YELLOW)));
        for (ReputationFactor f : s.factors)
            p.sendSystemMessage(Ui.text(String.format("  %s %-20s %3.0f  ", f.impact() >= 0 ? "▲" : "▼", f.label(), f.score()),
                    f.impact() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED).append(Ui.text(f.hint(), ChatFormatting.GRAY)));
        p.sendSystemMessage(Ui.text(String.format("Open terrain %d/%d runs, %d/%d lifts · Today %d guests, %s · %d tickets all-time · %.0f on the hill, wait %.0f min",
                s.openRuns, s.totalRuns, s.openLifts, s.totalLifts, s.guestsToday, Ui.money(s.revenueToday), s.ticketsSold, s.guestsOnMountain, s.waitMinutes), ChatFormatting.WHITE));
        p.sendSystemMessage(Ui.text(s.weather, ChatFormatting.DARK_AQUA));
        for (String a : s.alerts) p.sendSystemMessage(Ui.text("⚠ " + a, ChatFormatting.GOLD));
        for (Snapshot.Row j : s.jobs) p.sendSystemMessage(Ui.text("⚒ " + j.name + ": " + j.status, ChatFormatting.YELLOW));
        for (String m : s.milestones) p.sendSystemMessage(Ui.text("  " + m, m.startsWith("✔") ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY));
        p.sendSystemMessage(Ui.button("Catalog", "/resort catalog", "Buy buildings, lifts and equipment")
                .append(" ").append(Ui.button("Runs", "/resort trail list", "Trails"))
                .append(" ").append(Ui.button("Lifts", "/resort lifts", "Lift status")));
    }

    public static void catalog(ServerPlayer p) {
        Resort r = ResortServer.resort();
        p.sendSystemMessage(Ui.title("Resort catalog — " + Ui.money(r.ledger.cash()) + ", level " + r.level()));
        for (CatalogEntry e : r.catalog.entries) {
            String lock = r.lockReason(e);
            var t = r.catalog.template(e.template);
            String hover = e.description + (t != null ? "\nFootprint " + t.sx() + "×" + t.sz() : "") + (e.laborCost > 0 ? "\nCrew quote ~" + Ui.money(e.laborCost) : "")
                    + "\nService: " + e.service + (e.capacity > 0 ? " (" + e.capacity + "/h)" : "");
            MutableComponent row = Ui.text(String.format("%-24s %8s  L%d  ", e.name, Ui.money(e.price), e.requiredLevel), lock == null ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY);
            row.append(lock == null ? Ui.button("Buy", "/resort buy " + e.id, hover) : Ui.disabled("Locked", lock + "\n" + hover));
            p.sendSystemMessage(row);
        }
    }

    public static void jobBlock(ServerPlayer p, Pos at) {
        Resort r = ResortServer.resort();
        Job j = r.jobAt(at);
        LiftRecord lift = r.liftAt(at);
        if (lift == null) for (LiftRecord l : r.lifts.values()) if (l.upper.equals(at)) lift = l;
        if (lift != null) { lift(p, lift); return; }
        Facility f = r.facilityAt(at);
        if (f != null) { facility(p, f); return; }
        if (j == null) for (Job k : r.jobs.values()) if (at.equals(k.liftTop)) j = k;
        if (j == null) { p.sendSystemMessage(Ui.text("This construction block isn't linked to a project.", ChatFormatting.GRAY)); return; }
        job(p, j);
    }

    public static void job(ServerPlayer p, Job j) {
        Resort r = ResortServer.resort();
        p.sendSystemMessage(Ui.title(j.name + " — construction"));
        Crew idle = r.idleCrew();
        switch (j.state) {
            case PLANNED -> {
                long q = r.hireQuote(j);
                p.sendSystemMessage(Ui.text("Waiting for a crew. Quote " + Ui.money(q) + (idle != null ? " (assign idle crew " + idle.name + ")" : " (hire a new crew)")
                        + ". Cash: " + Ui.money(r.ledger.cash()), ChatFormatting.WHITE));
                p.sendSystemMessage(r.ledger.canAfford(q) ? Ui.button(idle != null ? "Assign " + idle.name : "Hire crew " + Ui.money(q), "/resort hire " + j.id, "Start construction")
                        : Ui.disabled("Hire crew " + Ui.money(q), "Not enough cash"));
            }
            case ACTIVE, BLOCKED -> {
                Crew c = r.crews.get(j.crewId);
                p.sendSystemMessage(Ui.text("Crew: " + (c == null ? "?" : c.name) + " · Stage: " + j.stage().label, ChatFormatting.WHITE));
                p.sendSystemMessage(Ui.text(Ui.bar(j.progress()), ChatFormatting.GREEN));
                if (j.blockedReason != null) p.sendSystemMessage(Ui.text("Blocked: " + j.blockedReason, ChatFormatting.RED));
            }
            case COMPLETE -> p.sendSystemMessage(Ui.text("Complete.", ChatFormatting.GREEN));
        }
    }

    public static void lift(ServerPlayer p, LiftRecord l) {
        p.sendSystemMessage(Ui.title(l.name));
        p.sendSystemMessage(Ui.text((l.open ? "OPEN" : "CLOSED") + String.format(" · %d riders/h · queue %.0f · wait %.0f min · %d rides today · %d towers",
                l.capacity, l.guestQueue, l.waitMinutes, l.ridesToday, l.towers.size()), l.open ? ChatFormatting.GREEN : ChatFormatting.RED));
        p.sendSystemMessage(Ui.text("To ride: walk onto the yellow loading mark at the bottom station; a chair picks you up. You're let off at the top.", ChatFormatting.GRAY));
        p.sendSystemMessage(l.open ? Ui.button("Close lift", "/resort lift close " + l.id, "Stop the lift")
                : Ui.button("Open lift", "/resort lift open " + l.id, "Start the lift (costs power per hour)"));
    }

    public static void facility(ServerPlayer p, Facility f) {
        Resort r = ResortServer.resort();
        p.sendSystemMessage(Ui.title(f.name));
        p.sendSystemMessage(Ui.text((f.open ? "Open" : "Closed") + " · " + f.service + " · capacity " + f.capacity + "/h", ChatFormatting.WHITE));
        if ("ticket_booth".equals(f.catalogId))
            p.sendSystemMessage(Ui.text(String.format("Tickets: %d today, %d all-time at %s", r.guestsToday, r.ticketsSold, Ui.money(r.config.ticketPrice)), ChatFormatting.GRAY));
        MutableComponent row = Ui.button(f.open ? "Close" : "Open", "/resort facility toggle " + f.id, "Toggle service");
        if ("groomer_garage".equals(f.catalogId)) row.append(" ").append(Ui.button("Drive groomer", "/resort groomer", "Take the snowcat out"));
        p.sendSystemMessage(row);
    }
}
