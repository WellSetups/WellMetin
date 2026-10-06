package com.wellsetups.wellmetin.platform;

import com.wellsetups.wellmetin.visual.OwnedEntities;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Resolves renamed public events once, without linking the core to removed API classes. */
public final class CarrierEvents {
    private CarrierEvents() { }

    public static void register(PluginManager manager, Plugin plugin, OwnedEntities tags) {
        try {
            Class<? extends Event> mount = eventClass("org.bukkit.event.entity.EntityMountEvent",
                    "org.spigotmc.event.entity.EntityMountEvent");
            Class<? extends Event> split = eventClass("org.bukkit.event.entity.CubeMobSplitEvent",
                    "org.bukkit.event.entity.SlimeSplitEvent");
            MethodHandle getMount = MethodHandles.publicLookup().unreflect(mount.getMethod("getMount"));
            Listener listener = new Listener() { };
            manager.registerEvent(mount, listener, EventPriority.HIGHEST, (ignored, event) -> {
                if (event instanceof EntityEvent entityEvent && event instanceof Cancellable cancellable) {
                    if (tags.carrier(entityEvent.getEntity()) || tags.carrier(mountOf(getMount, event))) {
                        cancellable.setCancelled(true);
                    }
                }
            }, plugin, true);
            manager.registerEvent(split, listener, EventPriority.HIGHEST, (ignored, event) -> {
                if (event instanceof EntityEvent entityEvent && event instanceof Cancellable cancellable
                        && tags.owned(entityEvent.getEntity())) {
                    cancellable.setCancelled(true);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Required public entity protection events are unavailable", failure);
        }
    }

    private static Class<? extends Event> eventClass(String current, String legacy) throws ClassNotFoundException {
        ClassLoader loader = CarrierEvents.class.getClassLoader();
        try {
            return Class.forName(current, false, loader).asSubclass(Event.class);
        } catch (ClassNotFoundException absent) {
            return Class.forName(legacy, false, loader).asSubclass(Event.class);
        }
    }

    private static Entity mountOf(MethodHandle method, Event event) throws EventException {
        try {
            return (Entity) method.invoke(event);
        } catch (Throwable failure) {
            throw new EventException(failure);
        }
    }
}
