package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.api.Position;
import com.wellsetups.wellmetin.util.Checks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

final class SettingsParser {
    static final List<String> COMMAND_IDS = List.of("help", "reload", "spawn", "remove", "list", "info",
            "coin", "givecoin", "removecoin", "setcoin", "debug");

    Settings settings(ConfigurationSection yaml, ConfigurationSection storage, ConfigurationSection spawns) {
        String language = YamlReader.required(yaml, "language");
        if (!language.matches("[a-zA-Z_]{2,16}")) {
            throw new IllegalArgumentException("language must be a filename without a path or extension");
        }
        return new Settings(language, YamlReader.number(yaml, "damage-multiplier", 1, 0, 1e6),
                YamlReader.bool(yaml, "currency.enabled", true),
                YamlReader.bool(storage, "persistent-metins", true),
                YamlReader.integer(yaml, "maintenance-ticks", 40, 10, 1200),
                YamlReader.integer(yaml, "maximum-active", 500, 1, 10000),
                YamlReader.integer(storage, "queue-capacity", 4096, 64, 65536),
                YamlReader.integer(storage, "reward-batch", 32, 1, 128),
                YamlReader.bool(yaml, "debug", false), spawns(spawns));
    }

    private List<SpawnRule> spawns(ConfigurationSection yaml) {
        List<SpawnRule> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ConfigurationSection entry : YamlReader.list(yaml, "spawns")) {
            String id = Checks.id("spawn.id", entry.getString("id"));
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate spawn ID " + id);
            }
            MetinDefinition.SpawnMode mode = Checks.enumeration(MetinDefinition.SpawnMode.class,
                    "mode", entry.getString("mode", "STATIC"));
            if (mode == MetinDefinition.SpawnMode.COMMAND_ONLY) {
                throw new IllegalArgumentException("spawns.yml accepts STATIC or RANDOM_REGION");
            }
            Position position = new Position(YamlReader.required(entry, "world"),
                    YamlReader.number(entry, "x", 0, -30_000_000, 30_000_000),
                    YamlReader.number(entry, "y", 64, -2048, 2048),
                    YamlReader.number(entry, "z", 0, -30_000_000, 30_000_000),
                    (float) YamlReader.number(entry, "yaw", 0, -360, 360));
            int minimumY = YamlReader.integer(entry, "minimum-y", -64, -2048, 2048);
            double minimumDistance = YamlReader.number(entry, "minimum-player-distance", 0, 0, 10000);
            result.add(new SpawnRule(id, YamlReader.required(entry, "metin"), mode, position,
                    YamlReader.integer(entry, "radius", 64, 1, 10000), minimumY,
                    YamlReader.integer(entry, "maximum-y", 320, minimumY, 2048),
                    YamlReader.integer(entry, "attempts", 8, 1, 64), minimumDistance,
                    YamlReader.number(entry, "maximum-player-distance", 256, minimumDistance, 10000)));
        }
        return result;
    }

    CommandSettings commands(ConfigurationSection yaml) {
        Map<String, CommandSettings.Command> commands = new HashMap<>();
        Set<String> labels = new HashSet<>();
        for (String id : COMMAND_IDS) {
            ConfigurationSection command = YamlReader.section(yaml, "commands." + id);
            String name = Checks.id("commands." + id + ".name", command.getString("name", id));
            List<String> aliases = command.getStringList("aliases");
            List<String> all = new ArrayList<>(aliases);
            all.add(name);
            for (String label : all) {
                if (!labels.add(Checks.id("command alias", label))) {
                    throw new IllegalArgumentException("duplicate command label " + label);
                }
            }
            commands.put(id, new CommandSettings.Command(id, name, aliases,
                    Checks.permission(command.getString("permission", "wellmetin.command." + id)),
                    YamlReader.bool(command, "enabled", true), command.getString("description-key", "help." + id),
                    command.getString("usage-key", "usage." + id)));
        }
        return new CommandSettings(commands);
    }
}
