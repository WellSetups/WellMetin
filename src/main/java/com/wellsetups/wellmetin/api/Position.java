package com.wellsetups.wellmetin.api;

import com.wellsetups.wellmetin.util.Checks;

/** Immutable world coordinates; safe to pass between schedulers. */
public record Position(String world, double x, double y, double z, float yaw) {
    public Position {
        Checks.text("world", world);
        Checks.range("x", x, -30_000_000, 30_000_000);
        Checks.range("y", y, -2048, 2048);
        Checks.range("z", z, -30_000_000, 30_000_000);
        Checks.range("yaw", yaw, -360, 360);
    }

    public int chunkX() { return ((int) Math.floor(x)) >> 4; }
    public int chunkZ() { return ((int) Math.floor(z)) >> 4; }

    public double distanceSquared(Position other) {
        if (!world.equals(other.world)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2) + Math.pow(z - other.z, 2);
    }
}
