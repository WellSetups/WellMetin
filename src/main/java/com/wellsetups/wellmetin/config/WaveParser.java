package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.api.ItemReference;
import com.wellsetups.wellmetin.util.Checks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffectType;

final class WaveParser {
    WaveDefinition parse(ConfigurationSection yaml) {
        List<WaveDefinition.Mob> mobs = new ArrayList<>();
        for (ConfigurationSection entry : YamlReader.list(yaml, "mobs")) {
            mobs.add(mob(entry));
        }
        if (mobs.isEmpty() || mobs.stream().mapToInt(WaveDefinition.Mob::amount).sum() > 100) {
            throw new IllegalArgumentException("wave must contain between 1 and 100 mobs");
        }
        return new WaveDefinition(Checks.id("id", yaml.getString("id")), mobs,
                YamlReader.integer(yaml, "timeout-seconds", 300, 1, 3600));
    }

    private WaveDefinition.Mob mob(ConfigurationSection entry) {
        EntityType type = Checks.enumeration(EntityType.class, "type", entry.getString("type"));
        if (!type.isAlive() || !type.isSpawnable() || type == EntityType.ARMOR_STAND) {
            throw new IllegalArgumentException("type must be a spawnable living mob: " + type);
        }
        double minimum = YamlReader.number(entry, "minimum-radius", 2, 0, 64);
        return new WaveDefinition.Mob(type.name(), YamlReader.integer(entry, "amount", 3, 1, 100), minimum,
                YamlReader.number(entry, "maximum-radius", 6, minimum, 64), entry.getString("name", ""),
                YamlReader.number(entry, "health-multiplier", 1, 0.01, 1000),
                YamlReader.number(entry, "damage-multiplier", 1, 0, 1000),
                YamlReader.bool(entry, "glowing", false), YamlReader.bool(entry, "ai", true),
                YamlReader.bool(entry, "vanilla-drops", false), YamlReader.number(entry, "chance", 100, 0, 100),
                YamlReader.integer(entry, "level", 1, 0, 1_000_000), entry.getString("loot", ""),
                equipment(entry), effects(entry));
    }

    private Map<String, ItemReference> equipment(ConfigurationSection entry) {
        ConfigurationSection equipment = entry.getConfigurationSection("equipment");
        Map<String, ItemReference> result = new HashMap<>();
        if (equipment != null) {
            for (String slot : equipment.getKeys(false)) {
                EquipmentSlot key = Checks.enumeration(EquipmentSlot.class, "equipment slot", slot);
                result.put(key.name(), YamlReader.item(YamlReader.section(equipment, slot)));
            }
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private List<WaveDefinition.Effect> effects(ConfigurationSection entry) {
        List<WaveDefinition.Effect> effects = new ArrayList<>();
        for (ConfigurationSection effect : YamlReader.list(entry, "effects")) {
            String type = YamlReader.required(effect, "type");
            if (PotionEffectType.getByName(type) == null) {
                throw new IllegalArgumentException("unknown potion effect " + type);
            }
            effects.add(new WaveDefinition.Effect(type, YamlReader.integer(effect, "ticks", 1200, 1, 72000),
                    YamlReader.integer(effect, "amplifier", 0, 0, 10)));
        }
        return effects;
    }
}
