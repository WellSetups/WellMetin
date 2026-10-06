package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.lang.Messages;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DefaultResources {
    public static final List<String> FILES = List.of("config.yml", "commands.yml", "storage.yml", "spawns.yml",
            "lang/en_US.yml", "metins/ancient_metin.yml", "loot/common_checkpoint.yml", "loot/destruction.yml",
            "mobs/guardians.yml", "mobs/elite_guardians.yml", "mobs/final_defense.yml");

    private DefaultResources() { }

    public static void install(Path directory) throws IOException {
        for (String file : FILES) {
            Path target = directory.resolve(file);
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                try (InputStream resource = resource(file)) {
                    Files.copy(resource, target);
                }
            }
        }
    }

    public static Messages english() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var reader = new InputStreamReader(resource("lang/en_US.yml"), StandardCharsets.UTF_8)) {
            yaml.load(reader);
            Map<String, String> messages = new HashMap<>();
            for (String key : yaml.getKeys(true)) {
                if (yaml.isString(key)) {
                    messages.put(key, yaml.getString(key));
                }
            }
            return new Messages(messages);
        } catch (IOException | InvalidConfigurationException failure) {
            throw new IllegalStateException("Bundled English language resource is missing or invalid", failure);
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = DefaultResources.class.getResourceAsStream("/" + path);
        if (stream == null) {
            throw new IllegalStateException("Missing bundled resource " + path);
        }
        return stream;
    }
}
