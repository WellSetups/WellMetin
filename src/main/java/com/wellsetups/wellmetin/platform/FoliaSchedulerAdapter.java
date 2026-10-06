package com.wellsetups.wellmetin.platform;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** Resolves only documented public scheduler APIs; no optional types enter core class signatures. */
final class FoliaSchedulerAdapter implements Scheduler {
    private static final String PACKAGE = "io.papermc.paper.threadedregions.scheduler.";
    private final Plugin plugin;
    private final Object global;
    private final Object regions;
    private final Object asynchronous;
    private final MethodHandle entityScheduler;
    private final MethodHandle ownsEntity;
    private final MethodHandle ownsLocation;
    private final MethodHandle globalOnce;
    private final MethodHandle globalRepeat;
    private final MethodHandle regionOnce;
    private final MethodHandle regionRepeat;
    private final MethodHandle entityOnce;
    private final MethodHandle entityRepeat;
    private final MethodHandle asyncNow;
    private final MethodHandle cancel;
    private final Set<Handle> handles = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        try {
            global = invoke(handle(Bukkit.class, "getGlobalRegionScheduler"), null);
            regions = invoke(handle(Bukkit.class, "getRegionScheduler"), null);
            asynchronous = invoke(handle(Bukkit.class, "getAsyncScheduler"), null);
            entityScheduler = handle(Entity.class, "getScheduler");
            ownsEntity = handle(Bukkit.class, "isOwnedByCurrentRegion", Entity.class);
            ownsLocation = handle(Bukkit.class, "isOwnedByCurrentRegion", Location.class);
            globalOnce = method("GlobalRegionScheduler", "runDelayed", Plugin.class, Consumer.class, long.class);
            globalRepeat = method("GlobalRegionScheduler", "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            regionOnce = method("RegionScheduler", "runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
            regionRepeat = method("RegionScheduler", "runAtFixedRate", Plugin.class, Location.class, Consumer.class, long.class, long.class);
            entityOnce = method("EntityScheduler", "runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            entityRepeat = method("EntityScheduler", "runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class, long.class);
            asyncNow = method("AsyncScheduler", "runNow", Plugin.class, Consumer.class);
            cancel = method("ScheduledTask", "cancel");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Required public Folia scheduler API is unavailable", failure);
        }
    }

    private static MethodHandle method(String type, String name, Class<?>... parameters) throws ReflectiveOperationException {
        return handle(Class.forName(PACKAGE + type), name, parameters);
    }

    private static MethodHandle handle(Class<?> type, String name, Class<?>... parameters) throws ReflectiveOperationException {
        return MethodHandles.publicLookup().unreflect(type.getMethod(name, parameters));
    }

    @Override
    public Task global(Runnable action, long delay, long period) {
        Handle handle = handle();
        Consumer<Object> run = callback(handle, action, period);
        handle.attach(period == 0 ? invoke(globalOnce, global, plugin, run, Math.max(1, delay))
                : invoke(globalRepeat, global, plugin, run, Math.max(1, delay), period));
        return handle;
    }

    @Override
    public Task region(Location location, Runnable action, long delay, long period) {
        Handle handle = handle();
        Consumer<Object> run = callback(handle, action, period);
        handle.attach(period == 0 ? invoke(regionOnce, regions, plugin, location, run, Math.max(1, delay))
                : invoke(regionRepeat, regions, plugin, location, run, Math.max(1, delay), period));
        return handle;
    }

    @Override
    public Task entity(Entity entity, Runnable action, Runnable retired, long delay, long period) {
        Handle handle = handle();
        Consumer<Object> run = callback(handle, action, period);
        Runnable retire = () -> {
            if (handle.finish()) {
                retired.run();
            }
        };
        Object scheduler = invoke(entityScheduler, entity);
        Object scheduled = period == 0 ? invoke(entityOnce, scheduler, plugin, run, retire, Math.max(1, delay))
                : invoke(entityRepeat, scheduler, plugin, run, retire, Math.max(1, delay), period);
        handle.attach(scheduled);
        if (scheduled == null) {
            retire.run();
        }
        return handle;
    }

    @Override
    public Task async(Runnable action) {
        Handle handle = handle();
        handle.attach(invoke(asyncNow, asynchronous, plugin, callback(handle, action, 0)));
        return handle;
    }

    private Handle handle() {
        if (closed.get()) {
            throw new IllegalStateException("scheduler is closed");
        }
        Handle handle = new Handle();
        handles.add(handle);
        return handle;
    }

    private Consumer<Object> callback(Handle handle, Runnable action, long period) {
        return ignored -> {
            if (!handle.finished.get() && !closed.get()) {
                if (period == 0) {
                    handle.finish();
                }
                action.run();
            }
        };
    }

    private static Object invoke(MethodHandle method, Object target, Object... arguments) {
        try {
            if (target == null) {
                return method.invokeWithArguments(arguments);
            }
            var values = new ArrayList<Object>(arguments.length + 1);
            values.add(target);
            values.addAll(Arrays.asList(arguments));
            return method.invokeWithArguments(values);
        } catch (Throwable failure) {
            throw invocationFailure(failure);
        }
    }

    private static RuntimeException invocationFailure(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        return failure instanceof RuntimeException runtime ? runtime : new IllegalStateException("Folia scheduler failed", failure);
    }

    @Override public boolean owns(Entity entity) {
        try {
            return (boolean) ownsEntity.invokeExact(entity);
        } catch (Throwable failure) {
            throw invocationFailure(failure);
        }
    }
    @Override public boolean owns(Location location) {
        try {
            return (boolean) ownsLocation.invokeExact(location);
        } catch (Throwable failure) {
            throw invocationFailure(failure);
        }
    }
    @Override public String platform() { return "Folia"; }

    @Override
    public void close() {
        closed.set(true);
        handles.forEach(Handle::cancel);
        handles.clear();
    }

    private final class Handle implements Task {
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile Object delegate;

        void attach(Object task) {
            delegate = task;
            if (finished.get() && task != null) {
                invoke(cancel, task);
            }
        }

        boolean finish() {
            handles.remove(this);
            return finished.compareAndSet(false, true);
        }

        @Override
        public void cancel() {
            finish();
            Object task = delegate;
            if (task != null) {
                invoke(cancel, task);
            }
        }
    }
}
