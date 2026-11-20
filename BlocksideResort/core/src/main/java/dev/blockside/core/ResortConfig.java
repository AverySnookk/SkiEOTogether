package dev.blockside.core;

import dev.blockside.core.economy.Milestone;
import dev.blockside.core.lift.LiftParams;
import dev.blockside.core.ski.SkiParams;
import dev.blockside.core.snow.Climate;
import dev.blockside.core.snow.SnowParams;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** All gameplay tuning, loaded from blockside/resort_config.json (overridable per server). */
public final class ResortConfig {
    public long startingCash = 25000;
    /** levelXp[i] = XP needed for level i+1 (levelXp[0] = 0). */
    public long[] levelXp = {0, 200, 500, 1000, 1800, 3000};
    public List<Milestone> milestones = new ArrayList<>();
    public long ticketPrice = 45;
    public long rentalPrice = 25;
    public double rentalShare = 0.35;
    /** Crew: blocks placed per work step, work step interval (ticks), daily wage, re-assignment discount. */
    public int crewBlocksPerStep = 2;
    public int crewStepTicks = 10;
    public long crewDailyWage = 60;
    public double reassignDiscount = 0.6;
    public int claimRadius = 160;
    /** Everyone on the server may manage the resort (friends' server). Otherwise only invited members. */
    public boolean openMembership = true;
    /** Guests: base arrivals per open hour at reputation 50 with one lift and one run. */
    public double baseArrivalsPerHour = 14;
    public int openHour = 8, closeHour = 17;
    /** Guests take this many lift rides per hour on the mountain. */
    public double ridesPerGuestHour = 2.2;
    public double guestStayHours = 3;
    /** Reputation factor weights. */
    public double wSnow = 3, wWait = 2, wTerrain = 2, wFacilities = 1.5, wReliability = 1.5;
    public double reputationBlendPerHour = 0.15;
    public long liftPowerCostPerHour = 12;
    public long snowGunCostPerHour = 18;
    public double snowGunSwePerHour = 0.05;
    public int snowGunRange = 10;
    public int snowGunWidth = 6;
    public int snowStepTicks = 100;
    public int earthworkBlocksPerTick = 48;
    public int maxVisibleGuests = 4;
    public SnowParams snow = new SnowParams();
    public Climate climate = new Climate();
    public LiftParams lift = new LiftParams();
    public SkiParams ski = new SkiParams();

    public static ResortConfig loadBuiltin() {
        try (var in = ResortConfig.class.getClassLoader().getResourceAsStream("blockside/resort_config.json")) {
            if (in == null) return new ResortConfig();
            return Json.GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), ResortConfig.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public int level(long xp) { return dev.blockside.core.economy.Progression.levelFor(xp, levelXp); }
}
