package com.wellsetups.wellmetin.command;

import com.wellsetups.wellmetin.currency.CoinMath;
import com.wellsetups.wellmetin.currency.CoinService;
import com.wellsetups.wellmetin.platform.PlayerDirectory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class CoinCommand implements Subcommand {
    private final CoinMath.Operation operation;
    private final CoinService coins;
    private final PlayerDirectory players;
    private final Replies replies;
    private final Consumer<String> errors;

    CoinCommand(CoinMath.Operation operation, CoinService coins, PlayerDirectory players, Replies replies, Consumer<String> errors) {
        this.operation = operation;
        this.coins = coins;
        this.players = players;
        this.replies = replies;
        this.errors = errors;
    }

    @Override
    public void execute(CommandSender sender, String[] arguments) {
        if (operation == null) {
            query(sender, arguments);
            return;
        }
        if (arguments.length != 2) {
            throw new IllegalArgumentException("expected player and amount");
        }
        UUID target = target(arguments[0]);
        long amount = CoinMath.amount(arguments[1]);
        coins.change(target, amount, operation, "admin/" + UUID.randomUUID()).whenComplete((change, failure) -> {
            if (failure == null) {
                replies.send(sender, "coin.changed", Map.of("player", arguments[0], "coin", change.after()));
            } else {
                errors.accept("Coin command failed for " + target + ": " + failure);
                replies.send(sender, "coin.failed");
            }
        });
    }

    private void query(CommandSender sender, String[] arguments) {
        if (arguments.length > 1) {
            throw new IllegalArgumentException("expected optional player");
        }
        UUID player;
        String name;
        if (arguments.length == 0 && sender instanceof Player self) {
            player = self.getUniqueId();
            name = self.getName();
        } else if (arguments.length == 1) {
            player = target(arguments[0]);
            name = arguments[0];
        } else {
            replies.send(sender, "command.player-required");
            return;
        }
        coins.balance(player).whenComplete((balance, failure) -> {
            if (failure == null) {
                replies.send(sender, "coin.balance", Map.of("player", name, "coin", balance));
            } else {
                errors.accept("Balance query failed for " + player + ": " + failure);
                replies.send(sender, "coin.failed");
            }
        });
    }

    private UUID target(String text) {
        UUID online = players.byName(text);
        return online == null ? Arguments.uuid(text) : online;
    }

    @Override public List<String> complete(CommandSender sender, String[] arguments) {
        return arguments.length == 1 ? players.names() : List.of();
    }
}
