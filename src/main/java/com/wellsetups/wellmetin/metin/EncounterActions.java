package com.wellsetups.wellmetin.metin;

import com.wellsetups.wellmetin.api.ItemReference;
import com.wellsetups.wellmetin.config.MetinDefinition;
import com.wellsetups.wellmetin.lang.Messages;
import com.wellsetups.wellmetin.loot.RewardPlanner;
import com.wellsetups.wellmetin.mob.MobWaves;
import com.wellsetups.wellmetin.persistence.RewardDelivery;
import com.wellsetups.wellmetin.platform.PlayerDirectory;
import com.wellsetups.wellmetin.platform.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Particle;

public final class EncounterActions {
    private final Scheduler scheduler;
    private final PlayerDirectory players;
    private final MobWaves waves;
    private final RewardPlanner planner;
    private final Consumer<String> errors;

    public EncounterActions(Scheduler scheduler, PlayerDirectory players, MobWaves waves,
                            RewardPlanner planner, Consumer<String> errors) {
        this.scheduler = scheduler;
        this.players = players;
        this.waves = waves;
        this.planner = planner;
        this.errors = errors;
    }

    public List<RewardDelivery> loot(ActiveMetin metin, List<MetinDefinition.Action> actions, String key) {
        List<RewardDelivery> result = new ArrayList<>();
        for (int index = 0; index < actions.size(); index++) {
            MetinDefinition.Action action = actions.get(index);
            if (action.type() == MetinDefinition.ActionType.LOOT) {
                result.addAll(planner.plan(metin, key + "/" + index, action.value()));
            }
        }
        return List.copyOf(result);
    }

    public void run(ActiveMetin metin, List<MetinDefinition.Action> actions) {
        for (MetinDefinition.Action action : actions) {
            if (action.type() == MetinDefinition.ActionType.LOOT) {
                continue;
            }
            if (action.delayTicks() > 0) {
                scheduler.entity(metin.carrier(), () -> {
                    if (!metin.closed()) {
                        execute(metin, action);
                    }
                }, () -> { }, action.delayTicks(), 0);
            } else {
                execute(metin, action);
            }
        }
    }

    private void execute(ActiveMetin metin, MetinDefinition.Action action) {
        try {
            apply(metin, action);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            errors.accept("Action " + action.type() + " on " + metin.id() + " failed: " + failure.getMessage());
        }
    }

    private void apply(ActiveMetin metin, MetinDefinition.Action action) {
        var location = metin.carrier().getLocation();
        String message = Messages.format(action.value(), Map.of("metin", metin.definition().name(),
                "level", metin.definition().level(), "instance", metin.id()));
        switch (action.type()) {
            case WAVE -> {
                if (metin.state().health() > 0) {
                    waves.spawn(metin, action.value(), action.lock());
                }
            }
            case IMMUNITY -> metin.immunity(action.durationTicks());
            case DAMAGE_MULTIPLIER -> metin.multiplier(action.amount());
            case VISUAL -> metin.visual().change(new ItemReference("VANILLA", action.value(), 0));
            case SOUND -> location.getWorld().playSound(location, action.value(), (float) action.amount(), 1);
            case PARTICLE -> location.getWorld().spawnParticle(Particle.valueOf(action.value()), location,
                    (int) action.amount(), 0.6, 0.6, 0.6, 0);
            case BROADCAST -> players.ids().forEach(id -> players.execute(id, player -> player.sendMessage(message), () -> { }));
            case NEARBY_MESSAGE -> players.nearby(metin.view().position(), action.radius(), player -> player.sendMessage(message));
            case CONSOLE_COMMAND -> {
                String command = action.value().replace("<instance>", metin.id().toString())
                        .replace("<metin_id>", metin.definition().id());
                scheduler.global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command), 1, 0);
            }
            case LOOT -> throw new IllegalStateException("loot must be included in the encounter transaction");
        }
        metin.refresh();
    }
}
