package dev.blockside.core;

import dev.blockside.core.catalog.CatalogEntry;
import dev.blockside.core.catalog.Template;
import dev.blockside.core.construction.Job;
import dev.blockside.core.construction.JobState;
import dev.blockside.core.economy.Progression;
import dev.blockside.core.economy.ReputationFactor;
import dev.blockside.core.snow.SnowTile;

import java.util.ArrayList;
import java.util.List;

/** Read-only view of the resort sent to every client (dashboard, HUD). Same object for every player. */
public final class Snapshot {
    public String name;
    public boolean claimed;
    public long cash, xp, xpToNext, ticketsSold, guestsToday, revenueToday, lifetimeRevenue;
    public int level, openRuns, totalRuns, openLifts, totalLifts;
    public double reputation, guestsOnMountain, waitMinutes;
    public String weather;
    public List<ReputationFactor> factors = new ArrayList<>();
    public List<Row> catalog = new ArrayList<>();
    public List<Row> jobs = new ArrayList<>();
    public List<Row> lifts = new ArrayList<>();
    public List<Row> runs = new ArrayList<>();
    public List<String> alerts = new ArrayList<>();
    public List<String> milestones = new ArrayList<>();

    /** Generic table row so the UI stays simple. */
    public static final class Row {
        public String id, name, detail, status;
        public long price;
        public int level;
        public boolean locked;
        public double progress;
    }

    public static Snapshot of(Resort r, long gameTime) {
        Snapshot s = new Snapshot();
        s.name = r.name;
        s.claimed = r.claimed();
        s.cash = r.ledger.cash();
        s.xp = r.progression.xp();
        s.level = r.level();
        s.xpToNext = Progression.xpToNext(s.xp, r.config.levelXp);
        s.reputation = Math.round(r.reputation.value() * 10) / 10.0;
        s.factors = r.reputation.factors();
        s.ticketsSold = r.ticketsSold;
        s.guestsToday = r.guestsToday;
        s.revenueToday = r.revenueToday;
        s.lifetimeRevenue = r.ledger.lifetimeGuestRevenue();
        s.guestsOnMountain = Math.round(r.guestsOnMountain);
        s.waitMinutes = Math.round(r.lastWaitMinutes * 10) / 10.0;
        s.weather = r.weather.describe(r.config.climate, gameTime, r.claimCenter == null ? 64 : r.claimCenter.y());
        for (CatalogEntry e : r.catalog.entries) {
            Row row = new Row();
            row.id = e.id; row.name = e.name; row.price = e.price; row.level = e.requiredLevel;
            String lock = r.lockReason(e);
            row.locked = lock != null;
            Template t = r.catalog.template(e.template);
            String fp = t == null ? "" : t.sx() + "×" + t.sz() + " footprint, ";
            row.detail = fp + e.description + (e.laborCost > 0 ? " Crew ~$" + e.laborCost + "." : "");
            row.status = lock == null ? "Available" : lock;
            s.catalog.add(row);
        }
        for (Job j : r.jobs.values()) {
            if (j.state == JobState.COMPLETE && gameTime - j.completedAt > 24000) continue;
            Row row = new Row();
            row.id = j.id; row.name = j.name; row.progress = j.progress();
            row.status = switch (j.state) {
                case PLANNED -> "Waiting for crew (quote $" + r.hireQuote(j) + ")";
                case ACTIVE -> j.stage().label + " " + Math.round(j.progress() * 100) + "%";
                case BLOCKED -> "Blocked: " + j.blockedReason;
                case COMPLETE -> "Complete";
            };
            row.detail = "Center block at " + j.anchor;
            s.jobs.add(row);
        }
        for (LiftRecord l : r.lifts.values()) {
            Row row = new Row();
            row.id = l.id; row.name = l.name;
            row.status = l.open ? String.format("Open, queue %.0f, wait %.0f min", l.guestQueue, l.waitMinutes) : "Closed";
            row.detail = l.capacity + " riders/h, " + l.ridesToday + " rides today";
            s.lifts.add(row);
            s.totalLifts++;
            if (l.open) s.openLifts++;
        }
        for (Trail t : r.trails.values()) {
            Row row = new Row();
            row.id = t.id; row.name = t.name;
            row.status = (t.open ? "Open" : "Closed") + String.format(", %d%% covered, %d%% groomed", Math.round(t.coverage * 100), Math.round(t.groomedFraction * 100));
            row.detail = t.difficulty.label + String.format(", %.0f° avg / %.0f° max, %.0f m", t.avgGradeDeg, t.maxGradeDeg, t.length)
                    + (t.flags > 0 ? ", " + t.flags + " spots need earthwork" : "");
            row.progress = t.coverage;
            s.runs.add(row);
            s.totalRuns++;
            if (t.open) s.openRuns++;
            if (t.open && t.coverage < 0.8) s.alerts.add(t.name + ": thin snow (" + Math.round(t.coverage * 100) + "% covered)");
        }
        for (SnowGun g : r.snowGuns.values())
            if (g.on && g.status != SnowGun.Status.RUNNING) s.alerts.add("Snow gun at " + g.pos + ": " + g.describe());
        if (r.ledger.cash() < 0) s.alerts.add("Resort is in debt: snowmaking paused.");
        for (var m : r.config.milestones) {
            boolean done = r.progression.hasAward("milestone:" + m.id());
            String extra = "";
            if (!done && m.kind() == dev.blockside.core.economy.MilestoneKind.REPUTATION) {
                long held = r.progression.sustainedFor(m.id(), gameTime);
                extra = held < 0 ? " (rep " + Math.round(r.reputation.value()) + "/" + (int) m.threshold() + ")"
                        : " (held " + held / 1000 + "/" + m.sustainTicks() / 1000 + " h)";
            }
            if (!done && m.kind() == dev.blockside.core.economy.MilestoneKind.REVENUE)
                extra = " ($" + r.ledger.lifetimeGuestRevenue() + "/$" + (long) m.threshold() + ")";
            s.milestones.add((done ? "✔ " : "• ") + m.label() + " +" + m.xp() + " XP" + extra);
        }
        return s;
    }

    public String toJson() { return Json.COMPACT.toJson(this); }
    public static Snapshot fromJson(String json) { return Json.COMPACT.fromJson(json, Snapshot.class); }

    @SuppressWarnings("unused")
    private static double depth(SnowTile t) { return t.depth(); }
}
