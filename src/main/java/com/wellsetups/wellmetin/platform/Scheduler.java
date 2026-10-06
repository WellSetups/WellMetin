package com.wellsetups.wellmetin.platform;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/** All delays are ticks, minimum one. A zero period means a single execution. */
public interface Scheduler extends AutoCloseable {
    Task global(Runnable action, long delay, long period);
    Task region(Location location, Runnable action, long delay, long period);
    Task entity(Entity entity, Runnable action, Runnable retired, long delay, long period);
    Task async(Runnable action);
    boolean owns(Entity entity);
    boolean owns(Location location);
    String platform();
    @Override void close();
}
