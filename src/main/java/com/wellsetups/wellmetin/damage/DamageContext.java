package com.wellsetups.wellmetin.damage;

import java.util.UUID;

/** Contains no Bukkit objects, so modifiers can be tested without a server. */
public record DamageContext(UUID playerId, UUID instanceId, String definitionId, String weapon) { }
