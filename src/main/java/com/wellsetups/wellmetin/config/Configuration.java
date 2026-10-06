package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.lang.Messages;
import com.wellsetups.wellmetin.loot.LootTable;
import java.util.Map;

public record Configuration(Settings settings, CommandSettings commands, Messages messages,
                            Map<String, MetinDefinition> metins, Map<String, LootTable> loot,
                            Map<String, WaveDefinition> waves) {
    public Configuration {
        metins = Map.copyOf(metins);
        loot = Map.copyOf(loot);
        waves = Map.copyOf(waves);
    }
}
