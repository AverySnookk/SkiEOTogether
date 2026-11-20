package dev.blockside.core;

public record Pos(int x, int y, int z) {
    public Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    public static long columnKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    public static int keyX(long k) { return (int) (k >> 32); }
    public static int keyZ(long k) { return (int) k; }
    @Override public String toString() { return x + " " + y + " " + z; }
}
