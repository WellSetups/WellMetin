package com.wellsetups.wellmetin.platform;

import org.bukkit.plugin.Plugin;

public final class Schedulers {
    private Schedulers() { }

    public static Scheduler create(Plugin plugin) {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return new FoliaSchedulerAdapter(plugin);
        } catch (ClassNotFoundException absent) {
            return new BukkitSchedulerAdapter(plugin);
        }
    }
}
