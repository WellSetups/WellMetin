package com.wellsetups.wellmetin.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

final class BukkitSchedulerAdapter implements Scheduler {
    private final Plugin plugin;

    BukkitSchedulerAdapter(Plugin plugin) { this.plugin = plugin; }

    @Override
    public Task global(Runnable action, long delay, long period) {
        BukkitTask task = period == 0 ? Bukkit.getScheduler().runTaskLater(plugin, action, Math.max(1, delay))
                : Bukkit.getScheduler().runTaskTimer(plugin, action, Math.max(1, delay), period);
        return task::cancel;
    }

    @Override
    public Task region(Location location, Runnable action, long delay, long period) {
        return global(action, delay, period);
    }

    @Override
    public Task entity(Entity entity, Runnable action, Runnable retired, long delay, long period) {
        Task[] scheduled = new Task[1];
        scheduled[0] = global(() -> {
            if (entity.isValid()) {
                action.run();
            } else {
                scheduled[0].cancel();
                retired.run();
            }
        }, delay, period);
        return scheduled[0];
    }

    @Override
    public Task async(Runnable action) {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, action)::cancel;
    }

    @Override public boolean owns(Entity entity) { return Bukkit.isPrimaryThread(); }
    @Override public boolean owns(Location location) { return Bukkit.isPrimaryThread(); }
    @Override public String platform() { return Bukkit.getName(); }
    @Override public void close() { Bukkit.getScheduler().cancelTasks(plugin); }
}
