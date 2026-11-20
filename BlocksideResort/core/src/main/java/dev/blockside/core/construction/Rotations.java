package dev.blockside.core.construction;

/** Clockwise quarter turns viewed from above (MC: north=-z → east=+x). */
public final class Rotations {
    private Rotations() {}
    public static int[] rotate(int dx, int dz, int rot) {
        return switch (((rot % 4) + 4) % 4) {
            case 1 -> new int[]{-dz, dx};
            case 2 -> new int[]{-dx, -dz};
            case 3 -> new int[]{dz, -dx};
            default -> new int[]{dx, dz};
        };
    }
    /** Rotation that makes a template's "forward" (-z, north) point along (dx,dz). */
    public static int facing(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 1 : 3;
        return dz > 0 ? 2 : 0;
    }
    public static String name(int rot) {
        return switch (((rot % 4) + 4) % 4) { case 1 -> "east"; case 2 -> "south"; case 3 -> "west"; default -> "north"; };
    }
}
