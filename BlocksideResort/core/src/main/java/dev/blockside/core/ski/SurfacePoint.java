package dev.blockside.core.ski;

import dev.blockside.core.snow.SurfaceKind;

public record SurfacePoint(double height, Vec3 normal, SurfaceKind kind) {}
