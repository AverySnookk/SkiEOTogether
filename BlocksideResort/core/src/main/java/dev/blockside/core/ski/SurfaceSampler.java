package dev.blockside.core.ski;

import dev.blockside.core.snow.SurfaceKind;

/** Column-surface lookup implemented by the world (client: rendered blocks; server: snowpack). */
public interface SurfaceSampler {
    /** Top of the skiable surface in column (x,z) closest to nearY, or NaN if none within reach. */
    double columnTop(int x, int z, double nearY);
    SurfaceKind kind(int x, int z, double nearY);
}
