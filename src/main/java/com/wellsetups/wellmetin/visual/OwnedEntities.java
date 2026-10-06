package com.wellsetups.wellmetin.visual;

import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class OwnedEntities {
    private final NamespacedKey instance;
    private final NamespacedKey role;
    private final NamespacedKey level;

    public OwnedEntities(Plugin plugin) {
        instance = new NamespacedKey(plugin, "instance");
        role = new NamespacedKey(plugin, "role");
        level = new NamespacedKey(plugin, "level");
    }

    public void tag(Entity entity, UUID id, String value) {
        entity.getPersistentDataContainer().set(instance, PersistentDataType.STRING, id.toString());
        entity.getPersistentDataContainer().set(role, PersistentDataType.STRING, value);
        // SQLite owns lifecycle; native entity saves would otherwise resurrect stale hitboxes after a crash.
        entity.setPersistent(false);
    }

    public boolean owned(Entity entity) {
        return entity.getPersistentDataContainer().has(instance, PersistentDataType.STRING);
    }

    public boolean carrier(Entity entity) {
        return "carrier".equals(entity.getPersistentDataContainer().get(role, PersistentDataType.STRING));
    }

    public boolean guardian(Entity entity) {
        return "guardian".equals(entity.getPersistentDataContainer().get(role, PersistentDataType.STRING));
    }

    public void level(Entity entity, int value) {
        entity.getPersistentDataContainer().set(level, PersistentDataType.INTEGER, value);
    }
}
