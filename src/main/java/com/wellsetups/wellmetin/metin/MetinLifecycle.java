package com.wellsetups.wellmetin.metin;

import com.wellsetups.wellmetin.api.MetinView;
import com.wellsetups.wellmetin.api.Position;
import com.wellsetups.wellmetin.api.event.MetinLifecycleEvent;
import com.wellsetups.wellmetin.config.Configuration;
import com.wellsetups.wellmetin.config.MetinDefinition;
import com.wellsetups.wellmetin.mob.MobWaves;
import com.wellsetups.wellmetin.persistence.RewardDelivery;
import com.wellsetups.wellmetin.persistence.SavedMetin;
import com.wellsetups.wellmetin.persistence.SqliteStore;
import com.wellsetups.wellmetin.platform.Locations;
import com.wellsetups.wellmetin.platform.Scheduler;
import com.wellsetups.wellmetin.spawn.SpawnSafety;
import com.wellsetups.wellmetin.visual.HitboxProvider;
import com.wellsetups.wellmetin.visual.VisualProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public final class MetinLifecycle {
    private final Scheduler scheduler;
    private final Supplier<Configuration> configuration;
    private final MetinRegistry registry;
    private final HitboxProvider hitboxes;
    private final VisualProvider visuals;
    private final EncounterPersistence persistence;
    private final SqliteStore store;
    private final EncounterActions actions;
    private final MobWaves waves;
    private final Consumer<String> errors;
    private final PendingMetins pending = new PendingMetins();
    private final Set<UUID> restoring = ConcurrentHashMap.newKeySet();
    private final Set<UUID> awaitingPersistence = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> reservations = new HashMap<>();
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<String> occupiedSpawns = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public MetinLifecycle(Scheduler scheduler, Supplier<Configuration> configuration, MetinRegistry registry,
                          HitboxProvider hitboxes, VisualProvider visuals, EncounterPersistence persistence,
                          SqliteStore store, EncounterActions actions, MobWaves waves, Consumer<String> errors) {
        this.scheduler = scheduler;
        this.configuration = configuration;
        this.registry = registry;
        this.hitboxes = hitboxes;
        this.visuals = visuals;
        this.persistence = persistence;
        this.store = store;
        this.actions = actions;
        this.waves = waves;
        this.errors = errors;
    }

    public CompletableFuture<MetinView> spawn(String definitionId, Position position, UUID owner,
                                              String spawnId, MetinDefinition.SpawnMode mode) {
        Configuration config = configuration.get();
        MetinDefinition definition = config.metins().get(definitionId);
        if (closed || definition == null || !definition.spawn().modes().contains(mode)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Metin or spawn mode is unavailable"));
        }
        UUID id = UUID.randomUUID();
        if (!reserve(id, definition, config.settings().maximumActive(), spawnId)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("spawn is occupied or the active limit was reached"));
        }
        SavedMetin saved = new SavedMetin(id, definitionId, position, definition.health(), definition.health(),
                Map.of(), Set.of(), owner, null, spawnId, 0, 1, 0);
        CompletableFuture<MetinView> result = new CompletableFuture<>();
        try {
            Location location = Locations.resolve(position);
            scheduler.region(location, () -> createNew(saved, config, location, result), 1, 0);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            release(saved);
            result.completeExceptionally(failure);
        }
        return result;
    }

    private void createNew(SavedMetin saved, Configuration config, Location location, CompletableFuture<MetinView> result) {
        try {
            if (closed) {
                throw new IllegalStateException("plugin is stopping");
            }
            SpawnSafety.validate(location, config.metins().get(saved.definition()));
            ActiveMetin metin = create(saved, config, location);
            persistence.save(metin, true, List.of()).whenComplete((written, failure) -> {
                if (failure != null) {
                    result.completeExceptionally(failure);
                } else if (!closed) {
                    scheduler.region(location, () -> {
                        if (!metin.closed()) {
                            metin.ready();
                            actions.run(metin, metin.definition().onSpawn());
                            persistence.save(metin, true, List.of());
                            fire(metin, MetinLifecycleEvent.Kind.SPAWN);
                        }
                        result.complete(metin.view());
                    }, 1, 0);
                } else {
                    result.completeExceptionally(new IllegalStateException("plugin stopped during spawn"));
                }
            });
        } catch (IllegalArgumentException | IllegalStateException failure) {
            release(saved);
            result.completeExceptionally(failure);
        }
    }

    private ActiveMetin create(SavedMetin saved, Configuration config, Location location) {
        LivingEntity carrier = hitboxes.spawn(saved.id(), location, config.metins().get(saved.definition()).visual());
        VisualProvider.Visual visual = null;
        ActiveMetin registered = null;
        try {
            if (!carrier.isValid()) {
                throw new IllegalStateException("carrier spawn was cancelled by another plugin");
            }
            visual = visuals.spawn(saved.id(), location, config.metins().get(saved.definition()));
            ActiveMetin metin = new ActiveMetin(saved, config, carrier, visual);
            if (!registry.add(metin)) {
                throw new IllegalStateException("duplicate instance restoration " + saved.id());
            }
            registered = metin;
            visual.update(metin.view());
            metin.maintenance(scheduler.region(location, () -> maintain(metin),
                    config.settings().maintenanceTicks(), config.settings().maintenanceTicks()));
            return metin;
        } catch (RuntimeException failure) {
            if (registered != null) {
                registered.close();
                registered.cancelTasks();
                registry.remove(registered);
            }
            carrier.remove();
            if (visual != null) {
                visual.remove();
            }
            throw failure;
        }
    }

    public void restore(List<SavedMetin> states, Map<String, Long> savedCooldowns) {
        cooldowns.putAll(savedCooldowns);
        for (SavedMetin saved : states) {
            synchronized (reservations) {
                reservations.put(saved.id(), saved.definition());
            }
            if (!saved.spawnId().isEmpty()) {
                occupiedSpawns.add(saved.spawnId());
            }
            pending.put(saved);
            tryRestore(saved);
        }
    }

    public void loaded(String world, int x, int z) {
        pending.chunk(world, x, z).forEach(this::tryRestore);
    }

    public void retryRestoration() { pending.all().forEach(this::tryRestore); }

    private void tryRestore(SavedMetin saved) {
        Configuration config = configuration.get();
        if (closed || awaitingPersistence.contains(saved.id()) || !config.metins().containsKey(saved.definition())
                || !restoring.add(saved.id())) {
            return;
        }
        Location location;
        try {
            location = Locations.resolve(saved.position());
        } catch (IllegalArgumentException absentWorld) {
            restoring.remove(saved.id());
            return;
        }
        scheduler.region(location, () -> {
            try {
                if (!closed && pending.get(saved.id()) == saved && registry.get(saved.id()) == null
                        && location.getWorld().isChunkLoaded(saved.position().chunkX(), saved.position().chunkZ())) {
                    ActiveMetin metin = create(saved, config, location);
                    metin.ready();
                    metin.refresh();
                    pending.remove(saved.id());
                }
            } catch (IllegalArgumentException | IllegalStateException failure) {
                errors.accept("Could not restore Metin " + saved.id() + ": " + failure.getMessage());
            } finally {
                restoring.remove(saved.id());
            }
        }, 1, 0);
    }

    private void maintain(ActiveMetin metin) {
        if (closed || metin.closed()) {
            return;
        }
        if (!scheduler.owns(metin.carrier()) || !metin.carrier().isValid()
                || metin.view().position().distanceSquared(Locations.snapshot(metin.carrier().getLocation())) > 0.01) {
            suspend(metin);
            return;
        }
        long now = System.currentTimeMillis();
        metin.guardians().entrySet().removeIf(entry -> entry.getValue() <= now);
        metin.refresh();
    }

    public void suspend(ActiveMetin metin) {
        Location anchor = Locations.resolve(metin.view().position());
        if (!scheduler.owns(anchor)) {
            scheduler.region(anchor, () -> suspend(metin), 1, 0);
            return;
        }
        if (metin.closed()) {
            return;
        }
        if (metin.isEnding() || metin.state().health() <= 0) {
            persistence.save(metin, false, List.of());
            removeFinished(metin);
            return;
        }
        awaitingPersistence.add(metin.id());
        CompletableFuture<Boolean> saving = persistence.save(metin, true, List.of());
        pending.put(metin.saved());
        cleanup(metin);
        saving.whenComplete((written, failure) -> {
            if (failure == null && !closed) {
                awaitingPersistence.remove(metin.id());
                SavedMetin suspended = pending.get(metin.id());
                if (suspended != null) {
                    tryRestore(suspended);
                }
            }
        });
    }

    public void destroy(ActiveMetin metin, List<RewardDelivery> deliveries) {
        metin.ending();
        persistence.save(metin, false, deliveries).whenComplete((written, failure) -> {
            if (failure == null && !closed) {
                scheduler.region(Locations.resolve(metin.view().position()), () -> {
                    if (!metin.closed()) {
                        actions.run(metin, metin.definition().onDestroy());
                        fire(metin, MetinLifecycleEvent.Kind.DESTROY);
                        removeFinished(metin);
                    }
                }, 1, 0);
            }
        });
    }

    public CompletableFuture<Boolean> remove(UUID id) {
        ActiveMetin active = registry.get(id);
        SavedMetin suspended = pending.get(id);
        Position position = active == null ? (suspended == null ? null : suspended.position()) : active.view().position();
        if (position == null || closed) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            scheduler.region(Locations.resolve(position), () -> {
                ActiveMetin metin = registry.get(id);
                if (metin == null) {
                    removePending(id).whenComplete((removed, failure) -> complete(future, removed, failure));
                } else {
                    removeLoaded(metin, future);
                }
            }, 1, 0);
        } catch (IllegalArgumentException unloadedWorld) {
            return removePending(id);
        }
        return future;
    }

    private void removeLoaded(ActiveMetin metin, CompletableFuture<Boolean> future) {
        if (metin.closed()) {
            future.complete(false);
            return;
        }
        metin.ending();
        persistence.save(metin, false, List.of()).whenComplete((written, failure) -> {
            if (failure != null) {
                future.completeExceptionally(failure);
            } else if (!closed) {
                scheduler.region(Locations.resolve(metin.view().position()), () -> {
                    removeFinished(metin);
                    future.complete(true);
                }, 1, 0);
            } else {
                future.complete(false);
            }
        });
    }

    private static void complete(CompletableFuture<Boolean> future, Boolean result, Throwable failure) {
        if (failure == null) {
            future.complete(result);
        } else {
            future.completeExceptionally(failure);
        }
    }

    private CompletableFuture<Boolean> removePending(UUID id) {
        SavedMetin state = pending.remove(id);
        if (state == null) {
            return CompletableFuture.completedFuture(false);
        }
        SavedMetin removed = new SavedMetin(id, state.definition(), state.position(), 0, state.maximum(),
                state.contributions(), state.triggered(), state.owner(), state.lastHit(), state.spawnId(),
                state.version() + 1, state.multiplier(), state.immuneUntil());
        MetinDefinition definition = configuration.get().metins().get(state.definition());
        long availableAt = System.currentTimeMillis() + (definition == null ? 0 : definition.spawn().respawnSeconds() * 1000);
        return store.save(removed, List.of(), true, availableAt).whenComplete((written, failure) -> {
            if (failure == null && Boolean.TRUE.equals(written)) {
                if (!state.spawnId().isEmpty()) {
                    cooldowns.put(state.spawnId(), availableAt);
                }
                awaitingPersistence.remove(id);
                release(state);
            } else {
                pending.put(state);
            }
        });
    }

    private void removeFinished(ActiveMetin metin) {
        cooldowns.put(metin.spawnId(), System.currentTimeMillis() + metin.definition().spawn().respawnSeconds() * 1000);
        cleanup(metin);
        release(metin.saved());
    }

    private void cleanup(ActiveMetin metin) {
        if (metin.close()) {
            metin.cancelTasks();
            registry.remove(metin);
            waves.cleanup(metin);
            if (scheduler.owns(metin.carrier())) {
                metin.carrier().remove();
            } else {
                scheduler.entity(metin.carrier(), metin.carrier()::remove, () -> { }, 1, 0);
            }
            metin.visual().remove();
        }
    }

    private boolean reserve(UUID id, MetinDefinition definition, int maximum, String spawnId) {
        synchronized (reservations) {
            if (reservations.size() >= maximum || reservations.values().stream().filter(definition.id()::equals).count()
                    >= definition.spawn().maximumActive()) {
                return false;
            }
            if (!spawnId.isEmpty() && !occupiedSpawns.add(spawnId)) {
                return false;
            }
            reservations.put(id, definition.id());
            return true;
        }
    }

    private void release(SavedMetin state) {
        synchronized (reservations) {
            reservations.remove(state.id());
            occupiedSpawns.remove(state.spawnId());
        }
    }

    public boolean spawnAvailable(String id) {
        return !closed && !occupiedSpawns.contains(id) && cooldowns.getOrDefault(id, 0L) <= System.currentTimeMillis();
    }

    public int pendingCount() { return pending.size(); }

    private void fire(ActiveMetin metin, MetinLifecycleEvent.Kind kind) {
        Bukkit.getPluginManager().callEvent(new MetinLifecycleEvent(kind, metin.view(), "", metin.carrier().getUniqueId()));
    }

    public void shutdown() {
        closed = true;
        for (ActiveMetin metin : registry.active()) {
            Position position = metin.view().position();
            var world = Bukkit.getWorld(position.world());
            if (world != null && scheduler.owns(metin.carrier())
                    && scheduler.owns(new Location(world, position.x(), position.y(), position.z()))) {
                cleanup(metin);
            } else {
                metin.close();
            }
        }
        registry.clear();
        pending.clear();
        synchronized (reservations) {
            reservations.clear();
        }
        occupiedSpawns.clear();
    }
}
