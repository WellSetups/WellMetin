package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.api.ItemReference;
import java.util.List;
import java.util.Map;

public record WaveDefinition(String id, List<Mob> mobs, long timeoutSeconds) {
    public WaveDefinition { mobs = List.copyOf(mobs); }
    public record Effect(String type, int ticks, int amplifier) { }
    public record Mob(String type, int amount, double minimumRadius, double maximumRadius,
                      String name, double healthMultiplier, double damageMultiplier, boolean glowing,
                      boolean ai, boolean vanillaDrops, double chance, int level, String loot,
                      Map<String, ItemReference> equipment, List<Effect> effects) {
        public Mob {
            equipment = Map.copyOf(equipment);
            effects = List.copyOf(effects);
        }
    }
}
