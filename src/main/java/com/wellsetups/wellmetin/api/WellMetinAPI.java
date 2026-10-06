package com.wellsetups.wellmetin.api;

import com.wellsetups.wellmetin.config.MetinDefinition;
import com.wellsetups.wellmetin.currency.CoinMath;
import com.wellsetups.wellmetin.damage.DamageModifier;
import com.wellsetups.wellmetin.persistence.SqliteStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Obtain through Bukkit ServicesManager. Queries are snapshots; mutations schedule their own work.
 * Future continuations have no Bukkit thread guarantee. Never join a future from a server thread.
 */
public interface WellMetinAPI {
    Optional<MetinDefinition> definition(String id);
    List<MetinView> activeMetins();
    Optional<MetinView> metin(UUID id);
    CompletableFuture<MetinView> spawn(String definitionId, Position position, UUID owner);
    CompletableFuture<Boolean> remove(UUID instanceId);
    CompletableFuture<Long> balance(UUID player);
    CompletableFuture<Boolean> hasBalance(UUID player, long amount);
    /** Reuse the same transaction ID on retry; IDs are globally unique within this database. */
    CompletableFuture<SqliteStore.CoinChange> changeCoins(UUID player, long amount,
                                                        CoinMath.Operation operation, String transactionId);
    AutoCloseable registerDamageModifier(String id, int priority, DamageModifier modifier);
    /** Register during enable and close the registration before the providing plugin disables. */
    AutoCloseable registerItemProvider(String id, ItemProvider provider);
}
