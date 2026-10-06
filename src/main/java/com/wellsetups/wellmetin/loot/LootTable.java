package com.wellsetups.wellmetin.loot;

import com.wellsetups.wellmetin.api.ItemReference;
import com.wellsetups.wellmetin.util.Checks;
import java.util.List;

public record LootTable(String id, Mode mode, int rolls, List<Entry> entries) {
    public enum Mode { INDEPENDENT, WEIGHTED }
    public enum Kind { ITEM, COIN, CONSOLE_COMMAND, PLAYER_COMMAND, MESSAGE, SOUND }

    public record Entry(String id, Kind kind, double chance, double weight, int minimum, int maximum,
                        ItemReference item, String value, String permission, double minimumContribution) {
        public Entry {
            Checks.id("loot entry ID", id);
            Checks.range("chance", chance, 0, 100);
            Checks.range("weight", weight, 0.000001, 1e9);
            Checks.range("minimum", minimum, 1, Integer.MAX_VALUE);
            Checks.range("maximum", maximum, minimum, Integer.MAX_VALUE);
            Checks.range("minimum-contribution-percent", minimumContribution, 0, 100);
            Checks.permission(permission);
            if (kind == Kind.ITEM && item == null) {
                throw new IllegalArgumentException("item is required for ITEM rewards");
            }
            if (kind != Kind.ITEM && kind != Kind.COIN) {
                Checks.text("reward value", value);
            }
        }
    }

    public LootTable {
        Checks.id("loot table ID", id);
        Checks.range("rolls", rolls, 1, 100);
        entries = List.copyOf(entries);
        if (entries.isEmpty() || entries.stream().map(Entry::id).distinct().count() != entries.size()) {
            throw new IllegalArgumentException("loot entries must be nonempty and have unique IDs");
        }
    }
}
