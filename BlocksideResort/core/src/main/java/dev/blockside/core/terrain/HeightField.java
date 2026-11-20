package dev.blockside.core.terrain;

/** Terrain height query: y coordinate of the top face of the highest terrain block in the column. */
@FunctionalInterface
public interface HeightField {
    int top(int x, int z);
}
