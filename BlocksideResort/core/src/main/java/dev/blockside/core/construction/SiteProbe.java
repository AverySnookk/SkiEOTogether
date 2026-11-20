package dev.blockside.core.construction;

/** Read-only view of the world for placement validation. */
public interface SiteProbe {
    enum Kind {
        AIR, NATURAL, TERRAIN, FLUID, BUILT;
        public boolean solidGround() { return this == TERRAIN || this == BUILT; }
    }
    Kind probe(int x, int y, int z);
}
