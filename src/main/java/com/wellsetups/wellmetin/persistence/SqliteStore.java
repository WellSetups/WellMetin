package com.wellsetups.wellmetin.persistence;

import com.wellsetups.wellmetin.currency.CoinMath;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SqliteStore implements AutoCloseable {
    public record CoinChange(UUID player, long before, long after, boolean applied, String transactionId) { }
    private final Path file;
    private final DatabaseExecutor executor;
    private final JsonCodec codec = new JsonCodec();
    private final Consumer<String> errors;
    private final AtomicBoolean closed = new AtomicBoolean();
    private Connection connection;
    private final CompletableFuture<Void> closedFuture = new CompletableFuture<>();

    public SqliteStore(Path file, int capacity, Consumer<String> errors) {
        this.file = file;
        executor = new DatabaseExecutor(capacity);
        this.errors = errors;
    }

    public CompletableFuture<Void> initialize() {
        return executor.submit(() -> {
            connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file.toAbsolutePath(), new Properties());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=FULL");
                statement.execute("PRAGMA busy_timeout=5000");
                migrate(statement);
            }
            return null;
        });
    }

    private void migrate(Statement statement) throws SQLException {
        int version;
        try (var rows = statement.executeQuery("PRAGMA user_version")) {
            version = rows.next() ? rows.getInt(1) : 0;
        }
        if (version > 1) {
            throw new SQLException("database schema is newer than this WellMetin version");
        }
        transaction(() -> {
            statement.execute("CREATE TABLE IF NOT EXISTS balances (player TEXT PRIMARY KEY, balance INTEGER NOT NULL CHECK(balance >= 0))");
            statement.execute("CREATE TABLE IF NOT EXISTS coin_transactions (id TEXT PRIMARY KEY, player TEXT NOT NULL, before_value INTEGER NOT NULL, after_value INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS instances (id TEXT PRIMARY KEY, version INTEGER NOT NULL, alive INTEGER NOT NULL, payload TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS rewards (id TEXT PRIMARY KEY, player TEXT NOT NULL, claimed INTEGER NOT NULL DEFAULT 0, payload TEXT NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS pending_rewards ON rewards(player, claimed)");
            statement.execute("CREATE TABLE IF NOT EXISTS spawn_cooldowns (id TEXT PRIMARY KEY, available_at INTEGER NOT NULL)");
            statement.execute("PRAGMA user_version=1");
            return null;
        });
    }

    public CompletableFuture<List<SavedMetin>> restore() {
        return executor.submit(() -> {
            List<SavedMetin> result = new ArrayList<>();
            try (var query = connection.prepareStatement("SELECT id, payload FROM instances WHERE alive=1");
                 var rows = query.executeQuery()) {
                while (rows.next()) {
                    try {
                        SavedMetin saved = codec.decode(rows.getString(2), SavedMetin.class);
                        if (!saved.id().toString().equals(rows.getString(1)) || saved.health() <= 0) {
                            throw new IllegalArgumentException("instance ID or health does not match row");
                        }
                        result.add(saved);
                    } catch (IllegalArgumentException failure) {
                        errors.accept("Corrupt instance " + rows.getString(1) + ": " + failure.getMessage());
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<Boolean> save(SavedMetin state, List<RewardDelivery> rewards, boolean persistent, long respawnAt) {
        String payload = codec.encode(state);
        List<RewardDelivery> copy = List.copyOf(rewards);
        return executor.submit(() -> transaction(() -> {
            try (PreparedStatement save = connection.prepareStatement("INSERT INTO instances(id,version,alive,payload) VALUES(?,?,?,?) "
                    + "ON CONFLICT(id) DO UPDATE SET version=excluded.version,alive=excluded.alive,payload=excluded.payload WHERE instances.version<excluded.version")) {
                save.setString(1, state.id().toString());
                save.setLong(2, state.version());
                save.setInt(3, persistent && state.health() > 0 ? 1 : 0);
                save.setString(4, payload);
                if (save.executeUpdate() == 0) {
                    return false;
                }
            }
            insertRewards(copy);
            if (respawnAt > 0 && !state.spawnId().isEmpty()) {
                saveCooldown(state.spawnId(), respawnAt);
            }
            return true;
        }));
    }

    public CompletableFuture<Void> enqueue(List<RewardDelivery> rewards) {
        List<RewardDelivery> copy = List.copyOf(rewards);
        return executor.submit(() -> transaction(() -> {
            insertRewards(copy);
            return null;
        }));
    }

    private void insertRewards(List<RewardDelivery> rewards) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT OR IGNORE INTO rewards(id,player,payload) VALUES(?,?,?)")) {
            for (RewardDelivery reward : rewards) {
                insert.setString(1, reward.id());
                insert.setString(2, reward.player().toString());
                insert.setString(3, codec.encode(reward));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public CompletableFuture<List<RewardDelivery>> pending(UUID player, int limit) {
        return executor.submit(() -> {
            List<RewardDelivery> result = new ArrayList<>();
            try (var query = connection.prepareStatement("SELECT payload FROM rewards WHERE player=? AND claimed=0 ORDER BY rowid LIMIT ?")) {
                query.setString(1, player.toString());
                query.setInt(2, limit);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        result.add(codec.decode(rows.getString(1), RewardDelivery.class));
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<Boolean> claim(String id) {
        return executor.submit(() -> claimNow(id));
    }

    private boolean claimNow(String id) throws SQLException {
        try (var update = connection.prepareStatement("UPDATE rewards SET claimed=1 WHERE id=? AND claimed=0")) {
            update.setString(1, id);
            return update.executeUpdate() == 1;
        }
    }

    public CompletableFuture<CoinChange> claimCoins(RewardDelivery delivery, long amount) {
        return executor.submit(() -> transaction(() -> {
            if (!claimNow(delivery.id())) {
                long balance = balanceNow(delivery.player());
                return new CoinChange(delivery.player(), balance, balance, false, delivery.id());
            }
            return changeNow(delivery.player(), amount, CoinMath.Operation.ADD, "reward/" + delivery.id());
        }));
    }

    public CompletableFuture<Long> balance(UUID player) { return executor.submit(() -> balanceNow(player)); }

    private long balanceNow(UUID player) throws SQLException {
        try (var query = connection.prepareStatement("SELECT balance FROM balances WHERE player=?")) {
            query.setString(1, player.toString());
            try (var rows = query.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0;
            }
        }
    }

    public CompletableFuture<CoinChange> change(UUID player, long amount, CoinMath.Operation operation, String transactionId) {
        return executor.submit(() -> transaction(() -> changeNow(player, amount, operation, transactionId)));
    }

    private CoinChange changeNow(UUID player, long amount, CoinMath.Operation operation, String transactionId) throws SQLException {
        try (var query = connection.prepareStatement("SELECT player,before_value,after_value FROM coin_transactions WHERE id=?")) {
            query.setString(1, transactionId);
            try (var rows = query.executeQuery()) {
                if (rows.next()) {
                    if (!rows.getString(1).equals(player.toString())) {
                        throw new IllegalArgumentException("transaction ID belongs to another player");
                    }
                    return new CoinChange(player, rows.getLong(2), rows.getLong(3), false, transactionId);
                }
            }
        }
        long before = balanceNow(player);
        long after = CoinMath.apply(before, amount, operation);
        try (var update = connection.prepareStatement("INSERT INTO balances(player,balance) VALUES(?,?) ON CONFLICT(player) DO UPDATE SET balance=excluded.balance");
             var log = connection.prepareStatement("INSERT INTO coin_transactions(id,player,before_value,after_value) VALUES(?,?,?,?)")) {
            update.setString(1, player.toString());
            update.setLong(2, after);
            update.executeUpdate();
            log.setString(1, transactionId);
            log.setString(2, player.toString());
            log.setLong(3, before);
            log.setLong(4, after);
            log.executeUpdate();
        }
        return new CoinChange(player, before, after, true, transactionId);
    }

    public CompletableFuture<java.util.Map<String, Long>> cooldowns() {
        return executor.submit(() -> {
            java.util.Map<String, Long> result = new java.util.HashMap<>();
            try (var query = connection.prepareStatement("SELECT id,available_at FROM spawn_cooldowns");
                 var rows = query.executeQuery()) {
                while (rows.next()) {
                    result.put(rows.getString(1), rows.getLong(2));
                }
            }
            return java.util.Map.copyOf(result);
        });
    }

    private void saveCooldown(String id, long availableAt) throws SQLException {
        try (var save = connection.prepareStatement("INSERT INTO spawn_cooldowns(id,available_at) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET available_at=excluded.available_at")) {
            save.setString(1, id);
            save.setLong(2, availableAt);
            save.executeUpdate();
        }
    }

    private <T> T transaction(DatabaseExecutor.Work<T> work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            T result = work.run();
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdown(() -> {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException failure) {
                        errors.accept("Could not close SQLite: " + failure.getMessage());
                    }
                }
            }).whenComplete((ignored, failure) -> {
                if (failure == null) {
                    closedFuture.complete(null);
                } else {
                    closedFuture.completeExceptionally(failure);
                }
            });
        }
    }

    /** Completes after queued work has drained and the connection is closed. Never wait on a tick thread. */
    public CompletableFuture<Void> closedFuture() { return closedFuture; }
}
