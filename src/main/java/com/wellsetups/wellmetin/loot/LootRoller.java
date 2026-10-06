package com.wellsetups.wellmetin.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class LootRoller {
    public record Roll(LootTable.Entry entry, int amount, int ordinal) { }

    public List<Roll> roll(LootTable table, RandomGenerator random) {
        List<Roll> result = new ArrayList<>();
        if (table.mode() == LootTable.Mode.INDEPENDENT) {
            for (LootTable.Entry entry : table.entries()) {
                if (random.nextDouble(100) < entry.chance()) {
                    result.add(new Roll(entry, amount(entry, random), result.size()));
                }
            }
        } else {
            double total = table.entries().stream().mapToDouble(LootTable.Entry::weight).sum();
            for (int index = 0; index < table.rolls(); index++) {
                LootTable.Entry entry = weighted(table.entries(), random.nextDouble(total));
                result.add(new Roll(entry, amount(entry, random), index));
            }
        }
        return List.copyOf(result);
    }

    private LootTable.Entry weighted(List<LootTable.Entry> entries, double selection) {
        for (LootTable.Entry entry : entries) {
            selection -= entry.weight();
            if (selection < 0) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    private int amount(LootTable.Entry entry, RandomGenerator random) {
        return (int) random.nextLong(entry.minimum(), (long) entry.maximum() + 1);
    }
}
