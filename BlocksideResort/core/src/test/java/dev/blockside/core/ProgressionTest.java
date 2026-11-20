package dev.blockside.core;

import dev.blockside.core.construction.JobState;
import dev.blockside.core.economy.Milestone;
import dev.blockside.core.economy.MilestoneKind;
import dev.blockside.core.economy.TxKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgressionTest {
    @Test void revenueXpAwardedOnceAndIgnoresPurchaseRefundSpam() {
        Resort r = TestWorld.newResort();
        for (int i = 0; i < 50; i++) {
            String id = r.buy("ticket_booth", "alex", 0).ref();
            r.refund(id, "alex", 0);
        }
        r.progression.onRevenue(r.config.milestones, r.ledger.lifetimeGuestRevenue());
        assertEquals(0, r.progression.xp(), "refunds are not guest revenue");
        r.ledger.earn(1200, TxKind.GUEST_TICKET, "tickets", 0);
        r.progression.onRevenue(r.config.milestones, r.ledger.lifetimeGuestRevenue());
        r.progression.onRevenue(r.config.milestones, r.ledger.lifetimeGuestRevenue());
        assertEquals(100, r.progression.xp());
    }

    @Test void constructionXpOncePerUniqueProject() {
        Resort r = TestWorld.newResort();
        TestWorld w = TestWorld.flat(64);
        for (int i = 0; i < 2; i++) {
            String kit = r.buy("ticket_booth", "alex", 0).ref();
            var j = r.jobs.get(r.placeBuilding(kit, new Pos(10 + 20 * i, 64, 10), 0, w, "alex", 0).ref());
            r.hire(j.id, "alex", 0);
            ConstructionTest.runToCompletion(r, w);
            assertEquals(JobState.COMPLETE, j.state);
        }
        assertEquals(50, r.progression.xp(), "second (re)build of the same project pays nothing");
    }

    @Test void reputationMilestoneNeedsSustainAndPaysOnceDespiteDips() {
        Resort r = TestWorld.newResort();
        List<Milestone> ms = List.of(new Milestone("r60", MilestoneKind.REPUTATION, 60, "", 2000, 100, "rep"));
        r.progression.onReputation(ms, 65, 0);
        r.progression.onReputation(ms, 65, 1500);
        r.progression.onReputation(ms, 55, 1600); // dip resets
        r.progression.onReputation(ms, 65, 1700);
        r.progression.onReputation(ms, 65, 3000);
        assertEquals(0, r.progression.xp(), "dip reset the sustain timer");
        r.progression.onReputation(ms, 65, 3700);
        assertEquals(100, r.progression.xp());
        for (long t = 3800; t < 20000; t += 100) r.progression.onReputation(ms, t % 200 == 0 ? 50 : 70, t);
        assertEquals(100, r.progression.xp(), "oscillating around the threshold never pays again");
    }

    @Test void awardSurvivesReloadWithoutRepeating() {
        Resort r = TestWorld.newResort();
        r.ledger.earn(1500, TxKind.GUEST_TICKET, "tickets", 0);
        r.progression.onRevenue(r.config.milestones, r.ledger.lifetimeGuestRevenue());
        Resort again = Resort.fromJson(r.toJson(), TestWorld.config(), TestWorld.catalog());
        again.progression.onRevenue(again.config.milestones, again.ledger.lifetimeGuestRevenue());
        assertEquals(100, again.progression.xp(), "a reconnect/restart mid-award can't pay twice");
    }

    @Test void milestonesUnlockNextCatalogEntry() {
        Resort r = TestWorld.newResort();
        assertNotNull(r.lockReason(r.catalog.get("rental_shop")));
        r.ledger.earn(1200, TxKind.GUEST_TICKET, "tickets", 0);
        r.progression.onRevenue(r.config.milestones, r.ledger.lifetimeGuestRevenue());
        r.progression.onReputation(r.config.milestones, 70, 0);
        r.progression.onReputation(r.config.milestones, 70, 2000);
        assertEquals(200, r.progression.xp());
        assertEquals(2, r.level());
        assertNull(r.lockReason(r.catalog.get("rental_shop")), "level 2 unlocks the rental shop");
        assertTrue(r.buy("rental_shop", "alex", 0).ok());
    }

    @Test void twoPlayersSeeSameState() {
        Resort r = TestWorld.newResort();
        TestWorld w = TestWorld.flat(64);
        String kit = r.buy("ticket_booth", "alex", 0).ref();
        var j = r.jobs.get(r.placeBuilding(kit, new Pos(10, 64, 10), 0, w, "alex", 0).ref());
        r.hire(j.id, "sam", 5);
        r.workStep(w, 6);
        String forAlex = Snapshot.of(r, 100).toJson();
        String forSam = Snapshot.of(r, 100).toJson();
        assertEquals(forAlex, forSam);
        Snapshot s = Snapshot.fromJson(forSam);
        assertEquals(r.ledger.cash(), s.cash);
        assertEquals(1, s.jobs.size());
        assertTrue(s.jobs.get(0).status.startsWith("Site prep"), s.jobs.get(0).status);
    }

    @Test void guestsEarnTicketRevenueOnlyWhenResortCanOperate() {
        Resort r = TestWorld.newResort();
        long start = r.ledger.cash();
        // Winter day 5, 10:00 → gameTime: day 4*24000 + (10-6)*1000.
        long t = (r.config.climate.autumnDays + 2) * 24000L + 4000;
        r.tickHours(t);
        r.tickHours(t + 3000);
        assertEquals(start, r.ledger.cash(), "no booth, no lift, no run: no guests");
        // Give it a booth, a lift and an open run.
        Facility f = new Facility(); f.id = "F"; f.catalogId = "ticket_booth"; f.capacity = 40; f.anchor = new Pos(0, 0, 0);
        f.bounds = new Box(0, 0, 0, 1, 1, 1); r.facilities.put(f.id, f);
        LiftRecord l = new LiftRecord(); l.id = "L"; l.name = "Lift"; l.open = true; l.capacity = 60;
        l.lower = new Pos(0, 64, 0); l.upper = new Pos(0, 80, -40); r.lifts.put(l.id, l);
        Trail tr = new Trail(); tr.id = "T"; tr.name = "Run"; tr.open = true; tr.difficulty = dev.blockside.core.terrain.Difficulty.BEGINNER;
        r.trails.put(tr.id, tr);
        r.tickHours(t + 6000);
        assertTrue(r.ledger.total(TxKind.GUEST_TICKET) > 0);
        assertTrue(r.ticketsSold > 0);
        assertFalse(r.reputation.factors().isEmpty());
    }
}
