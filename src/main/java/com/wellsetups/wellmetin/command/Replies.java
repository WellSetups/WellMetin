package com.wellsetups.wellmetin.command;

import com.wellsetups.wellmetin.config.Configuration;
import com.wellsetups.wellmetin.platform.PlayerDirectory;
import com.wellsetups.wellmetin.platform.Scheduler;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Replies {
    private final Scheduler scheduler;
    private final PlayerDirectory players;
    private final Supplier<Configuration> configuration;

    public Replies(Scheduler scheduler, PlayerDirectory players, Supplier<Configuration> configuration) {
        this.scheduler = scheduler;
        this.players = players;
        this.configuration = configuration;
    }

    public void send(CommandSender sender, String key, Map<String, ?> values) {
        String text = configuration.get().messages().text(key, values);
        if (sender instanceof Player player) {
            if (scheduler.owns(player)) {
                player.sendMessage(text);
            } else {
                players.execute(player.getUniqueId(), recipient -> recipient.sendMessage(text), () -> { });
            }
        } else {
            scheduler.global(() -> sender.sendMessage(text), 1, 0);
        }
    }

    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }
}
