package com.wellsetups.wellmetin.command;

import com.wellsetups.wellmetin.config.CommandSettings;
import com.wellsetups.wellmetin.config.ConfigurationService;
import com.wellsetups.wellmetin.metin.MetinLifecycle;
import com.wellsetups.wellmetin.metin.MetinRegistry;
import com.wellsetups.wellmetin.platform.Scheduler;
import java.util.Map;
import org.bukkit.command.CommandSender;

final class QueryCommands {
    private final ConfigurationService configurations;
    private final MetinRegistry registry;
    private final MetinLifecycle lifecycle;
    private final Scheduler scheduler;
    private final Replies replies;

    QueryCommands(ConfigurationService configurations, MetinRegistry registry, MetinLifecycle lifecycle,
                   Scheduler scheduler, Replies replies) {
        this.configurations = configurations;
        this.registry = registry;
        this.lifecycle = lifecycle;
        this.scheduler = scheduler;
        this.replies = replies;
    }

    void help(CommandSender sender, String[] arguments) {
        noArguments(arguments);
        replies.send(sender, "help.header");
        configurations.get().commands().commands().values().stream()
                .sorted(java.util.Comparator.comparing(CommandSettings.Command::name))
                .filter(command -> command.enabled() && (command.permission().isEmpty() || sender.hasPermission(command.permission())))
                .forEach(command -> replies.send(sender, "help.entry", Map.of("command", command.name(),
                        "description", configurations.get().messages().text(command.descriptionKey(), Map.of()))));
    }

    void list(CommandSender sender, String[] arguments) {
        if (arguments.length > 1) {
            throw new IllegalArgumentException("expected optional page");
        }
        int page = arguments.length == 0 ? 1 : Integer.parseInt(arguments[0]);
        if (page < 1 || page > 1_000_000) {
            throw new IllegalArgumentException("invalid page");
        }
        var all = registry.views().stream().sorted(java.util.Comparator.comparing(view -> view.id().toString())).toList();
        replies.send(sender, "list.header", Map.of("count", all.size(), "pending", lifecycle.pendingCount(), "page", page));
        all.stream().skip((long) (page - 1) * 10).limit(10).forEach(view -> replies.send(sender, "list.entry",
                Map.of("id", view.id(), "metin", view.name(), "health", Math.round(view.health()),
                        "max_health", Math.round(view.maxHealth()), "world", view.position().world())));
    }

    void info(CommandSender sender, String[] arguments) {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("expected UUID");
        }
        var metin = registry.get(Arguments.uuid(arguments[0]));
        if (metin == null) {
            replies.send(sender, "metin.not-found");
            return;
        }
        var view = metin.view();
        replies.send(sender, "info.detail", Map.of("id", view.id(), "metin", view.name(),
                "health", Math.round(view.health()), "max_health", Math.round(view.maxHealth()),
                "percent", Math.round(view.healthPercent()), "world", view.position().world(),
                "locked", view.locked(), "x", view.position().x(), "y", view.position().y(), "z", view.position().z()));
    }

    void debug(CommandSender sender, String[] arguments) {
        noArguments(arguments);
        replies.send(sender, "debug.status", Map.of("platform", scheduler.platform(), "active", registry.size(),
                "pending", lifecycle.pendingCount(), "definitions", configurations.get().metins().size(),
                "loot", configurations.get().loot().size(), "waves", configurations.get().waves().size()));
    }

    private void noArguments(String[] arguments) {
        if (arguments.length != 0) {
            throw new IllegalArgumentException("unexpected arguments");
        }
    }
}
