package com.wellsetups.wellmetin.config;

import java.util.List;
import java.util.Map;

public record CommandSettings(Map<String, Command> commands) {
    public CommandSettings { commands = Map.copyOf(commands); }
    public record Command(String id, String name, List<String> aliases, String permission,
                          boolean enabled, String descriptionKey, String usageKey) {
        public Command { aliases = List.copyOf(aliases); }
    }
}
