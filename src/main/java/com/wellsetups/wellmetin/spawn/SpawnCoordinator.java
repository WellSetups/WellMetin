package com.wellsetups.wellmetin.spawn;

import com.wellsetups.wellmetin.api.Position;
import com.wellsetups.wellmetin.config.Configuration;
import com.wellsetups.wellmetin.config.MetinDefinition;
import com.wellsetups.wellmetin.config.SpawnRule;
import com.wellsetups.wellmetin.metin.MetinLifecycle;
import com.wellsetups.wellmetin.platform.Locations;
import com.wellsetups.wellmetin.platform.PlayerDirectory;
import com.wellsetups.wellmetin.platform.Scheduler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Location;

public final class SpawnCoordinator {
    private final Supplier<Configuration> configuration;
    private final MetinLifecycle lifecycle;
    private final PlayerDirectory players;
    private final Scheduler scheduler;
    private final Set<String> searching = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public SpawnCoordinator(Supplier<Configuration> configuration, MetinLifecycle lifecycle,
                            PlayerDirectory players, Scheduler scheduler) {
        this.configuration = configuration;
        this.lifecycle = lifecycle;
        this.players = players;
        this.scheduler = scheduler;
    }

    public void tick() {
        for (SpawnRule rule : configuration.get().settings().spawns()) {
            if (!closed && lifecycle.spawnAvailable(rule.id()) && searching.add(rule.id())) {
                attempt(rule, rule.attempts());
            }
        }
    }

    private void attempt(SpawnRule rule, int remaining) {
        if (closed || remaining == 0 || !lifecycle.spawnAvailable(rule.id())) {
            searching.remove(rule.id());
            return;
        }
        Location location;
        try {
            location = Locations.resolve(candidate(rule));
        } catch (IllegalArgumentException unloadedWorld) {
            searching.remove(rule.id());
            return;
        }
        scheduler.region(location, () -> inspect(rule, remaining, location), 1, 0);
    }

    private void inspect(SpawnRule rule, int remaining, Location location) {
        if (closed) {
            searching.remove(rule.id());
            return;
        }
        var world = java.util.Objects.requireNonNull(location.getWorld());
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            attempt(rule, remaining - 1);
            return;
        }
        if (rule.mode() == MetinDefinition.SpawnMode.RANDOM_REGION) {
            location.setY(world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1);
        }
        Position position = Locations.snapshot(location);
        if (position.y() < rule.minimumY() || position.y() > rule.maximumY() || !playerDistance(rule, position)) {
            attempt(rule, remaining - 1);
            return;
        }
        lifecycle.spawn(rule.definition(), position, null, rule.id(), rule.mode()).whenComplete((metin, failure) -> {
            if (failure != null && !closed) {
                attempt(rule, remaining - 1);
            } else {
                searching.remove(rule.id());
            }
        });
    }

    private Position candidate(SpawnRule rule) {
        if (rule.mode() == MetinDefinition.SpawnMode.STATIC) {
            return rule.position();
        }
        double x = rule.position().x() + ThreadLocalRandom.current().nextInt(-rule.radius(), rule.radius() + 1);
        double z = rule.position().z() + ThreadLocalRandom.current().nextInt(-rule.radius(), rule.radius() + 1);
        return new Position(rule.position().world(), x, rule.position().y(), z, rule.position().yaw());
    }

    private boolean playerDistance(SpawnRule rule, Position position) {
        double nearest = Double.POSITIVE_INFINITY;
        for (var id : players.ids()) {
            Position player = players.position(id);
            if (player != null) {
                nearest = Math.min(nearest, position.distanceSquared(player));
            }
        }
        return nearest >= rule.minimumPlayerDistance() * rule.minimumPlayerDistance()
                && nearest <= rule.maximumPlayerDistance() * rule.maximumPlayerDistance();
    }

    public void close() { closed = true; }
}
