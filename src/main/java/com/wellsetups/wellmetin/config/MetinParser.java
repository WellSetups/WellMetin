package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.api.ItemReference;
import com.wellsetups.wellmetin.loot.RewardMode;
import com.wellsetups.wellmetin.util.Checks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

final class MetinParser {
    MetinDefinition parse(ConfigurationSection yaml) {
        String id = Checks.id("id", yaml.getString("id"));
        return new MetinDefinition(id, YamlReader.required(yaml, "name"), yaml.getStringList("description"),
                YamlReader.integer(yaml, "level", 1, 0, 1_000_000),
                YamlReader.number(yaml, "health", 10_000, 1, 1e15), visual(yaml), combat(yaml), display(yaml),
                rewards(yaml), phases(yaml), rules(yaml), spawn(yaml), actions(yaml, "on-spawn"),
                actions(yaml, "on-destroy"), tags(yaml));
    }

    private MetinDefinition.Visual visual(ConfigurationSection yaml) {
        ConfigurationSection visual = YamlReader.section(yaml, "visual");
        return new MetinDefinition.Visual(YamlReader.item(visual),
                (float) YamlReader.number(visual, "scale", 2, 0.1, 16),
                new MetinDefinition.Hitbox(Checks.enumeration(MetinDefinition.HitboxType.class, "hitbox.type",
                        yaml.getString("hitbox.type", "IRON_GOLEM")),
                        YamlReader.integer(yaml, "hitbox.slime-size", 3, 1, 8),
                        YamlReader.bool(yaml, "hitbox.baby", false)),
                YamlReader.number(visual, "offset-y", 0.8, -8, 8));
    }

    private MetinDefinition.Combat combat(ConfigurationSection yaml) {
        Set<String> weapons = new HashSet<>();
        for (String weapon : yaml.getStringList("combat.weapons")) {
            Material material = Material.matchMaterial(weapon);
            if (material == null) {
                throw new IllegalArgumentException("combat.weapons contains unknown material " + weapon);
            }
            weapons.add(material.name());
        }
        List<ItemReference> custom = YamlReader.list(yaml, "combat.custom-weapons").stream()
                .map(YamlReader::item).toList();
        if (weapons.isEmpty() && custom.isEmpty()) {
            throw new IllegalArgumentException("combat.weapons or combat.custom-weapons must permit a weapon");
        }
        return new MetinDefinition.Combat(YamlReader.number(yaml, "combat.multiplier", 1, 0, 1e6),
                weapons, custom, YamlReader.integer(yaml, "combat.minimum-enchantment-level", 0, 0, 255),
                YamlReader.integer(yaml, "combat.minimum-attack-interval-ticks", 10, 1, 200));
    }

    private MetinDefinition.Display display(ConfigurationSection yaml) {
        return new MetinDefinition.Display(YamlReader.bool(yaml, "display.enabled", true),
                yaml.getStringList("display.lines"),
                YamlReader.integer(yaml, "display.update-ticks", 5, 1, 1200),
                YamlReader.number(yaml, "display.offset-y", 2.6, 0, 20),
                YamlReader.integer(yaml, "display.bar-segments", 10, 1, 100),
                yaml.getString("display.filled", "&a█"), yaml.getString("display.empty", "&7░"));
    }

    private MetinDefinition.Rewards rewards(ConfigurationSection yaml) {
        List<MetinDefinition.Checkpoint> checkpoints = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ConfigurationSection entry : YamlReader.list(yaml, "reward-checkpoints")) {
            String id = Checks.id("checkpoint.id", entry.getString("id"));
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate checkpoint ID " + id);
            }
            checkpoints.add(new MetinDefinition.Checkpoint(id,
                    YamlReader.number(entry, "at-health-percent", -1, 0, 100),
                    YamlReader.required(entry, "table")));
        }
        return new MetinDefinition.Rewards(Checks.enumeration(RewardMode.class, "reward-mode",
                yaml.getString("reward-mode", "CONTRIBUTION")),
                YamlReader.number(yaml, "minimum-contribution-percent", 10, 0, 100),
                checkpoints, yaml.getString("destruction-table", ""));
    }

    private List<MetinDefinition.Phase> phases(ConfigurationSection yaml) {
        ConfigurationSection phases = yaml.getConfigurationSection("phases");
        if (phases == null) {
            if (yaml.contains("phases")) {
                throw new IllegalArgumentException("phases must be a YAML section");
            }
            return List.of();
        }
        List<MetinDefinition.Phase> result = new ArrayList<>();
        for (String id : phases.getKeys(false)) {
            ConfigurationSection phase = YamlReader.section(phases, id);
            result.add(new MetinDefinition.Phase(Checks.id("phase ID", id),
                    YamlReader.number(phase, "trigger-health-percent", -1, 0, 100),
                    YamlReader.bool(phase, "trigger-once", true), actions(phase, "actions")));
        }
        return result;
    }

    static List<MetinDefinition.Action> actions(ConfigurationSection yaml, String path) {
        List<MetinDefinition.Action> result = new ArrayList<>();
        for (ConfigurationSection entry : YamlReader.list(yaml, path)) {
            result.add(new MetinDefinition.Action(Checks.enumeration(MetinDefinition.ActionType.class,
                    path + ".type", entry.getString("type")), entry.getString("value", ""),
                    YamlReader.number(entry, "amount", 1, 0, 1e6),
                    YamlReader.integer(entry, "duration-ticks", 100, 1, 72000),
                    YamlReader.integer(entry, "delay-ticks", 0, 0, 72000),
                    YamlReader.bool(entry, "lock-metin-until-wave-dead", false),
                    YamlReader.number(entry, "radius", 32, 0, 256)));
        }
        return result;
    }

    private MetinDefinition.Rules rules(ConfigurationSection yaml) {
        return new MetinDefinition.Rules(Checks.permission(yaml.getString("permission", "wellmetin.use")),
                Set.copyOf(yaml.getStringList("worlds.whitelist")), Set.copyOf(yaml.getStringList("worlds.blacklist")),
                Set.copyOf(yaml.getStringList("biomes")));
    }

    private MetinDefinition.Spawn spawn(ConfigurationSection yaml) {
        List<String> modes = yaml.getStringList("spawn.modes");
        Set<MetinDefinition.SpawnMode> parsed = modes.isEmpty() ? Set.of(MetinDefinition.SpawnMode.COMMAND_ONLY)
                : modes.stream().map(mode -> Checks.enumeration(MetinDefinition.SpawnMode.class,
                "spawn.modes", mode)).collect(Collectors.toSet());
        return new MetinDefinition.Spawn(parsed,
                YamlReader.integer(yaml, "spawn.respawn-seconds", 300, 1, 2_592_000),
                YamlReader.integer(yaml, "spawn.maximum-active", 100, 1, 10000),
                YamlReader.bool(yaml, "spawn.safe-ground", true), YamlReader.bool(yaml, "spawn.allow-liquids", false));
    }

    private Map<String, String> tags(ConfigurationSection yaml) {
        ConfigurationSection tags = yaml.getConfigurationSection("tags");
        return tags == null ? Map.of() : tags.getValues(false).entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
    }
}
