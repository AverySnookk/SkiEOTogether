package dev.blockside.mod.server;

import dev.blockside.core.Box;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;

public final class Particles {
    private Particles() {}

    public static void at(ServerLevel l, ParticleOptions type, double x, double y, double z) {
        l.sendParticles(type, x, y, z, 1, 0, 0, 0, 0);
    }

    /** Outline of a box's footprint at height y. */
    public static void outline(ServerLevel l, ParticleOptions type, Box b, double y) {
        for (int x = b.minX(); x <= b.maxX() + 1; x++) { at(l, type, x, y, b.minZ()); at(l, type, x, y, b.maxZ() + 1); }
        for (int z = b.minZ(); z <= b.maxZ() + 1; z++) { at(l, type, b.minX(), y, z); at(l, type, b.maxX() + 1, y, z); }
    }

    public static void line(ServerLevel l, ParticleOptions type, double[] a, double[] b, double step) {
        double len = Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
        int n = Math.max(1, (int) (len / step));
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            at(l, type, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
        }
    }
}
