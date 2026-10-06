package com.wellsetups.wellmetin.currency;

import com.wellsetups.wellmetin.api.event.MetinCoinChangeEvent;
import com.wellsetups.wellmetin.persistence.SqliteStore;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;

public final class CoinService implements AutoCloseable {
    private final SqliteStore store;
    private final BooleanSupplier enabled;
    private final Consumer<String> errors;
    private final ThreadPoolExecutor notifications = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1024), action -> {
                Thread thread = new Thread(action, "WellMetin-coin-events");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public CoinService(SqliteStore store, BooleanSupplier enabled, Consumer<String> errors) {
        this.store = store;
        this.enabled = enabled;
        this.errors = errors;
    }

    public CompletableFuture<Long> balance(UUID player) { return store.balance(player); }
    public CompletableFuture<Boolean> has(UUID player, long amount) {
        if (amount < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("amount must be nonnegative"));
        }
        return balance(player).thenApply(balance -> enabled.getAsBoolean() && balance >= amount);
    }

    public CompletableFuture<SqliteStore.CoinChange> change(UUID player, long amount,
                                                            CoinMath.Operation operation, String transactionId) {
        if (!enabled.getAsBoolean()) {
            return CompletableFuture.failedFuture(new IllegalStateException("currency is disabled"));
        }
        if (transactionId == null || transactionId.isBlank() || transactionId.length() > 256) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("transaction ID must contain 1-256 characters"));
        }
        return store.change(player, amount, operation, transactionId).thenApply(change -> {
            notifyChange(change);
            return change;
        });
    }

    public void notifyChange(SqliteStore.CoinChange change) {
        if (change.applied()) {
            try {
                notifications.execute(() -> Bukkit.getPluginManager().callEvent(new MetinCoinChangeEvent(change)));
            } catch (RejectedExecutionException unavailable) {
                if (!notifications.isShutdown()) {
                    errors.accept("Coin notification queue is full; committed transaction " + change.transactionId()
                            + " was not announced to API listeners");
                }
            }
        }
    }

    @Override public void close() { notifications.shutdown(); }
}
