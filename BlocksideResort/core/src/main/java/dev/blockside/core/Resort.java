package dev.blockside.core;

import dev.blockside.core.catalog.Catalog;
import dev.blockside.core.catalog.CatalogEntry;
import dev.blockside.core.catalog.EntryKind;
import dev.blockside.core.catalog.Stage;
import dev.blockside.core.catalog.Template;
import dev.blockside.core.construction.Crew;
import dev.blockside.core.construction.Job;
import dev.blockside.core.construction.JobState;
import dev.blockside.core.construction.Placement;
import dev.blockside.core.construction.PlacementCheck;
import dev.blockside.core.construction.Purchase;
import dev.blockside.core.construction.Rotations;
import dev.blockside.core.construction.SiteProbe;
import dev.blockside.core.construction.TemplatePlacer;
import dev.blockside.core.economy.Ledger;
import dev.blockside.core.economy.Progression;
import dev.blockside.core.economy.Reputation;
import dev.blockside.core.economy.ReputationFactor;
import dev.blockside.core.economy.TxKind;
import dev.blockside.core.guest.GuestSim;
import dev.blockside.core.lift.LiftDesign;
import dev.blockside.core.snow.Climate;
import dev.blockside.core.snow.SnowTile;
import dev.blockside.core.snow.Snowpack;
import dev.blockside.core.snow.Weather;
import dev.blockside.core.terrain.HeightField;
import dev.blockside.core.terrain.TrailDesign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The whole resort: the single server-authoritative state shared by every player. All mutations are
 * methods here and run on the server thread, so each is atomic with respect to other players' requests.
 */
public final class Resort {
    public static final int FORMAT = 1;
    public int format = FORMAT;

    public String name = "Blockside Resort";
    public Pos claimCenter;
    public int claimRadius;
    public String owner;
    public final Set<String> members = new LinkedHashSet<>();

    public final Ledger ledger;
    public final Reputation reputation = new Reputation(50);
    public final Progression progression = new Progression();

    public final Map<String, Purchase> purchases = new LinkedHashMap<>();
    public final Map<String, Job> jobs = new LinkedHashMap<>();
    public final Map<String, Crew> crews = new LinkedHashMap<>();
    public final Map<String, Facility> facilities = new LinkedHashMap<>();
    public final Map<String, LiftRecord> lifts = new LinkedHashMap<>();
    public final Map<String, Trail> trails = new LinkedHashMap<>();
    public final Map<String, SnowGun> snowGuns = new LinkedHashMap<>();
    public final Snowpack snowpack = new Snowpack();
    public final Weather weather = new Weather();

    public long counter;
    public long lastHourTick = -1;
    public long lastDay = -1;
    public double guestsOnMountain;
    public long guestsToday, revenueToday, ticketsSold;
    public double lastWaitMinutes;

    public transient ResortConfig config;
    public transient Catalog catalog;
    private transient List<String> notices = new ArrayList<>();

    public Resort(ResortConfig config, Catalog catalog) {
        this.config = config;
        this.catalog = catalog;
        this.ledger = new Ledger(config.startingCash);
    }

    public void bind(ResortConfig config, Catalog catalog) {
        this.config = config;
        this.catalog = catalog;
        for (Job j : jobs.values()) regeneratePlacements(j);
    }

    // ------------------------------------------------------------------ persistence

    public String toJson() { return Json.GSON.toJson(this); }

    public static Resort fromJson(String json, ResortConfig config, Catalog catalog) {
        Resort r = Json.GSON.fromJson(json, Resort.class);
        r.bind(config, catalog);
        return r;
    }

    // ------------------------------------------------------------------ membership / claim

    public boolean claimed() { return claimCenter != null; }

    public ActionResult claim(String player, Pos center) {
        if (claimed()) return ActionResult.fail("The resort is already claimed around " + claimCenter + ".");
        claimCenter = center;
        claimRadius = config.claimRadius;
        owner = player;
        members.add(player);
        notice(player + " claimed " + name + " (radius " + claimRadius + ").");
        return ActionResult.ok("Resort claimed.");
    }

    public boolean isMember(String player) { return player != null && (config.openMembership || members.contains(player) || player.equals(owner)); }

    public boolean inClaim(int x, int z) {
        return claimed() && Math.hypot(x - claimCenter.x(), z - claimCenter.z()) <= claimRadius;
    }

    // ------------------------------------------------------------------ catalog / purchase

    public int level() { return config.level(progression.xp()); }

    /** Why this entry can't be bought right now, or null. */
    public String lockReason(CatalogEntry e) {
        if (!e.available()) return "Template coming in a later update";
        if (level() < e.requiredLevel) return "Requires resort level " + e.requiredLevel;
        if (reputation.value() < e.requiredReputation) return "Requires reputation " + (int) e.requiredReputation;
        return null;
    }

    public ActionResult buy(String catalogId, String buyer, long gameTime) {
        if (!isMember(buyer)) return ActionResult.fail("You are not a member of this resort.");
        if (!claimed()) return ActionResult.fail("Claim the resort first: /resort claim");
        CatalogEntry e = catalog.get(catalogId);
        if (e == null) return ActionResult.fail("Unknown project " + catalogId);
        String lock = lockReason(e);
        if (lock != null) return ActionResult.fail(e.name + ": " + lock);
        if (!ledger.trySpend(e.price, TxKind.PROJECT_PURCHASE, e.name, gameTime))
            return ActionResult.fail("Not enough cash for " + e.name + " ($" + e.price + ", you have $" + ledger.cash() + ")");
        Purchase p = new Purchase();
        p.id = "P" + (++counter);
        p.catalogId = catalogId;
        p.buyer = buyer;
        p.price = e.price;
        p.boughtAt = gameTime;
        if (e.kind == EntryKind.EQUIPMENT) p.state = Purchase.State.PLACED; // handed over directly
        purchases.put(p.id, p);
        notice(buyer + " bought " + e.name + " for $" + e.price + ".");
        return ActionResult.ok("Bought " + e.name + ".", p.id);
    }

    /** Refund an unplaced kit. Refunds never count as revenue, so buy/refund spam can't earn XP. */
    public ActionResult refund(String purchaseId, String player, long gameTime) {
        Purchase p = purchases.get(purchaseId);
        if (p == null) return ActionResult.fail("Unknown purchase.");
        if (p.state != Purchase.State.KIT) return ActionResult.fail("That kit was already used or refunded.");
        p.state = Purchase.State.REFUNDED;
        ledger.earn(p.price, TxKind.PROJECT_REFUND, "Refund " + p.catalogId, gameTime);
        return ActionResult.ok("Refunded $" + p.price + ".");
    }

    // ------------------------------------------------------------------ placement

    public PlacementCheck checkBuilding(String purchaseId, Pos anchor, int rot, SiteProbe probe) {
        PlacementCheck c = new PlacementCheck();
        Purchase p = purchases.get(purchaseId);
        if (p == null) { c.block("Unknown project kit."); return c; }
        if (p.state != Purchase.State.KIT) { c.block("This kit has already been placed or refunded."); return c; }
        CatalogEntry e = catalog.get(p.catalogId);
        Template t = catalog.template(e.template);
        if (t == null) { c.block("No template for " + e.name); return c; }
        Box fp = TemplatePlacer.footprint(t, anchor.x(), anchor.y(), anchor.z(), rot);
        checkVolume(c, fp, t, anchor, rot, probe);
        return c;
    }

    private void checkVolume(PlacementCheck c, Box fp, Template t, Pos anchor, int rot, SiteProbe probe) {
        if (!inClaim(fp.minX(), fp.minZ()) || !inClaim(fp.maxX(), fp.maxZ())) c.block("Outside the resort claim.");
        Box zone = fp.grow(1, 0);
        for (Job j : jobs.values())
            if (j.state != JobState.COMPLETE && j.zone.intersects(zone)) c.block("Overlaps the construction zone of " + j.name + ".");
        for (Facility f : facilities.values())
            if (f.bounds.intersects(fp)) c.block("Overlaps " + f.name + ".");
        for (LiftRecord l : lifts.values())
            for (Pos tw : l.towers) if (fp.grow(1, 0).containsXZ(tw.x(), tw.z())) c.block("Too close to a tower of " + l.name + ".");
        int unsupported = 0, foundation = 0;
        for (int y = 0; y < t.sy(); y++) for (int z = 0; z < t.sz(); z++) for (int x = 0; x < t.sx(); x++) {
            char ch = t.at(x, y, z);
            if (ch == ' ') continue;
            int[] w = TemplatePlacer.toWorld(t, new int[]{x, y, z}, anchor.x(), anchor.y(), anchor.z(), rot);
            SiteProbe.Kind k = probe.probe(w[0], w[1], w[2]);
            Pos wp = new Pos(w[0], w[1], w[2]);
            if (k == SiteProbe.Kind.BUILT && !(ch == '@' && y == t.anchor()[1]))
                c.blockAt("Existing build in the footprint (protected).", wp);
            if (k == SiteProbe.Kind.FLUID) c.blockAt("Water or lava in the footprint.", wp);
            if (y == 0 && ch != '.') {
                foundation++;
                SiteProbe.Kind below = probe.probe(w[0], w[1] - 1, w[2]);
                if (!k.solidGround() && !below.solidGround()) unsupported++;
            }
            if (y >= 1 && k == SiteProbe.Kind.TERRAIN) {
                if (y >= 3) c.blockAt("Ground rises too high inside the footprint; level the site.", wp);
                else c.cutBlocks++;
            }
            if (y >= 1 && k == SiteProbe.Kind.NATURAL) c.clearBlocks++;
        }
        if (foundation > 0 && unsupported > foundation / 3)
            c.block("Ground drops away under " + unsupported + " of " + foundation + " foundation cells.");
        if (c.cutBlocks > 0) c.notes.add("Crew will cut " + c.cutBlocks + " blocks of terrain.");
        if (c.clearBlocks > 0) c.notes.add("Crew will clear " + c.clearBlocks + " plants/trees.");
    }

    /** Confirms a building placement: creates the zone and the job (state PLANNED, no crew yet). */
    public ActionResult placeBuilding(String purchaseId, Pos anchor, int rot, SiteProbe probe, String player, long gameTime) {
        if (!isMember(player)) return ActionResult.fail("You are not a member of this resort.");
        PlacementCheck c = checkBuilding(purchaseId, anchor, rot, probe);
        if (!c.ok()) return ActionResult.fail("Can't place here: " + c.summary());
        Purchase p = purchases.get(purchaseId);
        CatalogEntry e = catalog.get(p.catalogId);
        Template t = catalog.template(e.template);
        Job j = newJob(p, e, anchor, rot, gameTime);
        j.zone = TemplatePlacer.footprint(t, anchor.x(), anchor.y(), anchor.z(), rot).grow(1, 0);
        regeneratePlacements(j);
        notice(player + " placed a construction zone for " + e.name + ". Hire a crew at its center block.");
        return ActionResult.ok("Zone placed for " + e.name + ".", j.id);
    }

    public LiftDesign planLift(String purchaseId, Pos lower, Pos upper, HeightField terrain) {
        Purchase p = purchases.get(purchaseId);
        CatalogEntry e = p == null ? null : catalog.get(p.catalogId);
        return LiftDesign.plan(lower, upper, terrain, config.lift, e == null ? 140 : e.maxLength);
    }

    public PlacementCheck checkLift(String purchaseId, Pos lower, Pos upper, HeightField terrain, SiteProbe probe) {
        PlacementCheck c = new PlacementCheck();
        Purchase p = purchases.get(purchaseId);
        if (p == null) { c.block("Unknown project kit."); return c; }
        if (p.state != Purchase.State.KIT) { c.block("This kit has already been placed or refunded."); return c; }
        CatalogEntry e = catalog.get(p.catalogId);
        if (e.kind != EntryKind.LIFT) { c.block(e.name + " is not a lift."); return c; }
        LiftDesign d = planLift(purchaseId, lower, upper, terrain);
        for (String s : d.problems) c.block(s);
        for (Pos v : d.clearanceViolations) c.blockedAt.add(v);
        int rot = Rotations.facing(upper.x() - lower.x(), upper.z() - lower.z());
        Template lt = catalog.template(e.template), ut = catalog.template(e.upperTemplate);
        checkVolume(c, TemplatePlacer.footprint(lt, lower.x(), lower.y(), lower.z(), rot), lt, lower, rot, probe);
        checkVolume(c, TemplatePlacer.footprint(ut, upper.x(), upper.y(), upper.z(), rot), ut, upper, rot, probe);
        for (Pos tw : d.towerTops) {
            for (int y = terrain.top(tw.x(), tw.z()); y <= tw.y(); y++) {
                SiteProbe.Kind k = probe.probe(tw.x(), y, tw.z());
                if (k == SiteProbe.Kind.BUILT || k == SiteProbe.Kind.FLUID) c.blockAt("A tower site is blocked.", new Pos(tw.x(), y, tw.z()));
            }
            if (!inClaim(tw.x(), tw.z())) c.block("A tower is outside the resort claim.");
        }
        return c;
    }

    public ActionResult placeLift(String purchaseId, Pos lower, Pos upper, HeightField terrain, SiteProbe probe, String player, long gameTime) {
        if (!isMember(player)) return ActionResult.fail("You are not a member of this resort.");
        PlacementCheck c = checkLift(purchaseId, lower, upper, terrain, probe);
        if (!c.ok()) return ActionResult.fail("Can't build the lift: " + c.summary());
        Purchase p = purchases.get(purchaseId);
        CatalogEntry e = catalog.get(p.catalogId);
        LiftDesign d = planLift(purchaseId, lower, upper, terrain);
        int rot = Rotations.facing(upper.x() - lower.x(), upper.z() - lower.z());
        Job j = newJob(p, e, lower, rot, gameTime);
        j.liftTop = upper;
        j.towers = new ArrayList<>(d.towerTops);
        j.towerBases = new ArrayList<>(d.towerBases);
        regeneratePlacements(j);
        // The zone that blocks other projects is the lower station; towers/upper station are checked above.
        j.zone = TemplatePlacer.footprint(catalog.template(e.template), lower.x(), lower.y(), lower.z(), rot).grow(1, 0);
        notice(player + " laid out " + e.name + " (" + d.towerTops.size() + " towers, " + (int) d.horizontalLength + " m). Hire a crew at the lower station.");
        return ActionResult.ok("Lift zone placed.", j.id);
    }

    private Job newJob(Purchase p, CatalogEntry e, Pos anchor, int rot, long gameTime) {
        Job j = new Job();
        j.id = "J" + (++counter);
        j.purchaseId = p.id;
        j.catalogId = e.id;
        j.name = e.name;
        j.anchor = anchor;
        j.rotation = rot;
        j.laborQuote = e.laborCost;
        j.createdAt = gameTime;
        p.state = Purchase.State.PLACED;
        p.jobId = j.id;
        jobs.put(j.id, j);
        return j;
    }

    /** Placements are a pure function of (template, anchor, rotation, lift line) so they survive restarts. */
    public void regeneratePlacements(Job j) {
        CatalogEntry e = catalog.get(j.catalogId);
        if (e == null) { j.placements = List.of(); return; }
        Template t = catalog.template(e.template);
        List<Placement> out = new ArrayList<>(TemplatePlacer.placements(t, j.anchor.x(), j.anchor.y(), j.anchor.z(), j.rotation));
        if (e.kind == EntryKind.LIFT && j.liftTop != null) {
            for (int i = 0; i < j.towers.size(); i++)
                out.addAll(towerPlacements(j.towers.get(i), j.towerBases.get(i), j.rotation));
            Template ut = catalog.template(e.upperTemplate);
            out.addAll(TemplatePlacer.placements(ut, j.liftTop.x(), j.liftTop.y(), j.liftTop.z(), j.rotation));
            // Keep stage order across stations and towers.
            out.sort(java.util.Comparator.comparingInt((Placement p) -> p.stage().ordinal()));
        }
        j.placements = out;
    }

    private List<Placement> towerPlacements(Pos top, int baseY, int rot) {
        List<Placement> out = new ArrayList<>();
        out.add(new Placement(top.x(), baseY - 1, top.z(), "minecraft:polished_andesite", Stage.FOUNDATION, rot));
        for (int y = baseY; y < top.y(); y++)
            out.add(new Placement(top.x(), y, top.z(), "blockside:tower_section", Stage.STRUCTURE, rot));
        out.add(new Placement(top.x(), top.y(), top.z(), "minecraft:polished_andesite", Stage.STRUCTURE, rot));
        boolean alongZ = rot == 0 || rot == 2;
        for (int s : new int[]{-2, -1, 1, 2}) {
            int x = top.x() + (alongZ ? s : 0), z = top.z() + (alongZ ? 0 : s);
            out.add(new Placement(x, top.y(), z, "minecraft:polished_andesite_slab[type=top]", Stage.EQUIPMENT, rot));
        }
        out.sort(java.util.Comparator.comparingInt(Placement::y));
        return out;
    }

    // ------------------------------------------------------------------ crews

    public long hireQuote(Job j) {
        boolean idle = crews.values().stream().anyMatch(Crew::idle);
        return idle ? Math.round(j.laborQuote * config.reassignDiscount) : j.laborQuote;
    }

    public Crew idleCrew() { return crews.values().stream().filter(Crew::idle).findFirst().orElse(null); }

    /** Idempotent: a second click (or a second player) on a staffed job changes nothing and charges nothing. */
    public HireResult hire(String jobId, String player, long gameTime) {
        if (!isMember(player)) return HireResult.NO_PERMISSION;
        Job j = jobs.get(jobId);
        if (j == null) return HireResult.NOT_FOUND;
        if (j.state == JobState.COMPLETE) return HireResult.ALREADY_COMPLETE;
        if (j.crewId != null) return HireResult.ALREADY_STAFFED;
        Crew idle = idleCrew();
        long cost = hireQuote(j);
        if (!ledger.trySpend(cost, TxKind.CREW_LABOR, "Crew for " + j.name, gameTime)) return HireResult.INSUFFICIENT_FUNDS;
        Crew c = idle;
        if (c == null) {
            c = new Crew();
            c.id = "C" + (++counter);
            c.name = CREW_NAMES[(int) (crews.size() % CREW_NAMES.length)];
            c.hiredAt = gameTime;
            crews.put(c.id, c);
        }
        c.jobId = j.id;
        j.crewId = c.id;
        j.state = JobState.ACTIVE;
        notice(player + " put " + c.name + " to work on " + j.name + " ($" + cost + ").");
        return idle != null ? HireResult.ASSIGNED : HireResult.HIRED;
    }

    private static final String[] CREW_NAMES = {"Timberline Builders", "Ridgecrest Crew", "Summit Works", "Powderhorn Contractors"};

    /** World adapter applies one placement; returns null on success or a reason it couldn't. */
    public interface Builder { String apply(Placement p); }

    /** One work step for every active job. */
    public void workStep(Builder builder, long gameTime) {
        for (Job j : new ArrayList<>(jobs.values())) {
            if (j.state != JobState.ACTIVE && j.state != JobState.BLOCKED) continue;
            if (j.placements == null) regeneratePlacements(j);
            int real = config.crewBlocksPerStep, cheap = 12;
            j.state = JobState.ACTIVE;
            j.blockedReason = null;
            while (j.cursor < j.placements.size()) {
                Placement p = j.placements.get(j.cursor);
                boolean isCheap = p.spec().startsWith("@clear") || p.spec().startsWith("?");
                if (isCheap ? cheap <= 0 : real <= 0) break;
                String err = builder.apply(p);
                if (err != null) {
                    j.state = JobState.BLOCKED;
                    j.blockedReason = err + " at " + p.x() + " " + p.y() + " " + p.z();
                    break;
                }
                j.cursor++;
                if (isCheap) cheap--; else real--;
            }
            if (j.cursor >= j.placements.size()) complete(j, gameTime);
        }
    }

    private void complete(Job j, long gameTime) {
        j.state = JobState.COMPLETE;
        j.completedAt = gameTime;
        Crew c = crews.get(j.crewId);
        if (c != null) c.jobId = null;
        CatalogEntry e = catalog.get(j.catalogId);
        String added;
        if (e.kind == EntryKind.LIFT) {
            LiftRecord l = new LiftRecord();
            l.id = "L" + (++counter);
            l.catalogId = e.id;
            l.name = e.name + " " + (lifts.size() + 1);
            l.lower = j.anchor;
            l.upper = j.liftTop;
            l.towers = new ArrayList<>(j.towers);
            l.speed = e.liftSpeed;
            l.seats = e.seatsPerChair;
            l.spacing = e.chairSpacing;
            l.capacity = e.capacity;
            l.open = false;
            lifts.put(l.id, l);
            added = l.name + " is ready: open it at the lower station (" + e.capacity + " riders/hour).";
        } else {
            Facility f = new Facility();
            f.id = "F" + (++counter);
            f.catalogId = e.id;
            f.name = e.name;
            f.category = e.category;
            f.service = e.service;
            f.capacity = e.capacity;
            f.anchor = j.anchor;
            Template t = catalog.template(e.template);
            f.bounds = TemplatePlacer.footprint(t, j.anchor.x(), j.anchor.y(), j.anchor.z(), j.rotation);
            if (t.entrance != null) {
                int[] w = TemplatePlacer.toWorld(t, t.entrance, j.anchor.x(), j.anchor.y(), j.anchor.z(), j.rotation);
                f.entrance = new Pos(w[0], w[1], w[2]);
            }
            f.builtAt = gameTime;
            facilities.put(f.id, f);
            added = f.name + " is open: " + e.service + " (capacity " + e.capacity + ").";
        }
        notice("Construction complete! " + added);
        progression.onConstructionComplete(config.milestones, e.id, e.name);
        flushProgressNotices();
    }

    public Job jobAt(Pos anchor) {
        for (Job j : jobs.values()) if (j.anchor.equals(anchor)) return j;
        return null;
    }

    public LiftRecord liftAt(Pos lowerAnchor) {
        for (LiftRecord l : lifts.values()) if (l.lower.equals(lowerAnchor)) return l;
        return null;
    }

    public Facility facilityAt(Pos anchor) {
        for (Facility f : facilities.values()) if (f.anchor.equals(anchor)) return f;
        return null;
    }

    public boolean hasService(String catalogId) {
        return facilities.values().stream().anyMatch(f -> f.catalogId.equals(catalogId) && f.open);
    }

    // ------------------------------------------------------------------ trails

    public Trail addTrail(String name, List<int[]> centerline, double width, TrailDesign.Result d) {
        Trail t = new Trail();
        t.id = "T" + (++counter);
        t.name = name;
        t.centerline = new ArrayList<>(centerline);
        t.width = width;
        updateTrail(t, d);
        trails.put(t.id, t);
        notice("Trail " + name + " marked: " + t.difficulty.label + String.format(", %.0f° average", t.avgGradeDeg) + ".");
        return t;
    }

    /** (Re)builds the trail's snow tiles from a fresh design, keeping existing snow mass. */
    public void updateTrail(Trail t, TrailDesign.Result d) {
        t.difficulty = d.difficulty;
        t.avgGradeDeg = d.avgGradeDeg;
        t.maxGradeDeg = d.maxGradeDeg;
        t.length = d.length;
        t.flags = d.flags.size();
        t.tiles.clear();
        for (TrailDesign.Column c : d.columns.values()) {
            SnowTile tile = snowpack.getOrCreate(c.x, c.z, c.ground, c.design);
            tile.ground = c.ground;
            tile.design = c.design;
            tile.sun = c.sun;
            t.tiles.add(Pos.columnKey(c.x, c.z));
        }
    }

    public void refreshTrailStats() {
        for (Trail t : trails.values()) {
            int cov = 0, gr = 0, n = 0;
            for (long k : t.tiles) {
                SnowTile s = snowpack.get(Pos.keyX(k), Pos.keyZ(k));
                if (s == null) continue;
                n++;
                if (s.covered(config.snow)) cov++;
                if (s.groomed > 0.35) gr++;
            }
            t.coverage = n == 0 ? 0 : (double) cov / n;
            t.groomedFraction = n == 0 ? 0 : (double) gr / n;
        }
    }

    // ------------------------------------------------------------------ hourly simulation

    /** Runs whole game hours that have elapsed. Called from the server tick. */
    public void tickHours(long gameTime) {
        long hour = Math.floorDiv(gameTime, 1000L);
        if (lastHourTick < 0) { lastHourTick = hour; return; }
        int guard = 0;
        while (lastHourTick < hour && guard++ < 48) {
            lastHourTick++;
            hourly(lastHourTick * 1000L);
        }
        lastHourTick = hour;
    }

    void hourly(long gameTime) {
        double hourOfDay = Weather.hourOfDay(gameTime);
        long day = Weather.day(gameTime);
        if (day != lastDay) {
            lastDay = day;
            guestsToday = 0; revenueToday = 0;
            for (LiftRecord l : lifts.values()) l.ridesToday = 0;
            long busy = crews.size();
            if (busy > 0) ledger.charge(busy * config.crewDailyWage, TxKind.CREW_WAGES, busy + " crew(s) daily wages", gameTime);
        }
        refreshTrailStats();
        boolean openHours = hourOfDay >= config.openHour && hourOfDay < config.closeHour;
        Climate.Season season = weather.season(config.climate, gameTime);
        double seasonFactor = switch (season) { case WINTER -> 1.0; case SPRING -> 0.7; case AUTUMN -> 0.25; case SUMMER -> 0; };

        int openLifts = 0; double cap = 0;
        for (LiftRecord l : lifts.values()) {
            if (l.open) {
                openLifts++; cap += l.capacity; l.openHours++;
                ledger.charge(config.liftPowerCostPerHour, TxKind.OPERATING_POWER, l.name + " power", gameTime);
            } else if (openHours && season == Climate.Season.WINTER) {
                l.downHours++;
            }
        }
        int openRuns = (int) trails.values().stream().filter(t -> t.open).count();
        int boothCap = facilities.values().stream().filter(f -> f.open && "ticket_booth".equals(f.catalogId)).mapToInt(f -> f.capacity).sum();
        GuestSim.Outputs out = GuestSim.hour(new GuestSim.Inputs(reputation.value(), openRuns, openLifts, cap, boothCap,
                hasService("rental_shop"), config.baseArrivalsPerHour, openHours ? seasonFactor : 0,
                config.ridesPerGuestHour, guestsOnMountain, config.guestStayHours));
        guestsOnMountain = out.guestsOnMountain();
        lastWaitMinutes = out.waitMinutes();
        for (LiftRecord l : lifts.values()) if (l.open) {
            l.guestQueue = out.queue() * l.capacity / Math.max(1, cap);
            l.waitMinutes = out.waitMinutes();
            l.ridesToday += Math.round(out.ridesTaken() * l.capacity / Math.max(1, cap));
        } else { l.guestQueue = 0; l.waitMinutes = 0; }
        if (out.arrivals() > 0) {
            long tickets = out.arrivals() * config.ticketPrice;
            ledger.earn(tickets, TxKind.GUEST_TICKET, out.arrivals() + " lift tickets", gameTime);
            ticketsSold += out.arrivals();
            guestsToday += out.arrivals();
            revenueToday += tickets;
            if (out.rentals() > 0) {
                long r = out.rentals() * config.rentalPrice;
                ledger.earn(r, TxKind.GUEST_RENTAL, out.rentals() + " rentals", gameTime);
                revenueToday += r;
            }
        }
        progression.onRevenue(config.milestones, ledger.lifetimeGuestRevenue());

        List<ReputationFactor> factors = reputationFactors(season, openRuns, openLifts);
        if (openHours && season != Climate.Season.SUMMER && claimed()) reputation.update(factors, config.reputationBlendPerHour);
        else reputation.update(factors, 0); // still refresh the breakdown
        progression.onReputation(config.milestones, reputation.value(), gameTime);
        flushProgressNotices();
    }

    public List<ReputationFactor> reputationFactors(Climate.Season season, int openRuns, int openLifts) {
        List<ReputationFactor> f = new ArrayList<>();
        double snowScore = 0; int n = 0;
        for (Trail t : trails.values()) if (t.open) { snowScore += 100 * (0.75 * t.coverage + 0.25 * t.groomedFraction); n++; }
        snowScore = n == 0 ? 20 : snowScore / n;
        f.add(new ReputationFactor("snow", "Snow quality", snowScore, config.wSnow,
                n == 0 ? "No open runs" : snowScore < 60 ? "Thin or ungroomed snow on open runs" : "Good coverage"));
        double wait = Math.max(0, 100 - lastWaitMinutes * 100 / 30.0);
        f.add(new ReputationFactor("wait", "Lift lines", openLifts == 0 ? 30 : wait, config.wWait,
                openLifts == 0 ? "No lift running" : lastWaitMinutes > 10 ? "Long lift lines" : "Short lift lines"));
        double terrain = Math.min(100, 20 + 25 * openRuns + 20 * openLifts);
        f.add(new ReputationFactor("terrain", "Open terrain", terrain, config.wTerrain, openRuns + " runs, " + openLifts + " lifts open"));
        double fac = 25;
        if (hasService("ticket_booth")) fac += 30;
        if (hasService("rental_shop")) fac += 20;
        if (hasService("small_lodge")) fac += 25;
        f.add(new ReputationFactor("facilities", "Facilities", Math.min(100, fac), config.wFacilities,
                hasService("ticket_booth") ? "Guest services available" : "No ticket booth"));
        double up = 0, tot = 0;
        for (LiftRecord l : lifts.values()) { up += l.openHours; tot += l.openHours + l.downHours; }
        double rel = tot == 0 ? 60 : 100 * up / tot;
        f.add(new ReputationFactor("reliability", "Reliable operations", rel, config.wReliability,
                rel < 70 ? "Lifts often closed in season" : "Lifts run when expected"));
        return f;
    }

    private void flushProgressNotices() { for (String s : progression.drainNotices()) notice(s); }

    // ------------------------------------------------------------------ snow

    /** Elevation-aware temperature at a tile. */
    public double temperatureAt(long gameTime, int y) { return weather.temperature(config.climate, gameTime, y); }

    /**
     * Slow-clock snowpack update. Tiles whose chunk is loaded are advanced to gameTime, integrating any
     * time they spent unloaded in hour-sized steps against the (deterministic) weather history.
     * @return tiles that changed (for the world adapter to re-render)
     */
    public List<SnowTile> snowStep(long gameTime, java.util.function.LongPredicate loaded) {
        List<SnowTile> changed = new ArrayList<>();
        double[] runoff = {0};
        for (SnowTile t : snowpack.all()) {
            if (!loaded.test(Pos.columnKey(t.x, t.z))) continue;
            if (t.lastTick <= 0 || t.lastTick > gameTime) { t.lastTick = gameTime; continue; }
            long elapsed = Math.min(gameTime - t.lastTick, 72_000L);
            long at = gameTime - elapsed;
            while (elapsed > 0) {
                // Step to the next whole hour so a catch-up matches hour-by-hour simulation exactly.
                long step = Math.min(1000L - Math.floorMod(at, 1000L), elapsed);
                at += step;
                elapsed -= step;
                Snowpack.step(t, temperatureAt(at, (int) Math.round(t.surface())), weather.snowfall(config.climate, at),
                        step / 1000.0, config.snow, runoff);
            }
            t.lastTick = gameTime;
            changed.add(t);
        }
        snowpack.runoff += runoff[0];
        return changed;
    }

    /** Tiles a snow gun at pos facing rot would cover, with weights (peaking mid-throw). */
    public List<SnowTile> gunFootprint(Pos pos, int rot, List<Double> weightsOut) {
        List<SnowTile> out = new ArrayList<>();
        int range = config.snowGunRange;
        for (int d = 2; d <= range; d++) {
            double spread = 1 + (config.snowGunWidth / 2.0 - 1) * (d / (double) range);
            for (int l = (int) -Math.floor(spread); l <= (int) Math.floor(spread); l++) {
                int[] r = Rotations.rotate(l, -d, rot);
                SnowTile t = snowpack.get(pos.x() + r[0], pos.z() + r[1]);
                if (t == null) continue;
                double along = 1 - Math.abs(d - range * 0.55) / range;
                double across = 1 - Math.abs(l) / (spread + 1);
                out.add(t);
                weightsOut.add(Math.max(0.05, along * across));
            }
        }
        return out;
    }

    /** Runs a snow gun for dtHours. Water/power come from the world adapter. */
    public List<SnowTile> runSnowGun(SnowGun g, boolean water, boolean power, double dtHours, long gameTime) {
        g.tempC = temperatureAt(gameTime, g.pos.y());
        List<Double> w = new ArrayList<>();
        List<SnowTile> fp = gunFootprint(g.pos, g.facing, w);
        if (!g.on) g.status = SnowGun.Status.OFF;
        else if (!power) g.status = SnowGun.Status.NO_POWER;
        else if (!water) g.status = SnowGun.Status.NO_WATER;
        else if (g.tempC > config.climate.snowGunCutoff) g.status = SnowGun.Status.TOO_WARM;
        else if (fp.isEmpty()) g.status = SnowGun.Status.NO_TERRAIN;
        else if (ledger.cash() <= 0) g.status = SnowGun.Status.NO_FUNDS;
        else g.status = SnowGun.Status.RUNNING;
        if (g.status != SnowGun.Status.RUNNING) return List.of();
        double swe = config.snowGunSwePerHour * dtHours;
        double[] weights = w.stream().mapToDouble(Double::doubleValue).toArray();
        Snowpack.deposit(fp, weights, swe, config.snow);
        g.madeSwe += swe;
        ledger.charge(Math.round(config.snowGunCostPerHour * dtHours), TxKind.OPERATING_WATER, "Snowmaking", gameTime);
        return fp;
    }

    /** Grooms every tile the tiller swept between two simulation steps. */
    public List<SnowTile> groomPath(double x0, double z0, double x1, double z1, double halfWidth) {
        Set<Long> keys = new LinkedHashSet<>();
        double len = Math.hypot(x1 - x0, z1 - z0);
        int steps = Math.max(1, (int) Math.ceil(len / 0.5));
        double dx = len < 1e-6 ? 0 : (x1 - x0) / len, dz = len < 1e-6 ? 0 : (z1 - z0) / len;
        double px = -dz, pz = dx;
        for (int i = 0; i <= steps; i++) {
            double cx = x0 + (x1 - x0) * i / steps, cz = z0 + (z1 - z0) * i / steps;
            for (double o = -halfWidth; o <= halfWidth + 1e-6; o += 0.5)
                keys.add(Pos.columnKey((int) Math.floor(cx + px * o), (int) Math.floor(cz + pz * o)));
        }
        List<SnowTile> pass = new ArrayList<>();
        for (long k : keys) { SnowTile t = snowpack.get(Pos.keyX(k), Pos.keyZ(k)); if (t != null) pass.add(t); }
        snowpack.groom(pass, config.snow);
        return pass;
    }

    // ------------------------------------------------------------------ notices

    public void notice(String s) { if (notices == null) notices = new ArrayList<>(); notices.add(s); }

    public List<String> drainNotices() {
        if (notices == null) notices = new ArrayList<>();
        List<String> out = new ArrayList<>(notices);
        notices.clear();
        return out;
    }
}
