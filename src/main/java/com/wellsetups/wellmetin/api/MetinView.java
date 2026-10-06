package com.wellsetups.wellmetin.api;

import java.util.UUID;

/** Snapshot, never a live mutable entity. Health is the latest accepted in-memory value. */
public record MetinView(UUID id, String definitionId, String name, Position position,
                        double health, double maxHealth, boolean locked, UUID owner) {
    public double healthPercent() { return 100 * health / maxHealth; }
}
