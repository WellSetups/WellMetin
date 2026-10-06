package com.wellsetups.wellmetin.command;

import java.util.List;
import org.bukkit.command.CommandSender;

interface Subcommand {
    void execute(CommandSender sender, String[] arguments);
    default List<String> complete(CommandSender sender, String[] arguments) { return List.of(); }
}
