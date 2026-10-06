package com.wellsetups.wellmetin.command;

import com.wellsetups.wellmetin.api.Position;
import com.wellsetups.wellmetin.api.WellMetinAPI;
import com.wellsetups.wellmetin.config.Configuration;
import com.wellsetups.wellmetin.platform.Locations;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class SpawnCommand implements Subcommand {
    private final WellMetinAPI api;
    private final Supplier<Configuration> configuration;
    private final Replies replies;

    SpawnCommand(WellMetinAPI api, Supplier<Configuration> configuration, Replies replies) {
        this.api = api;
        this.configuration = configuration;
        this.replies = replies;
    }

    @Override
    public void execute(CommandSender sender, String[] arguments) {
        if (arguments.length != 1 && arguments.length != 5) {
            throw new IllegalArgumentException("expected Metin ID and optional world x y z");
        }
        Position position;
        if (arguments.length == 5) {
            position = Arguments.position(arguments, 1);
        } else if (sender instanceof Player player) {
            position = Locations.snapshot(player.getLocation());
        } else {
            replies.send(sender, "command.location-required");
            return;
        }
        api.spawn(arguments[0], position, sender instanceof Player player ? player.getUniqueId() : null)
                .whenComplete((metin, failure) -> {
                    if (failure == null) {
                        replies.send(sender, "spawn.success", Map.of("metin", metin.name(), "id", metin.id()));
                    } else {
                        replies.send(sender, "spawn.failed");
                    }
                });
    }

    @Override public List<String> complete(CommandSender sender, String[] arguments) {
        return arguments.length == 1 ? List.copyOf(configuration.get().metins().keySet()) : List.of();
    }
}
