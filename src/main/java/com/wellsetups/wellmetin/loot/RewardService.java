package com.wellsetups.wellmetin.loot;

import com.wellsetups.wellmetin.api.event.MetinRewardEvent;
import com.wellsetups.wellmetin.config.Configuration;
import com.wellsetups.wellmetin.currency.CoinService;
import com.wellsetups.wellmetin.integration.ItemProviders;
import com.wellsetups.wellmetin.lang.Messages;
import com.wellsetups.wellmetin.persistence.RewardDelivery;
import com.wellsetups.wellmetin.persistence.SqliteStore;
import com.wellsetups.wellmetin.platform.PlayerDirectory;
import com.wellsetups.wellmetin.platform.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RewardService {
    private final SqliteStore store;
    private final Scheduler scheduler;
    private final PlayerDirectory players;
    private final ItemProviders items;
    private final CoinService coins;
    private final Supplier<Configuration> configuration;
    private final Consumer<String> errors;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public RewardService(SqliteStore store, Scheduler scheduler, PlayerDirectory players, ItemProviders items,
                         CoinService coins, Supplier<Configuration> configuration, Consumer<String> errors) {
        this.store = store;
        this.scheduler = scheduler;
        this.players = players;
        this.items = items;
        this.coins = coins;
        this.configuration = configuration;
        this.errors = errors;
    }

    public void pump(UUID player) {
        if (closed || !running.add(player)) {
            return;
        }
        store.pending(player, configuration.get().settings().rewardBatch()).thenCompose(pending -> {
            CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
            for (RewardDelivery reward : pending) {
                chain = chain.thenCompose(proceed -> proceed && !closed ? schedule(reward)
                        : CompletableFuture.completedFuture(false));
            }
            return chain;
        }).whenComplete((ignored, failure) -> {
            running.remove(player);
            if (failure != null && reported.add("player/" + player)) {
                errors.accept("Reward delivery for " + player + " failed; pending rewards retained: " + failure);
            }
        });
    }

    private CompletableFuture<Boolean> schedule(RewardDelivery reward) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        players.execute(reward.player(), player -> {
            try {
                deliver(player, reward).whenComplete((result, failure) -> {
                    if (failure == null) {
                        future.complete(result);
                    } else {
                        future.completeExceptionally(failure);
                    }
                });
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        }, () -> future.complete(false));
        return future;
    }

    private CompletableFuture<Boolean> deliver(Player player, RewardDelivery reward) {
        if (closed) {
            return CompletableFuture.completedFuture(false);
        }
        if (!reward.entry().permission().isEmpty() && !player.hasPermission(reward.entry().permission())) {
            return store.claim(reward.id());
        }
        MetinRewardEvent event = new MetinRewardEvent(reward);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return store.claim(reward.id());
        }
        if (reward.entry().kind() == LootTable.Kind.COIN) {
            if (!configuration.get().settings().currencyEnabled()) {
                return store.claim(reward.id());
            }
            return store.claimCoins(reward, event.getAmount()).thenApply(change -> {
                coins.notifyChange(change);
                return true;
            });
        }
        return deliverExternal(player, reward, event.getAmount());
    }

    private CompletableFuture<Boolean> deliverExternal(Player player, RewardDelivery reward, int amount) {
        List<ItemStack> stacks = List.of();
        if (reward.entry().kind() == LootTable.Kind.ITEM) {
            ItemStack prototype = items.create(reward.entry().item(), 1);
            if (!InventoryRewards.fits(player, prototype, amount)) {
                return CompletableFuture.completedFuture(false);
            }
            stacks = InventoryRewards.prepare(prototype, amount);
        }
        String name = player.getName();
        if (reward.entry().kind() == LootTable.Kind.CONSOLE_COMMAND || reward.entry().kind() == LootTable.Kind.PLAYER_COMMAND) {
            safeCommand(reward.entry().value(), name, amount, reward);
        }
        List<ItemStack> prepared = stacks;
        return store.claim(reward.id()).thenCompose(claimed -> {
            if (!claimed || closed) {
                return CompletableFuture.completedFuture(!closed);
            }
            CompletableFuture<Boolean> finished = new CompletableFuture<>();
            players.execute(reward.player(), recipient -> {
                try {
                    apply(recipient, reward, amount, prepared, name);
                    finished.complete(true);
                } catch (RuntimeException failure) {
                    finished.completeExceptionally(failure);
                }
            }, () -> {
                errors.accept("Claimed nontransactional reward " + reward.id() + " lost its online recipient before delivery");
                finished.complete(false);
            });
            return finished;
        });
    }

    private void apply(Player player, RewardDelivery reward, int amount, List<ItemStack> stacks, String name) {
        Map<String, Object> values = Map.of("player", name, "uuid", player.getUniqueId(), "amount", amount,
                "metin", reward.metin(), "instance", reward.instance());
        String value = Messages.format(reward.entry().value(), values);
        switch (reward.entry().kind()) {
            case ITEM -> {
                for (ItemStack stack : stacks) {
                    player.getInventory().addItem(stack).values().forEach(leftover ->
                            player.getWorld().dropItem(player.getLocation(), leftover));
                }
            }
            case MESSAGE -> player.sendMessage(value);
            case SOUND -> player.playSound(player.getLocation(), value, 1, 1);
            case PLAYER_COMMAND -> player.performCommand(safeCommand(reward.entry().value(), name, amount, reward));
            case CONSOLE_COMMAND -> {
                String command = safeCommand(reward.entry().value(), name, amount, reward);
                scheduler.global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command), 1, 0);
            }
            default -> throw new IllegalStateException("unexpected external reward kind");
        }
    }

    public static String safeCommand(String template, String player, int amount, RewardDelivery reward) {
        if ((template.contains("<player>") && !player.matches("[A-Za-z0-9_]{1,16}"))
                || template.indexOf('\n') >= 0 || template.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("unsafe command template or player name");
        }
        // Names and arbitrary display text are never interpolated into executable commands.
        return template.replace("<player>", player).replace("<uuid>", reward.player().toString())
                .replace("<amount>", Integer.toString(amount)).replace("<instance>", reward.instance().toString());
    }

    public void close() { closed = true; }
}
