package dev.blockside.core;

import dev.blockside.core.terrain.Difficulty;
import dev.blockside.core.terrain.EarthworkPlanner;
import dev.blockside.core.terrain.EditJournal;
import dev.blockside.core.terrain.HeightField;
import dev.blockside.core.terrain.TrailDesign;
import dev.blockside.core.terrain.TrailFlag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TerrainTest {
    /** Staircase descending toward +z: `down` blocks for every `forward` blocks. */
    static HeightField stairs(int down, int forward) {
        return (x, z) -> 100 - Math.floorDiv(Math.max(0, z), forward) * down;
    }

    static TrailDesign.Result design(HeightField h, int len) {
        return new TrailDesign().design(List.of(new int[]{0, 0}, new int[]{0, len}), 7, h);
    }

    @Test void eighteenDegreeFixture() {
        TrailDesign.Result r = design(stairs(1, 3), 45);
        assertEquals(18.4, r.avgGradeDeg, 1.0);
        assertTrue(r.flags.isEmpty(), "1:3 stairs are skiable once snow fills them: " + r.flags);
        assertTrue(r.difficulty.ordinal() <= Difficulty.INTERMEDIATE.ordinal(), r.difficulty.toString());
        for (TrailDesign.Column c : r.columns.values()) {
            assertTrue(c.design >= c.ground - 1e-9, "never below ground");
            assertTrue(c.design - c.ground <= 1.0, "fill stays within one block");
        }
        // Interior columns sit on a smooth plane: neighbouring designed heights differ by about 1/3.
        TrailDesign.Column a = r.columns.get(Pos.columnKey(0, 20)), b = r.columns.get(Pos.columnKey(0, 21));
        assertEquals(1 / 3.0, a.design - b.design, 0.2);
    }

    @Test void fortyFiveDegreeExpertFixture() {
        TrailDesign.Result r = design(stairs(1, 1), 30);
        assertEquals(45, r.avgGradeDeg, 2.0);
        assertTrue(r.difficulty == Difficulty.EXPERT || r.difficulty == Difficulty.ADVANCED, r.difficulty.toString());
        assertTrue(r.flags.stream().noneMatch(f -> f.type() == TrailFlag.Type.GAP));
    }

    @Test void sixtyThreeDegreeChuteIsFlagged() {
        TrailDesign.Result r = design(stairs(2, 1), 20);
        assertEquals(63.4, r.avgGradeDeg, 2.0);
        assertEquals(Difficulty.EXTREME, r.difficulty);
        assertTrue(r.flags.stream().anyMatch(f -> f.type() == TrailFlag.Type.CLIFF), "steeper than the cliff limit");
    }

    @Test void protrusionAndGapAreFlagged() {
        HeightField base = stairs(1, 3);
        HeightField rock = (x, z) -> (x == 0 && z == 20) ? base.top(x, z) + 3 : base.top(x, z);
        assertTrue(design(rock, 45).flags.stream().anyMatch(f -> f.type() == TrailFlag.Type.PROTRUDING));
        HeightField hole = (x, z) -> (Math.abs(x) <= 1 && z >= 20 && z <= 22) ? base.top(x, z) - 4 : base.top(x, z);
        assertTrue(design(hole, 45).flags.stream().anyMatch(f -> f.type() == TrailFlag.Type.GAP));
    }

    @Test void earthworkIsBoundedAndUndoable() {
        HeightField h = stairs(1, 3);
        Map<Pos, String> world = new HashMap<>();
        world.put(new Pos(1, 100, 5), "minecraft:oak_log");
        world.put(new Pos(1, 101, 5), "minecraft:oak_leaves");
        world.put(new Pos(40, 100, 5), "minecraft:oak_log"); // outside the corridor
        EarthworkPlanner.World w = new EarthworkPlanner.World() {
            public String spec(int x, int y, int z) {
                return world.getOrDefault(new Pos(x, y, z), y < h.top(x, z) ? "minecraft:stone" : "minecraft:air");
            }
            public boolean isTree(String s) { return s.contains("log") || s.contains("leaves"); }
            public boolean isVegetation(String s) { return s.contains("grass"); }
            public boolean isAir(String s) { return s.equals("minecraft:air"); }
        };
        TrailDesign.Result d = design(h, 45);
        EditJournal j = new EarthworkPlanner().plan(d, EarthworkPlanner.Mode.GRADE, w);
        assertTrue(j.edits.stream().anyMatch(e -> e.x() == 1 && e.y() == 100 && e.z() == 5));
        assertTrue(j.edits.stream().noneMatch(e -> e.x() == 40), "stays inside the marked corridor");
        Map<Pos, String> before = new HashMap<>();
        for (var e : j.edits) before.put(new Pos(e.x(), e.y(), e.z()), w.spec(e.x(), e.y(), e.z()));
        while (!j.done()) { var b = j.nextBatch(16); for (var e : b) world.put(new Pos(e.x(), e.y(), e.z()), e.after()); j.advance(b.size()); }
        assertEquals("minecraft:air", world.get(new Pos(1, 100, 5)));
        j.undoing = true;
        while (!j.done()) { var b = j.nextBatch(16); for (var e : b) world.put(new Pos(e.x(), e.y(), e.z()), e.after()); j.advance(b.size()); }
        for (var en : before.entrySet()) assertEquals(en.getValue(), w.spec(en.getKey().x(), en.getKey().y(), en.getKey().z()));
    }
}
