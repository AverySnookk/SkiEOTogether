package dev.blockside.core.construction;

import dev.blockside.core.catalog.Stage;

/**
 * One block edit of a job, in world coordinates. spec is a block spec ("minecraft:stone_bricks",
 * "minecraft:oak_stairs[facing=north]"), or one of the specials:
 * "@clear" (remove natural blocks / cut one layer of terrain), "@anchor" (the job block),
 * "?spec" (place only if the spot is air or replaceable — used for foundation support fill).
 * rotation (0..3 clockwise quarter turns) applies to directional block states.
 */
public record Placement(int x, int y, int z, String spec, Stage stage, int rotation) {}
