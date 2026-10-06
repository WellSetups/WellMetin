package com.wellsetups.wellmetin.metin;

import com.wellsetups.wellmetin.damage.EncounterCombat;
import com.wellsetups.wellmetin.mob.MobWaves;
import com.wellsetups.wellmetin.visual.OwnedEntities;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public final class CombatListener implements Listener {
    private final MetinRegistry registry;
    private final OwnedEntities tags;
    private final EncounterCombat combat;
    private final MobWaves waves;

    public CombatListener(MetinRegistry registry, OwnedEntities tags, EncounterCombat combat, MobWaves waves) {
        this.registry = registry;
        this.tags = tags;
        this.combat = combat;
        this.waves = waves;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (tags.owned(event.getEntity()) && !tags.guardian(event.getEntity())) {
            boolean previouslyCancelled = event.isCancelled();
            double effectiveDamage = event.getFinalDamage();
            event.setCancelled(true);
            ActiveMetin metin = registry.byEntity(event.getEntity().getUniqueId());
            if (!previouslyCancelled && metin != null && tags.carrier(event.getEntity())
                    && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    && event instanceof EntityDamageByEntityEvent attack && attack.getDamager() instanceof Player player) {
                combat.hit(metin, player, effectiveDamage);
            }
            return;
        }
        if (!event.isCancelled() && event instanceof EntityDamageByEntityEvent attack) {
            outgoing(attack);
        }
    }

    private void outgoing(EntityDamageByEntityEvent event) {
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (registry.byEntity(source.getUniqueId()) != null) {
            event.setCancelled(true);
            return;
        }
        MobWaves.Guardian guardian = waves.get(source.getUniqueId());
        if (guardian != null) {
            event.setDamage(event.getDamage() * guardian.settings().damageMultiplier());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) {
        if (!tags.owned(event.getEntity())) {
            return;
        }
        MobWaves.Guardian guardian = waves.get(event.getEntity().getUniqueId());
        if (guardian == null || !guardian.settings().vanillaDrops()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        Player killer = event.getEntity().getKiller();
        waves.died(event.getEntity().getUniqueId(), killer == null ? null : killer.getUniqueId());
    }
}
