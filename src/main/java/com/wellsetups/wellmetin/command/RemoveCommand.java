package com.wellsetups.wellmetin.command;

import com.wellsetups.wellmetin.api.WellMetinAPI;
import com.wellsetups.wellmetin.metin.MetinRegistry;
import com.wellsetups.wellmetin.platform.Locations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class RemoveCommand implements Subcommand {
    private final WellMetinAPI api;
    private final MetinRegistry registry;
    private final Replies replies;

    RemoveCommand(WellMetinAPI api, MetinRegistry registry, Replies replies) {
        this.api = api;
        this.registry = registry;
        this.replies = replies;
    }

    @Override
    public void execute(CommandSender sender, String[] arguments) {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("expected UUID or nearest");
        }
        UUID id;
        if (arguments[0].equalsIgnoreCase("nearest")) {
            if (!(sender instanceof Player player)) {
                replies.send(sender, "command.player-required");
                return;
            }
            var nearest = registry.nearest(Locations.snapshot(player.getLocation()), 64);
            if (nearest == null) {
                replies.send(sender, "metin.not-found");
                return;
            }
            id = nearest.id();
        } else {
            id = Arguments.uuid(arguments[0]);
        }
        api.remove(id).whenComplete((removed, failure) -> {
            if (failure != null) {
                replies.send(sender, "command.failed");
            } else {
                replies.send(sender, removed ? "remove.success" : "metin.not-found", Map.of("id", id));
            }
        });
    }

    @Override public List<String> complete(CommandSender sender, String[] arguments) {
        if (arguments.length != 1) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        result.add("nearest");
        registry.views().forEach(view -> result.add(view.id().toString()));
        return List.copyOf(result);
    }
}
