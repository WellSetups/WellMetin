package com.wellsetups.wellmetin.command;

import com.wellsetups.wellmetin.api.WellMetinAPI;
import com.wellsetups.wellmetin.config.ConfigurationService;
import com.wellsetups.wellmetin.currency.CoinMath;
import com.wellsetups.wellmetin.currency.CoinService;
import com.wellsetups.wellmetin.metin.MetinLifecycle;
import com.wellsetups.wellmetin.metin.MetinRegistry;
import com.wellsetups.wellmetin.platform.PlayerDirectory;
import com.wellsetups.wellmetin.platform.Scheduler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.command.CommandSender;

public final class Commands {
    private Commands() { }

    public static CommandRouter create(ConfigurationService configuration, Scheduler scheduler, PlayerDirectory players,
                                        WellMetinAPI api, CoinService coins, MetinRegistry registry,
                                        MetinLifecycle lifecycle, Consumer<String> errors) {
        Replies replies = new Replies(scheduler, players, configuration::get);
        QueryCommands queries = new QueryCommands(configuration, registry, lifecycle, scheduler, replies);
        Map<String, Subcommand> handlers = new HashMap<>();
        handlers.put("help", queries::help);
        handlers.put("list", queries::list);
        handlers.put("debug", queries::debug);
        handlers.put("info", new Subcommand() {
            @Override public void execute(CommandSender sender, String[] arguments) { queries.info(sender, arguments); }
            @Override public List<String> complete(CommandSender sender, String[] arguments) {
                return arguments.length == 1 ? registry.views().stream().map(view -> view.id().toString()).toList() : List.of();
            }
        });
        handlers.put("spawn", new SpawnCommand(api, configuration::get, replies));
        handlers.put("remove", new RemoveCommand(api, registry, replies));
        handlers.put("coin", new CoinCommand(null, coins, players, replies, errors));
        handlers.put("givecoin", new CoinCommand(CoinMath.Operation.ADD, coins, players, replies, errors));
        handlers.put("removecoin", new CoinCommand(CoinMath.Operation.REMOVE, coins, players, replies, errors));
        handlers.put("setcoin", new CoinCommand(CoinMath.Operation.SET, coins, players, replies, errors));
        handlers.put("reload", (sender, arguments) -> {
            if (arguments.length != 0) {
                throw new IllegalArgumentException("unexpected arguments");
            }
            configuration.reload().whenComplete((ignored, failure) -> {
                if (failure == null) {
                    lifecycle.retryRestoration();
                    replies.send(sender, "reload.success");
                } else {
                    errors.accept("Configuration reload rejected: " + failure);
                    replies.send(sender, "reload.failed");
                }
            });
        });
        return new CommandRouter(configuration::get, handlers, replies, scheduler);
    }
}
