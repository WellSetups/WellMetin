package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.loot.LootTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Particle;

final class ReferenceValidator {
    private ReferenceValidator() { }

    static void validate(Map<String, MetinDefinition> metins, Map<String, WaveDefinition> waves,
                         Map<String, LootTable> loot, List<String> errors) {
        waves.values().removeIf(wave -> {
            boolean invalid = wave.mobs().stream().anyMatch(mob -> !mob.loot().isEmpty() && !loot.containsKey(mob.loot()));
            if (invalid) {
                errors.add("wave " + wave.id() + ": unknown loot table");
            }
            return invalid;
        });
        metins.values().removeIf(metin -> {
            try {
                for (MetinDefinition.Checkpoint checkpoint : metin.rewards().checkpoints()) {
                    require(loot, checkpoint.table(), "checkpoint loot");
                }
                if (!metin.rewards().destructionTable().isEmpty()) {
                    require(loot, metin.rewards().destructionTable(), "destruction loot");
                }
                List<MetinDefinition.Action> actions = new ArrayList<>(metin.onSpawn());
                actions.addAll(metin.onDestroy());
                if (metin.onDestroy().stream().anyMatch(action -> action.delayTicks() > 0)) {
                    throw new IllegalArgumentException("on-destroy actions cannot be delayed because the carrier is removed");
                }
                metin.phases().forEach(phase -> actions.addAll(phase.actions()));
                for (MetinDefinition.Action action : actions) {
                    validateAction(action, waves, loot);
                }
                return false;
            } catch (IllegalArgumentException failure) {
                errors.add("Metin '" + metin.id() + "': " + failure.getMessage());
                return true;
            }
        });
    }

    private static void validateAction(MetinDefinition.Action action, Map<String, WaveDefinition> waves,
                                       Map<String, LootTable> loot) {
        if (action.type() == MetinDefinition.ActionType.LOOT && action.delayTicks() > 0) {
            throw new IllegalArgumentException("LOOT actions cannot be delayed; rewards commit with the health checkpoint");
        }
        if (action.type() == MetinDefinition.ActionType.SOUND && action.amount() > 16) {
            throw new IllegalArgumentException("sound volume must not exceed 16");
        }
        switch (action.type()) {
            case WAVE -> require(waves, action.value(), "wave");
            case LOOT -> require(loot, action.value(), "loot");
            case PARTICLE -> {
                Particle particle = Particle.valueOf(action.value());
                if (particle.getDataType() != Void.class || action.amount() > 1000) {
                    throw new IllegalArgumentException("particle must need no data and have amount <= 1000");
                }
            }
            case VISUAL -> {
                if (org.bukkit.Material.matchMaterial(action.value()) == null) {
                    throw new IllegalArgumentException("VISUAL action requires a vanilla item material");
                }
            }
            case CONSOLE_COMMAND, BROADCAST, NEARBY_MESSAGE, SOUND -> {
                if (action.value().isBlank() || action.value().contains("\n") || action.value().contains("\r")) {
                    throw new IllegalArgumentException("action value must be nonempty and on one line");
                }
            }
            default -> { }
        }
    }

    private static void require(Map<String, ?> values, String id, String kind) {
        if (!values.containsKey(id)) {
            throw new IllegalArgumentException("unknown " + kind + " '" + id + "'");
        }
    }
}
