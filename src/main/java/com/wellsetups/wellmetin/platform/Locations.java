package com.wellsetups.wellmetin.platform;

import com.wellsetups.wellmetin.api.Position;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class Locations {
    private Locations() { }

    public static Position snapshot(Location location) {
        World world = java.util.Objects.requireNonNull(location.getWorld(), "world");
        return new Position(world.getName(), location.getX(), location.getY(), location.getZ(), location.getYaw());
    }

    public static Location resolve(Position position) {
        World world = Bukkit.getWorld(position.world());
        if (world == null) {
            throw new IllegalArgumentException("world is not loaded: " + position.world());
        }
        return new Location(world, position.x(), position.y(), position.z(), position.yaw(), 0);
    }
}
