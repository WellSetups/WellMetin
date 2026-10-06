package com.wellsetups.wellmetin.metin;

import com.wellsetups.wellmetin.loot.RewardService;
import com.wellsetups.wellmetin.mob.MobWaves;
import com.wellsetups.wellmetin.platform.PlayerDirectory;
import com.wellsetups.wellmetin.visual.OwnedEntities;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;

public final class LifecycleListener implements Listener {
    private final MetinRegistry registry;
    private final MetinLifecycle lifecycle;
    private final OwnedEntities tags;
    private final MobWaves waves;
    private final PlayerDirectory players;
    private final RewardService rewards;

    public LifecycleListener(MetinRegistry registry, MetinLifecycle lifecycle, OwnedEntities tags,
                             MobWaves waves, PlayerDirectory players, RewardService rewards) {
        this.registry = registry;
        this.lifecycle = lifecycle;
        this.tags = tags;
        this.waves = waves;
        this.players = players;
        this.rewards = rewards;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void loaded(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (tags.owned(entity) && registry.byEntity(entity.getUniqueId()) == null && waves.get(entity.getUniqueId()) == null) {
                entity.remove();
            }
        }
        lifecycle.loaded(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void unloaded(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            ActiveMetin metin = registry.byEntity(entity.getUniqueId());
            if (metin != null) {
                lifecycle.suspend(metin);
            }
            if (waves.detach(entity.getUniqueId()) != null) {
                entity.remove();
            }
        }
    }

    @EventHandler public void worldLoaded(WorldLoadEvent event) { lifecycle.retryRestoration(); }
    @EventHandler public void join(PlayerJoinEvent event) {
        players.join(event.getPlayer());
        rewards.pump(event.getPlayer().getUniqueId());
    }
    @EventHandler public void quit(PlayerQuitEvent event) { players.quit(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void burn(EntityCombustEvent event) {
        if (tags.carrier(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(EntityTeleportEvent event) {
        if (tags.owned(event.getEntity()) && !tags.guardian(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent event) {
        if (tags.carrier(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent event) {
        if (tags.carrier(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interactAt(PlayerInteractAtEntityEvent event) {
        if (tags.carrier(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void leash(PlayerLeashEntityEvent event) {
        if (tags.carrier(event.getEntity())) {
            event.setCancelled(true);
        }
    }

}
