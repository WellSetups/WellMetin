package com.wellsetups.wellmetin.api;

import com.wellsetups.wellmetin.config.Configuration;
import com.wellsetups.wellmetin.config.MetinDefinition;
import com.wellsetups.wellmetin.currency.CoinMath;
import com.wellsetups.wellmetin.currency.CoinService;
import com.wellsetups.wellmetin.damage.DamageModifier;
import com.wellsetups.wellmetin.damage.DamagePipeline;
import com.wellsetups.wellmetin.integration.ItemProviders;
import com.wellsetups.wellmetin.metin.MetinLifecycle;
import com.wellsetups.wellmetin.metin.MetinRegistry;
import com.wellsetups.wellmetin.persistence.SqliteStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class WellMetinService implements WellMetinAPI {
    private final Supplier<Configuration> configuration;
    private final MetinRegistry registry;
    private final MetinLifecycle lifecycle;
    private final CoinService coins;
    private final DamagePipeline damage;
    private final ItemProviders items;

    public WellMetinService(Supplier<Configuration> configuration, MetinRegistry registry, MetinLifecycle lifecycle,
                            CoinService coins, DamagePipeline damage, ItemProviders items) {
        this.configuration = configuration;
        this.registry = registry;
        this.lifecycle = lifecycle;
        this.coins = coins;
        this.damage = damage;
        this.items = items;
    }

    @Override public Optional<MetinDefinition> definition(String id) { return Optional.ofNullable(configuration.get().metins().get(id)); }
    @Override public List<MetinView> activeMetins() { return registry.views(); }
    @Override public Optional<MetinView> metin(UUID id) {
        var active = registry.get(id);
        return active == null ? Optional.empty() : Optional.of(active.view());
    }
    @Override public CompletableFuture<MetinView> spawn(String id, Position position, UUID owner) {
        return lifecycle.spawn(id, position, owner, "", MetinDefinition.SpawnMode.COMMAND_ONLY);
    }
    @Override public CompletableFuture<Boolean> remove(UUID id) { return lifecycle.remove(id); }
    @Override public CompletableFuture<Long> balance(UUID player) { return coins.balance(player); }
    @Override public CompletableFuture<Boolean> hasBalance(UUID player, long amount) { return coins.has(player, amount); }
    @Override public CompletableFuture<SqliteStore.CoinChange> changeCoins(UUID player, long amount,
                                                                         CoinMath.Operation operation, String transactionId) {
        return coins.change(player, amount, operation, transactionId);
    }
    @Override public AutoCloseable registerDamageModifier(String id, int priority, DamageModifier modifier) {
        return damage.register(id, priority, modifier);
    }
    @Override public AutoCloseable registerItemProvider(String id, ItemProvider provider) { return items.register(id, provider); }
}
