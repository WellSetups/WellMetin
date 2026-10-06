package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.api.Position;

public record SpawnRule(String id, String definition, MetinDefinition.SpawnMode mode, Position position,
                        int radius, int minimumY, int maximumY, int attempts,
                        double minimumPlayerDistance, double maximumPlayerDistance) { }
