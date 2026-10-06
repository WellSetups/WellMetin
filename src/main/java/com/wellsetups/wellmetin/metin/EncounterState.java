package com.wellsetups.wellmetin.metin;

import com.wellsetups.wellmetin.util.Checks;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Mutable only on the stationary carrier's region; persistence receives immutable copies. */
public final class EncounterState {
    private final double maximum;
    private double health;
    private final Map<UUID, Double> contributions;
    private final Set<String> triggered;
    private UUID lastHit;

    public EncounterState(double maximum) {
        this(maximum, maximum, Map.of(), Set.of(), null);
    }

    public EncounterState(double maximum, double health, Map<UUID, Double> contributions,
                          Set<String> triggered, UUID lastHit) {
        this.maximum = Checks.range("maximum", maximum, 1, 1e15);
        this.health = Checks.range("health", health, 0, maximum);
        this.contributions = new HashMap<>(contributions);
        contributions.values().forEach(value -> Checks.range("contribution", value, 0, maximum));
        double total = contributions.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!Double.isFinite(total) || total > maximum - health + Math.max(1e-6, maximum * 1e-12)) {
            throw new IllegalArgumentException("contributions exceed the encounter's missing health");
        }
        this.triggered = new HashSet<>(triggered);
        this.lastHit = lastHit;
    }

    public double damage(UUID player, double requested) {
        Checks.range("damage", requested, 0, Double.MAX_VALUE);
        double applied = Math.min(health, requested);
        if (applied > 0) {
            health = Math.max(0, health - applied);
            contributions.merge(player, applied, Double::sum);
            lastHit = player;
        }
        return applied;
    }

    public double percentage() { return health * 100 / maximum; }
    public double health() { return health; }
    public double maximum() { return maximum; }
    public UUID lastHit() { return lastHit; }
    public boolean mark(String id) { return triggered.add(id); }
    public Map<UUID, Double> contributions() { return Map.copyOf(contributions); }
    public Set<String> triggered() { return Set.copyOf(triggered); }
}
