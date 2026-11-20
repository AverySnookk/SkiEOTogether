package dev.blockside.core;

import dev.blockside.core.construction.Job;
import dev.blockside.core.construction.JobState;
import dev.blockside.core.construction.PlacementCheck;
import dev.blockside.core.construction.Purchase;
import dev.blockside.core.construction.TemplatePlacer;
import dev.blockside.core.economy.TxKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionTest {
    static String buy(Resort r, String id) {
        ActionResult a = r.buy(id, "alex", 0);
        assertTrue(a.ok(), a.message());
        return a.ref();
    }

    static void runToCompletion(Resort r, TestWorld w) {
        for (int i = 0; i < 5000 && r.jobs.values().stream().anyMatch(j -> j.state == JobState.ACTIVE); i++) r.workStep(w, i);
    }

    @Test void buyPlaceHireBuild() {
        Resort r = TestWorld.newResort();
        TestWorld w = TestWorld.flat(64);
        long cash0 = r.ledger.cash();
        String kit = buy(r, "ticket_booth");
        assertEquals(cash0 - 3000, r.ledger.cash());
        ActionResult placed = r.placeBuilding(kit, new Pos(10, 64, 10), 0, w, "alex", 0);
        assertTrue(placed.ok(), placed.message());
        Job j = r.jobs.get(placed.ref());
        assertEquals(JobState.PLANNED, j.state, "construction waits for a crew");
        r.workStep(w, 1);
        assertEquals(0, j.cursor, "no progress without a crew");
        assertEquals(HireResult.HIRED, r.hire(j.id, "alex", 2));
        runToCompletion(r, w);
        assertEquals(JobState.COMPLETE, j.state);
        assertTrue(r.hasService("ticket_booth"));
        assertEquals("minecraft:stone_bricks", w.spec(10, 63, 10), "foundation replaces the top ground layer");
        // Every edit stayed inside the approved footprint (plus support fill directly below).
        var fp = TemplatePlacer.footprint(r.catalog.template("ticket_booth"), 10, 64, 10, 0);
        for (Pos p : w.blocks.keySet()) assertTrue(fp.grow(0, 2).contains(p.x(), p.y(), p.z()), "edit outside footprint " + p);
        assertEquals(1, r.crews.size());
        assertTrue(r.crews.values().iterator().next().idle(), "crew is released after the job");
    }

    @Test void doubleClickHireChargesOnce() {
        Resort r = TestWorld.newResort();
        TestWorld w = TestWorld.flat(64);
        String kit = buy(r, "ticket_booth");
        Job j = r.jobs.get(r.placeBuilding(kit, new Pos(10, 64, 10), 0, w, "alex", 0).ref());
        long before = r.ledger.cash();
        assertTrue(r.hire(j.id, "alex", 1).ok());
        assertEquals(HireResult.ALREADY_STAFFED, r.hire(j.id, "alex", 1));
        assertEquals(HireResult.ALREADY_STAFFED, r.hire(j.id, "sam", 1), "second player clicking at the same time");
        assertEquals(before - j.laborQuote, r.ledger.cash());
        assertEquals(1, r.crews.size());
    }

    @Test void overlappingZonesRejected() {
        Resort r = TestWorld.newResort();
        TestWorld w = TestWorld.flat(64);
        String a = buy(r, "ticket_booth"), b = buy(r, "ticket_booth");
        assertTrue(r.placeBuilding(a, new Pos(10, 64, 10), 0, w, "alex", 0).ok());
        ActionResult second = r.placeBuilding(b, new Pos(13, 64, 10), 0, w, "sam", 0);
        assertFalse(second.ok());
        assertTrue(second.message().contains("Overlaps"), second.message());
        assertEquals(Purchase.State.KIT, r.purchases.get(b).state, "rejected placement keeps the kit");
        assertTrue(r.placeBuilding(b, new Pos(20, 64, 10), 0, w, "sam", 0).ok());
    }

    @Test void kitCanOnlyBePlacedOnce() {
        Resort r = TestWorld.newResort();
        TestWorld w = TestWorld.flat(64);
        String a = buy(r, "ticket_booth");
        assertTrue(r.placeBuilding(a, new Pos(10, 64, 10), 0, w, "alex", 0).ok());
        assertFalse(r.placeBuilding(a, new Pos(40, 64, 40), 0, w, "sam", 0).ok(), "duplicated kit item");
        assertFalse(r.refund(a, "alex", 0).ok(), "can't refund a placed kit");
    }

    @Test void illegalPlacements() {
        Resort r = TestWorld.newResort();
        TestWorld w = TestWorld.flat(64);
        String kit = buy(r, "ticket_booth");
        // Outside claim.
        assertFalse(r.placeBuilding(kit, new Pos(500, 64, 500), 0, w, "alex", 0).ok());
        // Someone's house in the way.
        w.blocks.put(new Pos(11, 65, 11), "minecraft:oak_planks");
        PlacementCheck c = r.checkBuilding(kit, new Pos(10, 64, 10), 0, w);
        assertFalse(c.ok());
        assertTrue(c.summary().contains("protected"), c.summary());
        assertEquals(new Pos(11, 65, 11), c.blockedAt.get(0));
        // Water.
        w.blocks.clear();
        w.blocks.put(new Pos(9, 64, 9), "minecraft:water");
        assertFalse(r.checkBuilding(kit, new Pos(10, 64, 10), 0, w).ok());
        // Cliff edge: ground drops away under most of the foundation.
        TestWorld cliff = new TestWorld((x, z) -> x >= 10 ? 64 : 50);
        assertFalse(r.checkBuilding(kit, new Pos(10, 64, 10), 0, cliff).ok());
        // Trees are fine: the crew clears them.
        TestWorld trees = TestWorld.flat(64);
        trees.blocks.put(new Pos(11, 64, 11), "minecraft:oak_log");
        PlacementCheck ok = r.checkBuilding(kit, new Pos(10, 64, 10), 0, trees);
        assertTrue(ok.ok(), ok.summary());
        assertTrue(ok.clearBlocks > 0);
        // Not a member (closed membership).
        r.config.openMembership = false;
        assertFalse(r.placeBuilding(kit, new Pos(10, 64, 10), 0, w, "stranger", 0).ok());
    }

    @Test void saveAndReloadDuringJobResumesFromSavedStage() {
        Resort r = TestWorld.newResort();
        TestWorld w = TestWorld.flat(64);
        String kit = buy(r, "ticket_booth");
        Job j = r.jobs.get(r.placeBuilding(kit, new Pos(10, 64, 10), 1, w, "alex", 0).ref());
        r.hire(j.id, "alex", 0);
        for (int i = 0; i < 15; i++) r.workStep(w, i);
        int cursor = j.cursor;
        var stage = j.stage();
        assertTrue(cursor > 0 && cursor < j.total());
        long cash = r.ledger.cash();

        Resort loaded = Resort.fromJson(r.toJson(), TestWorld.config(), TestWorld.catalog());
        Job lj = loaded.jobs.get(j.id);
        assertEquals(cursor, lj.cursor);
        assertEquals(stage, lj.stage());
        assertEquals(JobState.ACTIVE, lj.state);
        assertEquals(j.total(), lj.total(), "placements regenerate identically");
        assertEquals(cash, loaded.ledger.cash());
        assertEquals(HireResult.ALREADY_STAFFED, loaded.hire(j.id, "alex", 1), "no re-charge after reload");
        ConstructionTest.runToCompletion(loaded, w);
        assertEquals(JobState.COMPLETE, lj.state);
        assertEquals(1, loaded.facilities.size());
    }

    @Test void idleCrewIsReassignedAtDiscount() {
        Resort r = TestWorld.newResort();
        TestWorld w = TestWorld.flat(64);
        Job a = r.jobs.get(r.placeBuilding(buy(r, "ticket_booth"), new Pos(10, 64, 10), 0, w, "alex", 0).ref());
        r.hire(a.id, "alex", 0);
        runToCompletion(r, w);
        Job b = r.jobs.get(r.placeBuilding(buy(r, "groomer_garage"), new Pos(30, 64, 10), 0, w, "alex", 0).ref());
        long before = r.ledger.cash();
        assertEquals(HireResult.ASSIGNED, r.hire(b.id, "alex", 0));
        assertEquals(Math.round(b.laborQuote * r.config.reassignDiscount), before - r.ledger.cash());
        assertEquals(1, r.crews.size());
    }

    @Test void blockedJobReportsReasonAndResumes() {
        Resort r = TestWorld.newResort();
        TestWorld w = TestWorld.flat(64);
        Job j = r.jobs.get(r.placeBuilding(buy(r, "ticket_booth"), new Pos(10, 64, 10), 0, w, "alex", 0).ref());
        r.hire(j.id, "alex", 0);
        boolean[] fail = {true};
        r.workStep(p -> fail[0] ? "Chunk not loaded" : w.apply(p), 0);
        assertEquals(JobState.BLOCKED, j.state);
        assertTrue(j.blockedReason.startsWith("Chunk not loaded"));
        fail[0] = false;
        r.workStep(p -> w.apply(p), 1);
        assertEquals(JobState.ACTIVE, j.state);
    }

    @Test void cannotBuyWithoutCashOrLevel() {
        Resort r = TestWorld.newResort();
        assertFalse(r.buy("rental_shop", "alex", 0).ok(), "level 2 entry locked at level 1");
        r.ledger.charge(r.ledger.cash(), TxKind.ADMIN, "drain", 0);
        assertFalse(r.buy("ticket_booth", "alex", 0).ok());
        assertTrue(r.purchases.isEmpty());
    }

    @Test void templatesAreValidAndRotationsStayInFootprint() {
        Resort r = TestWorld.newResort();
        for (String id : new String[]{"ticket_booth", "rental_shop", "groomer_garage", "small_lodge", "lift_lower_station", "lift_upper_station"}) {
            var t = r.catalog.template(id);
            assertNotNull(t, id);
            assertTrue(t.validate().isEmpty(), id + " " + t.validate());
            for (int rot = 0; rot < 4; rot++) {
                var fp = TemplatePlacer.footprint(t, 0, 64, 0, rot);
                for (var p : TemplatePlacer.placements(t, 0, 64, 0, rot))
                    assertTrue(fp.grow(0, 2).contains(p.x(), p.y(), p.z()), id + " rot " + rot);
            }
        }
    }
}
