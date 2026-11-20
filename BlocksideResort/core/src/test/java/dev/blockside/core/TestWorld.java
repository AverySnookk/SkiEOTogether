package dev.blockside.core;

import dev.blockside.core.catalog.Catalog;
import dev.blockside.core.construction.SiteProbe;
import dev.blockside.core.terrain.HeightField;

import java.util.HashMap;
import java.util.Map;

/** Tiny in-memory world: ground from a height function, plus edits. */
final class TestWorld implements SiteProbe, Resort.Builder {
    final HeightField ground;
    final Map<Pos, String> blocks = new HashMap<>();

    TestWorld(HeightField ground) { this.ground = ground; }
    static TestWorld flat(int top) { return new TestWorld((x, z) -> top); }

    String spec(int x, int y, int z) {
        String s = blocks.get(new Pos(x, y, z));
        if (s != null) return s;
        return y < ground.top(x, z) ? "minecraft:stone" : "minecraft:air";
    }

    @Override public Kind probe(int x, int y, int z) {
        String s = spec(x, y, z);
        if (s.equals("minecraft:air")) return Kind.AIR;
        if (s.equals("minecraft:stone") || s.equals("minecraft:dirt")) return Kind.TERRAIN;
        if (s.equals("minecraft:water")) return Kind.FLUID;
        if (s.startsWith("minecraft:oak_log")) return Kind.NATURAL;
        return Kind.BUILT;
    }

    @Override public String apply(dev.blockside.core.construction.Placement p) {
        String cur = spec(p.x(), p.y(), p.z());
        if (p.spec().equals("@clear")) { if (probe(p.x(), p.y(), p.z()) != Kind.AIR) blocks.put(new Pos(p.x(), p.y(), p.z()), "minecraft:air"); return null; }
        if (p.spec().startsWith("?")) { if (cur.equals("minecraft:air")) blocks.put(new Pos(p.x(), p.y(), p.z()), p.spec().substring(1)); return null; }
        blocks.put(new Pos(p.x(), p.y(), p.z()), p.spec());
        return null;
    }

    static ResortConfig config() { return ResortConfig.loadBuiltin(); }
    static Catalog catalog() { return Catalog.loadBuiltin(); }

    static Resort newResort() {
        Resort r = new Resort(config(), catalog());
        r.claim("alex", new Pos(0, 64, 0));
        return r;
    }
}
