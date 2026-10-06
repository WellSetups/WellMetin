package com.wellsetups.wellmetin.loot;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class Recipients {
    private Recipients() { }

    public static double percent(double contribution, double maximum) {
        if (!Double.isFinite(contribution) || !Double.isFinite(maximum) || maximum <= 0) {
            throw new IllegalArgumentException("invalid contribution or maximum");
        }
        return Math.min(100, Math.max(0, contribution / maximum * 100));
    }

    public static Set<UUID> select(RewardMode mode, Map<UUID, Double> damage, double maximum,
                                   double minimum, UUID owner, UUID lastHit) {
        Set<UUID> eligible = damage.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && percent(entry.getValue(), maximum) >= minimum)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        return switch (mode) {
            case PERSONAL, CONTRIBUTION -> Set.copyOf(eligible);
            case OWNER -> owner != null && eligible.contains(owner) ? Set.of(owner) : Set.of();
            case LAST_HIT -> lastHit != null && eligible.contains(lastHit) ? Set.of(lastHit) : Set.of();
            case TOP_DAMAGE -> eligible.stream().sorted(Comparator
                    .<UUID>comparingDouble(damage::get).reversed().thenComparing(UUID::toString))
                    .findFirst().map(Set::of).orElse(Set.of());
        };
    }
}
