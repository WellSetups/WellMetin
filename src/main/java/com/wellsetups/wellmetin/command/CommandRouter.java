package com.wellsetups.wellmetin.command;

import com.wellsetups.wellmetin.config.CommandSettings;
import com.wellsetups.wellmetin.config.Configuration;
import com.wellsetups.wellmetin.platform.Scheduler;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class CommandRouter implements TabExecutor {
    private final Supplier<Configuration> configuration;
    private final Map<String, Subcommand> handlers;
    private final Replies replies;
    private final Scheduler scheduler;

    public CommandRouter(Supplier<Configuration> configuration, Map<String, Subcommand> handlers,
                          Replies replies, Scheduler scheduler) {
        this.configuration = configuration;
        this.handlers = Map.copyOf(handlers);
        this.replies = replies;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        String requested = arguments.length == 0 ? "help" : arguments[0].toLowerCase(Locale.ROOT);
        CommandSettings.Command selected = arguments.length == 0
                ? configuration.get().commands().commands().get("help") : find(requested);
        if (selected == null || !selected.enabled()) {
            replies.send(sender, "command.unknown");
            return true;
        }
        String[] remaining = arguments.length == 0 ? new String[0] : Arrays.copyOfRange(arguments, 1, arguments.length);
        Runnable execute = () -> execute(sender, selected, remaining);
        if (sender instanceof Player player && !scheduler.owns(player)) {
            scheduler.entity(player, execute, () -> { }, 1, 0);
        } else {
            execute.run();
        }
        return true;
    }

    private void execute(CommandSender sender, CommandSettings.Command command, String[] arguments) {
        if (!allowed(sender, command)) {
            replies.send(sender, "command.no-permission");
            return;
        }
        try {
            handlers.get(command.id()).execute(sender, arguments);
        } catch (IllegalArgumentException failure) {
            replies.send(sender, command.usageKey());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        if (sender instanceof Player player && !scheduler.owns(player)) {
            return List.of();
        }
        if (arguments.length <= 1) {
            String prefix = arguments.length == 0 ? "" : arguments[0].toLowerCase(Locale.ROOT);
            return configuration.get().commands().commands().values().stream().filter(item -> allowed(sender, item))
                    .map(CommandSettings.Command::name).filter(name -> name.startsWith(prefix)).sorted().toList();
        }
        CommandSettings.Command selected = find(arguments[0].toLowerCase(Locale.ROOT));
        if (selected == null || !allowed(sender, selected)) {
            return List.of();
        }
        String[] remaining = Arrays.copyOfRange(arguments, 1, arguments.length);
        String prefix = remaining[remaining.length - 1].toLowerCase(Locale.ROOT);
        return handlers.get(selected.id()).complete(sender, remaining).stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().limit(100).toList();
    }

    private CommandSettings.Command find(String label) {
        return configuration.get().commands().commands().values().stream()
                .filter(command -> command.name().equals(label) || command.aliases().contains(label))
                .findFirst().orElse(null);
    }

    private boolean allowed(CommandSender sender, CommandSettings.Command command) {
        return command.enabled() && (command.permission().isEmpty() || sender.hasPermission(command.permission()));
    }
}
