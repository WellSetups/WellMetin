package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.loot.LootTable;
import com.wellsetups.wellmetin.util.Checks;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

final class LootParser {
    LootTable parse(ConfigurationSection yaml) {
        LootTable.Mode mode = Checks.enumeration(LootTable.Mode.class, "mode", yaml.getString("mode", "INDEPENDENT"));
        List<LootTable.Entry> entries = new ArrayList<>();
        for (ConfigurationSection entry : YamlReader.list(yaml, "entries")) {
            LootTable.Kind kind = Checks.enumeration(LootTable.Kind.class, "kind", entry.getString("kind"));
            if (mode == LootTable.Mode.WEIGHTED && entry.contains("chance")) {
                throw new IllegalArgumentException("weighted entries use weight, not chance");
            }
            if (mode == LootTable.Mode.INDEPENDENT && entry.contains("weight")) {
                throw new IllegalArgumentException("independent entries use chance, not weight");
            }
            int maximum = kind == LootTable.Kind.ITEM ? 2304 : Integer.MAX_VALUE;
            entries.add(new LootTable.Entry(entry.getString("id"), kind,
                    YamlReader.number(entry, "chance", 100, 0, 100),
                    YamlReader.number(entry, "weight", 1, 0.000001, 1e9),
                    YamlReader.integer(entry, "minimum", 1, 1, maximum),
                    YamlReader.integer(entry, "maximum", 1, 1, maximum),
                    kind == LootTable.Kind.ITEM ? YamlReader.item(YamlReader.section(entry, "item")) : null,
                    entry.getString("value", ""), Checks.permission(entry.getString("permission", "")),
                    YamlReader.number(entry, "minimum-contribution-percent", 0, 0, 100)));
        }
        return new LootTable(yaml.getString("id"), mode, YamlReader.integer(yaml, "rolls", 1, 1, 100), entries);
    }
}
