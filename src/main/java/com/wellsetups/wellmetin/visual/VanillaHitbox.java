package com.wellsetups.wellmetin.visual;

import com.wellsetups.wellmetin.config.MetinDefinition;
import com.wellsetups.wellmetin.platform.WorldSpawner;
import java.util.UUID;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.entity.Cow;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Slime;

public final class VanillaHitbox implements HitboxProvider {
    private final OwnedEntities tags;

    public VanillaHitbox(OwnedEntities tags) { this.tags = tags; }

    @Override
    public LivingEntity spawn(UUID instance, Location location, MetinDefinition.Visual settings) {
        var world = java.util.Objects.requireNonNull(location.getWorld());
        Class<? extends Mob> type = switch (settings.hitbox().type()) {
            case IRON_GOLEM -> IronGolem.class;
            case COW -> Cow.class;
            case SLIME -> Slime.class;
        };
        if (type == Slime.class && world.getDifficulty() == Difficulty.PEACEFUL) {
            throw new IllegalArgumentException("SLIME hitboxes require non-peaceful difficulty; use IRON_GOLEM or COW");
        }
        return WorldSpawner.spawn(location, type, carrier -> {
            tags.tag(carrier, instance, "carrier");
            carrier.setAI(false);
            carrier.setAware(false);
            carrier.setGravity(false);
            carrier.setSilent(true);
            carrier.setInvisible(true);
            carrier.setCollidable(false);
            carrier.setRemoveWhenFarAway(false);
            carrier.setCanPickupItems(false);
            carrier.setLootTable(null);
            carrier.setMaximumNoDamageTicks(0);
            specialize(carrier, settings.hitbox());
            // Invulnerable would suppress the native weapon/cooldown damage calculation we need.
        });
    }

    private void specialize(Mob carrier, MetinDefinition.Hitbox settings) {
        if (carrier instanceof Slime slime) {
            slime.setSize(settings.slimeSize());
        } else if (carrier instanceof Cow cow) {
            if (settings.baby()) {
                cow.setBaby();
            } else {
                cow.setAdult();
            }
            cow.setAgeLock(true);
        } else if (carrier instanceof IronGolem golem) {
            golem.setPlayerCreated(true);
        }
    }
}
