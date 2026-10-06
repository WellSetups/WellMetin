package com.wellsetups.wellmetin.platform;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** Keeps pre-spawn initialization across Bukkit's transition to java.util.function.Consumer. */
public final class WorldSpawner {
    private static final Binding BINDING = binding();
    private record Binding(MethodHandle spawn, Class<?> callback) { }

    private WorldSpawner() { }

    private static Binding binding() {
        try {
            Class<?> callback;
            try {
                World.class.getMethod("spawn", Location.class, Class.class, Consumer.class);
                callback = Consumer.class;
            } catch (NoSuchMethodException legacy) {
                callback = Class.forName("org.bukkit.util.Consumer");
            }
            return new Binding(MethodHandles.publicLookup().unreflect(
                    World.class.getMethod("spawn", Location.class, Class.class, callback)), callback);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Required public entity spawn API is unavailable", failure);
        }
    }

    public static <T extends Entity> T spawn(Location location, Class<T> type, Consumer<T> initialize) {
        World world = java.util.Objects.requireNonNull(location.getWorld(), "world");
        Object callback = initialize;
        if (BINDING.callback() != Consumer.class) {
            callback = Proxy.newProxyInstance(BINDING.callback().getClassLoader(), new Class<?>[]{BINDING.callback()},
                    (proxy, method, arguments) -> {
                        return switch (method.getName()) {
                            case "accept" -> {
                                initialize.accept(type.cast(arguments[0]));
                                yield null;
                            }
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "WellMetin spawn initializer";
                            default -> throw new IllegalStateException("Unexpected spawn callback method " + method);
                        };
                    });
        }
        try {
            return type.cast(BINDING.spawn().invoke(world, location, type, callback));
        } catch (Throwable failure) {
            if (failure instanceof Error error) {
                throw error;
            }
            throw failure instanceof RuntimeException runtime ? runtime : new IllegalStateException("Entity spawn failed", failure);
        }
    }
}
