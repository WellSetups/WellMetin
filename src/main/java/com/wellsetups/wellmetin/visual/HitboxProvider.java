package com.wellsetups.wellmetin.visual;

import com.wellsetups.wellmetin.config.MetinDefinition;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/** Implementations create a stationary, neutral living target on the supplied location's region. */
public interface HitboxProvider {
    LivingEntity spawn(UUID instance, Location location, MetinDefinition.Visual settings);
}
