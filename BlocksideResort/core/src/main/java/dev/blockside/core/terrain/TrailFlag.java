package dev.blockside.core.terrain;

import dev.blockside.core.Pos;

/** A spot on a corridor that needs earthwork before it can be skied smoothly. */
public record TrailFlag(Type type, Pos at, String detail) {
    public enum Type {
        /** Surface would float too far above the ground for snow to bridge. */
        GAP,
        /** Steeper than the cliff limit. */
        CLIFF,
        /** A column sticking up out of the surface (rock, stump). */
        PROTRUDING
    }
}
