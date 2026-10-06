package com.wellsetups.wellmetin.persistence;

import com.wellsetups.wellmetin.loot.LootTable;
import java.util.UUID;

/** Durable immutable reward intent. Only trusted configuration text can become a command. */
public record RewardDelivery(String id, UUID player, UUID instance, String metin,
                             LootTable.Entry entry, int amount, double contribution) {
    public RewardDelivery {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(instance, "instance");
        java.util.Objects.requireNonNull(entry, "entry");
        if (amount < 1 || !Double.isFinite(contribution) || contribution < 0 || contribution > 100) {
            throw new IllegalArgumentException("invalid reward amount or contribution");
        }
    }
}
