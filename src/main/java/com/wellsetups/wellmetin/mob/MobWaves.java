package com.wellsetups.wellmetin.mob;

import com.wellsetups.wellmetin.api.event.MetinLifecycleEvent;
import com.wellsetups.wellmetin.config.WaveDefinition;
import com.wellsetups.wellmetin.integration.ItemProviders;
import com.wellsetups.wellmetin.loot.RewardPlanner;
import com.wellsetups.wellmetin.loot.RewardService;
import com.wellsetups.wellmetin.metin.ActiveMetin;
import com.wellsetups.wellmetin.persistence.SqliteStore;
import com.wellsetups.wellmetin.platform.Scheduler;
import com.wellsetups.wellmetin.platform.WorldSpawner;
import com.wellsetups.wellmetin.visual.OwnedEntities;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public final class MobWaves {
    public record Guardian(ActiveMetin metin, WaveDefinition.Mob settings, LivingEntity entity, long expires) { }
    private final ConcurrentHashMap<UUID, Guardian> guardians = new ConcurrentHashMap<>();
    private final Scheduler scheduler;
    private final OwnedEntities tags;
    private final MobEquipment equipment;
    private final SqliteStore store;
    private final RewardPlanner planner;
    private final RewardService rewards;
    private final Consumer<String> errors;

    public MobWaves(Scheduler scheduler, OwnedEntities tags, ItemProviders items, SqliteStore store,
                    RewardPlanner planner, RewardService rewards, Consumer<String> errors) {
        this.scheduler = scheduler;
        this.tags = tags;
        equipment = new MobEquipment(items);
        this.store = store;
        this.planner = planner;
        this.rewards = rewards;
        this.errors = errors;
    }

    public void spawn(ActiveMetin metin, String waveId, boolean lock) {
        WaveDefinition wave = metin.configuration().waves().get(waveId);
        Location origin = metin.carrier().getLocation();
        for (WaveDefinition.Mob specification : wave.mobs()) {
            for (int index = 0; index < specification.amount(); index++) {
                if (ThreadLocalRandom.current().nextDouble(100) < specification.chance()) {
                    reserve(metin, origin, wave, specification, lock);
                }
            }
        }
        metin.refresh();
    }

    private void reserve(ActiveMetin metin, Location origin, WaveDefinition wave, WaveDefinition.Mob mob, boolean lock) {
        UUID reservation = UUID.randomUUID();
        long expires = System.currentTimeMillis() + wave.timeoutSeconds() * 1000;
        if (lock) {
            metin.guardians().put(reservation, expires);
        }
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double radius = mob.minimumRadius() + ThreadLocalRandom.current().nextDouble()
                * (mob.maximumRadius() - mob.minimumRadius());
        Location target = origin.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        scheduler.region(target, () -> {
            try {
                if (!metin.closed() && metin.view().health() > 0 && safe(target)) {
                    create(metin, target, mob, expires, lock);
                }
            } catch (IllegalArgumentException | IllegalStateException failure) {
                errors.accept("Guardian wave " + wave.id() + " could not spawn: " + failure.getMessage());
            } finally {
                metin.guardians().remove(reservation);
            }
        }, 1, 0);
    }

    private boolean safe(Location location) {
        var world = java.util.Objects.requireNonNull(location.getWorld());
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return false;
        }
        int y = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        if (Math.abs(y - location.getY()) > 8 || y + 2 >= world.getMaxHeight()) {
            return false;
        }
        location.setY(y);
        return location.getBlock().isPassable() && !location.clone().add(0, -1, 0).getBlock().isLiquid();
    }

    private void create(ActiveMetin metin, Location location, WaveDefinition.Mob settings, long expires, boolean lock) {
        EntityType type = EntityType.valueOf(settings.type());
        Class<? extends org.bukkit.entity.Entity> entityClass = java.util.Objects.requireNonNull(type.getEntityClass());
        LivingEntity entity = (LivingEntity) WorldSpawner.spawn(
                location, entityClass, spawned -> {
                    tags.tag(spawned, metin.id(), "guardian");
                    tags.level(spawned, settings.level());
                });
        try {
            equipment.configure(entity, settings);
            if (!entity.isValid() || metin.closed()) {
                entity.remove();
                return;
            }
            UUID id = entity.getUniqueId();
            guardians.put(id, new Guardian(metin, settings, entity, expires));
            if (lock) {
                metin.guardians().put(id, expires);
            }
            Bukkit.getPluginManager().callEvent(new MetinLifecycleEvent(MetinLifecycleEvent.Kind.MOB_SPAWN,
                    metin.view(), settings.type(), id));
        } catch (RuntimeException failure) {
            entity.remove();
            throw failure;
        }
    }

    public Guardian get(UUID id) { return guardians.get(id); }

    public void died(UUID id, UUID killer) {
        Guardian guardian = detach(id);
        if (guardian == null || killer == null || guardian.settings().loot().isEmpty() || guardian.metin().closed()) {
            return;
        }
        var table = guardian.metin().configuration().loot().get(guardian.settings().loot());
        var deliveries = planner.plan(table, guardian.metin().id(), guardian.metin().definition().name(),
                "guardian/" + id, killer, 100);
        store.enqueue(deliveries).whenComplete((ignored, failure) -> {
            if (failure == null) {
                rewards.pump(killer);
            } else {
                errors.accept("Guardian rewards could not be saved: " + failure);
            }
        });
    }

    public Guardian detach(UUID id) {
        Guardian guardian = guardians.remove(id);
        if (guardian != null) {
            guardian.metin().guardians().remove(id);
        }
        return guardian;
    }

    public void maintenance() {
        for (Map.Entry<UUID, Guardian> entry : guardians.entrySet()) {
            Guardian guardian = entry.getValue();
            scheduler.entity(guardian.entity(), () -> {
                if (guardian.expires() <= System.currentTimeMillis() || guardian.metin().closed()) {
                    detach(entry.getKey());
                    guardian.entity().remove();
                }
            }, () -> detach(entry.getKey()), 1, 0);
        }
    }

    public void cleanup(ActiveMetin metin) {
        guardians.forEach((id, guardian) -> {
            if (guardian.metin() == metin) {
                detach(id);
                scheduler.entity(guardian.entity(), guardian.entity()::remove, () -> { }, 1, 0);
            }
        });
        metin.guardians().clear();
    }

    public void shutdown() {
        for (Guardian guardian : List.copyOf(guardians.values())) {
            if (scheduler.owns(guardian.entity())) {
                guardian.entity().remove();
            }
        }
        guardians.clear();
    }
}
