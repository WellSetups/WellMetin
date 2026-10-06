package com.wellsetups.wellmetin.config;

import java.util.List;

public record Settings(String language, double damageMultiplier, boolean currencyEnabled,
                       boolean persistentMetins, long maintenanceTicks, int maximumActive,
                       int databaseQueue, int rewardBatch, boolean debug, List<SpawnRule> spawns) {
    public Settings { spawns = List.copyOf(spawns); }
}
