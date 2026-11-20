package dev.blockside.core;

/** Inclusive integer box. */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static Box of(int x0, int y0, int z0, int x1, int y1, int z1) {
        return new Box(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
    }
    public boolean intersects(Box o) {
        return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
    }
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
    public boolean containsXZ(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    public Box grow(int h, int v) { return new Box(minX - h, minY - v, minZ - h, maxX + h, maxY + v, maxZ + h); }
    public Box union(Box o) {
        return new Box(Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
                Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ));
    }
    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }
    @Override public String toString() { return "[" + minX + "," + minY + "," + minZ + " → " + maxX + "," + maxY + "," + maxZ + "]"; }
}
