package com.wellsetups.wellmetin.lang;

import java.util.Map;
import org.bukkit.command.CommandSender;

public final class Messages {
    private final Map<String, String> messages;

    public Messages(Map<String, String> messages) {
        this.messages = Map.copyOf(messages);
    }

    public String text(String key, Map<String, ?> values) {
        return format(messages.getOrDefault(key, key), values);
    }

    public void send(CommandSender sender, String key, Map<String, ?> values) {
        sender.sendMessage(text(key, values));
    }

    public boolean contains(String key) { return messages.containsKey(key); }
    public Map<String, String> values() { return messages; }

    public static String format(String template, Map<String, ?> values) {
        String result = template;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", String.valueOf(entry.getValue()));
        }
        return ColorParser.parse(result);
    }
}
