package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.lang.Messages;
import com.wellsetups.wellmetin.loot.LootTable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Performs disk work off-thread. No world, player or entity API calls. */
public final class ConfigurationLoader {
    public record Result(Configuration configuration, List<String> warnings) {
        public Result { warnings = List.copyOf(warnings); }
    }

    private final Path directory;

    public ConfigurationLoader(Path directory) { this.directory = directory; }

    public Result load(boolean strict) throws IOException {
        List<String> errors = new ArrayList<>();
        SettingsParser parser = new SettingsParser();
        Settings settings = parser.settings(yaml(directory.resolve("config.yml")),
                yaml(directory.resolve("storage.yml")), yaml(directory.resolve("spawns.yml")));
        CommandSettings commands = parser.commands(yaml(directory.resolve("commands.yml")));
        Messages messages = messages(settings.language());
        Map<String, LootTable> loot = directory("loot", new LootParser()::parse, LootTable::id, errors);
        Map<String, WaveDefinition> waves = directory("mobs", new WaveParser()::parse, WaveDefinition::id, errors);
        Map<String, MetinDefinition> metins = directory("metins", new MetinParser()::parse, MetinDefinition::id, errors);
        ReferenceValidator.validate(metins, waves, loot, errors);
        for (SpawnRule spawn : settings.spawns()) {
            MetinDefinition definition = metins.get(spawn.definition());
            if (definition == null || !definition.spawn().modes().contains(spawn.mode())) {
                throw new IllegalArgumentException("spawn " + spawn.id() + " references an unavailable Metin or spawn mode");
            }
        }
        for (CommandSettings.Command command : commands.commands().values()) {
            if (!messages.contains(command.descriptionKey()) || !messages.contains(command.usageKey())) {
                throw new IllegalArgumentException("command " + command.id() + " references a missing language key");
            }
        }
        if ((strict && !errors.isEmpty()) || metins.isEmpty()) {
            throw new IllegalArgumentException("configuration rejected: " + String.join("; ", errors));
        }
        return new Result(new Configuration(settings, commands, messages, metins, loot, waves), errors);
    }

    private Messages messages(String language) throws IOException {
        Map<String, String> messages = new HashMap<>(DefaultResources.english().values());
        messages.putAll(strings(yaml(directory.resolve("lang/en_US.yml"))));
        if (!language.equals("en_US")) {
            messages.putAll(strings(yaml(directory.resolve("lang/" + language + ".yml"))));
        }
        return new Messages(messages);
    }

    private Map<String, String> strings(YamlConfiguration yaml) {
        Map<String, String> messages = new HashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                messages.put(key, yaml.getString(key));
            }
        }
        return messages;
    }

    private <T> Map<String, T> directory(String name, Function<YamlConfiguration, T> parse,
                                        Function<T, String> id, List<String> errors) throws IOException {
        Map<String, T> result = new HashMap<>();
        try (var files = Files.list(directory.resolve(name))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).sorted().toList()) {
                try {
                    T entry = parse.apply(yaml(file));
                    if (result.putIfAbsent(id.apply(entry), entry) != null) {
                        throw new IllegalArgumentException("duplicate ID " + id.apply(entry));
                    }
                } catch (IllegalArgumentException | IOException failure) {
                    errors.add(file.getFileName() + ": " + failure.getMessage());
                }
            }
        }
        return result;
    }

    static YamlConfiguration yaml(Path file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var reader = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            yaml.load(reader);
        } catch (InvalidConfigurationException failure) {
            throw new IOException(file.getFileName() + ": " + failure.getMessage(), failure);
        }
        return yaml;
    }
}
