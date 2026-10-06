package com.wellsetups.wellmetin.persistence;

import com.wellsetups.wellmetin.api.Position;
import com.wellsetups.wellmetin.metin.EncounterState;
import com.wellsetups.wellmetin.util.Checks;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record SavedMetin(UUID id, String definition, Position position, double health, double maximum,
                         Map<UUID, Double> contributions, Set<String> triggered, UUID owner, UUID lastHit,
                         String spawnId, long version, double multiplier, long immuneUntil) {
    public SavedMetin {
        java.util.Objects.requireNonNull(id, "id");
        Checks.id("definition", definition);
        java.util.Objects.requireNonNull(position, "position");
        new EncounterState(maximum, health, contributions, triggered, lastHit);
        contributions = Map.copyOf(contributions);
        triggered = Set.copyOf(triggered);
        Checks.range("multiplier", multiplier, 0, 1e6);
        if (version < 0 || immuneUntil < 0 || spawnId == null) {
            throw new IllegalArgumentException("invalid saved version, immunity or spawn ID");
        }
    }
}
