package com.wellsetups.wellmetin.damage;

import com.wellsetups.wellmetin.api.event.MetinDamageEvent;
import com.wellsetups.wellmetin.api.event.MetinLifecycleEvent;
import com.wellsetups.wellmetin.api.event.MetinPreDamageEvent;
import com.wellsetups.wellmetin.config.MetinDefinition;
import com.wellsetups.wellmetin.loot.RewardPlanner;
import com.wellsetups.wellmetin.metin.ActiveMetin;
import com.wellsetups.wellmetin.metin.EncounterActions;
import com.wellsetups.wellmetin.metin.EncounterPersistence;
import com.wellsetups.wellmetin.metin.MetinLifecycle;
import com.wellsetups.wellmetin.metin.Thresholds;
import com.wellsetups.wellmetin.persistence.RewardDelivery;
import com.wellsetups.wellmetin.platform.Scheduler;
import com.wellsetups.wellmetin.platform.Locations;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public final class EncounterCombat {
    private final Scheduler scheduler;
    private final DamagePipeline pipeline;
    private final WeaponRules weapons;
    private final EncounterActions actions;
    private final RewardPlanner rewards;
    private final EncounterPersistence persistence;
    private final MetinLifecycle lifecycle;
    private final Consumer<String> errors;

    public EncounterCombat(Scheduler scheduler, DamagePipeline pipeline, WeaponRules weapons,
                           EncounterActions actions, RewardPlanner rewards, EncounterPersistence persistence,
                           MetinLifecycle lifecycle, Consumer<String> errors) {
        this.scheduler = scheduler;
        this.pipeline = pipeline;
        this.weapons = weapons;
        this.actions = actions;
        this.rewards = rewards;
        this.persistence = persistence;
        this.lifecycle = lifecycle;
        this.errors = errors;
    }

    public void hit(ActiveMetin metin, Player player, double effectiveDamage) {
        if (!valid(metin, player) || !Double.isFinite(effectiveDamage) || effectiveDamage <= 0) {
            return;
        }
        metin.processing(true);
        try {
            if (!weapons.allows(metin.definition().combat(), player.getInventory().getItemInMainHand())) {
                return;
            }
            if (!metin.acceptAttack(player.getUniqueId(), metin.carrier().getTicksLived())) {
                return;
            }
            DamageContext context = new DamageContext(player.getUniqueId(), metin.id(), metin.definition().id(),
                    player.getInventory().getItemInMainHand().getType().name());
            double calculated = pipeline.calculate(context, effectiveDamage,
                    metin.configuration().settings().damageMultiplier(), metin.definition().combat().multiplier() * metin.multiplier());
            MetinPreDamageEvent event = new MetinPreDamageEvent(metin.view(), player.getUniqueId(), calculated);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled() && event.getDamage() > 0) {
                apply(metin, player, event.getDamage());
            }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            if (!metin.failed()) {
                metin.fail();
                errors.accept("Combat frozen for " + metin.id() + ": " + failure.getMessage());
            }
        } finally {
            metin.processing(false);
        }
    }

    private boolean valid(ActiveMetin metin, Player player) {
        if (!scheduler.owns(metin.carrier()) || !scheduler.owns(player)
                || !scheduler.owns(Locations.resolve(metin.view().position()))) {
            return false;
        }
        if (metin.closed() || metin.processing() || metin.locked() || metin.state().health() <= 0) {
            return false;
        }
        if (!player.isOnline() || !player.isValid()
                || metin.view().position().distanceSquared(Locations.snapshot(metin.carrier().getLocation())) > 0.01) {
            return false;
        }
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return false;
        }
        String permission = metin.definition().rules().permission();
        return permission.isEmpty() || player.hasPermission(permission);
    }

    private void apply(ActiveMetin metin, Player player, double damage) {
        double before = metin.state().percentage();
        double applied = metin.state().damage(player.getUniqueId(), damage);
        List<RewardDelivery> deliveries = checkpoints(metin, before);
        for (MetinDefinition.Phase phase : Thresholds.crossed(metin.definition().phases(), before, metin.state().percentage())) {
            if (!phase.once() || metin.state().mark("phase/" + phase.id())) {
                deliveries.addAll(actions.loot(metin, phase.actions(), "phase/" + phase.id()));
                actions.run(metin, phase.actions());
                Bukkit.getPluginManager().callEvent(new MetinLifecycleEvent(MetinLifecycleEvent.Kind.PHASE,
                        metin.view(), phase.id(), metin.carrier().getUniqueId()));
            }
        }
        metin.refresh();
        Bukkit.getPluginManager().callEvent(new MetinDamageEvent(metin.view(), player.getUniqueId(), applied));
        if (metin.state().health() <= 0) {
            destructionRewards(metin, deliveries);
            lifecycle.destroy(metin, deliveries);
        } else {
            persistence.save(metin, true, deliveries);
            updateDisplay(metin);
        }
    }

    private List<RewardDelivery> checkpoints(ActiveMetin metin, double before) {
        List<RewardDelivery> deliveries = new ArrayList<>();
        for (MetinDefinition.Checkpoint checkpoint : Thresholds.crossed(metin.definition().rewards().checkpoints(),
                before, metin.state().percentage())) {
            if (metin.state().mark("checkpoint/" + checkpoint.id())) {
                deliveries.addAll(rewards.plan(metin, "checkpoint/" + checkpoint.id(), checkpoint.table()));
            }
        }
        return deliveries;
    }

    private void destructionRewards(ActiveMetin metin, List<RewardDelivery> deliveries) {
        String table = metin.definition().rewards().destructionTable();
        if (!table.isEmpty() && metin.state().mark("destruction")) {
            deliveries.addAll(rewards.plan(metin, "destruction", table));
        }
        deliveries.addAll(actions.loot(metin, metin.definition().onDestroy(), "on-destroy"));
    }

    private void updateDisplay(ActiveMetin metin) {
        if (!metin.definition().display().enabled() || metin.displayUpdate() != null) {
            return;
        }
        metin.displayUpdate(scheduler.entity(metin.carrier(), () -> {
            metin.displayUpdate(null);
            if (!metin.closed()) {
                metin.visual().update(metin.view());
            }
        }, () -> { }, metin.definition().display().updateTicks(), 0));
    }
}
