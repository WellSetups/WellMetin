package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.api.ItemReference;
import com.wellsetups.wellmetin.util.Checks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class YamlReader {
    private YamlReader() { }

    static ConfigurationSection section(ConfigurationSection source, String path) {
        ConfigurationSection section = source.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException(path + " must be a YAML section");
        }
        return section;
    }

    static List<ConfigurationSection> list(ConfigurationSection source, String path) {
        if (!source.contains(path)) {
            return List.of();
        }
        List<?> raw = source.getList(path);
        if (raw == null) {
            throw new IllegalArgumentException(path + " must be a YAML list");
        }
        List<ConfigurationSection> result = new ArrayList<>();
        for (Object element : raw) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(path + " entries must be YAML sections");
            }
            YamlConfiguration temporary = new YamlConfiguration();
            result.add(temporary.createSection("entry", map));
        }
        return result;
    }

    static double number(ConfigurationSection source, String path, double fallback, double min, double max) {
        Object raw = source.get(path, fallback);
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be numeric");
        }
        return Checks.range(path, number.doubleValue(), min, max);
    }

    static int integer(ConfigurationSection source, String path, int fallback, int min, int max) {
        double value = number(source, path, fallback, min, max);
        if (value != Math.rint(value)) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        return (int) value;
    }

    static boolean bool(ConfigurationSection source, String path, boolean fallback) {
        Object raw = source.get(path, fallback);
        if (!(raw instanceof Boolean value)) {
            throw new IllegalArgumentException(path + " must be true or false");
        }
        return value;
    }

    static String required(ConfigurationSection source, String path) {
        return Checks.text(path, source.getString(path));
    }

    static ItemReference item(ConfigurationSection source) {
        String provider = source.getString("provider", "VANILLA");
        String id = required(source, "id");
        if (provider.equalsIgnoreCase("VANILLA")) {
            Material material = Material.matchMaterial(id);
            if (material == null || !material.isItem() || material.isAir()) {
                throw new IllegalArgumentException("item.id is not a usable vanilla item: " + id);
            }
        }
        return new ItemReference(provider, id, integer(source, "custom-model-data", 0, 0, Integer.MAX_VALUE));
    }
}
