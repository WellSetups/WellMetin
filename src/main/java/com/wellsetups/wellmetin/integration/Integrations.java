package com.wellsetups.wellmetin.integration;

import com.wellsetups.wellmetin.lang.Messages;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class Integrations {
    private Integrations() { }

    public static void register(ItemProviders items, boolean folia, Messages messages, Logger logger) {
        connect(items, folia, messages, logger, "ItemsAdder", "ITEMSADDER",
                "dev.lone.itemsadder.api.CustomStack", "getInstance", "getItemStack", "byItemStack", "getNamespacedID");
        connect(items, folia, messages, logger, "Oraxen", "ORAXEN",
                "io.th0rgal.oraxen.api.OraxenItems", "getItemById", "build", "getIdByItem", null);
        connect(items, folia, messages, logger, "Nexo", "NEXO",
                "com.nexomc.nexo.api.NexoItems", "itemFromId", "build", "idFromItem", null);
    }

    private static void connect(ItemProviders items, boolean folia, Messages messages, Logger logger,
                                String pluginName, String id, String type, String lookup, String build,
                                String identify, String namespacedId) {
        Plugin dependency = Bukkit.getPluginManager().getPlugin(pluginName);
        if (dependency == null || !dependency.isEnabled()) {
            return;
        }
        if (folia) {
            logger.warning(messages.text("log.integration-folia", Map.of("plugin", pluginName)));
            return;
        }
        try {
            items.register(id, new ExternalItemProvider(dependency.getClass().getClassLoader(), type,
                    lookup, build, identify, namespacedId));
        } catch (ReflectiveOperationException | LinkageError failure) {
            logger.warning(messages.text("log.integration-failed", Map.of("plugin", pluginName, "error", failure.toString())));
        }
    }
}
