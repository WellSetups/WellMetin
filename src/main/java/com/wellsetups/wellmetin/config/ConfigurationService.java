package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.platform.Scheduler;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigurationService {
    private final AtomicReference<Configuration> current;
    private final AtomicBoolean reloading = new AtomicBoolean();
    private final ConfigurationLoader loader;
    private final Scheduler scheduler;
    private volatile boolean closed;

    public ConfigurationService(Configuration initial, ConfigurationLoader loader, Scheduler scheduler) {
        current = new AtomicReference<>(initial);
        this.loader = loader;
        this.scheduler = scheduler;
    }

    public Configuration get() { return current.get(); }

    public CompletableFuture<Void> reload() {
        if (closed || !reloading.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("reload is unavailable or already in progress"));
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        scheduler.async(() -> {
            try {
                Configuration replacement = loader.load(true).configuration();
                requireRestartUnchanged(replacement.settings());
                if (closed) {
                    throw new IllegalStateException("plugin is stopping");
                }
                scheduler.global(() -> {
                    if (!closed) {
                        current.set(replacement);
                        result.complete(null);
                    } else {
                        result.completeExceptionally(new IllegalStateException("plugin is stopping"));
                    }
                }, 1, 0);
            } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
                result.completeExceptionally(failure);
            }
        });
        result.whenComplete((ignored, failure) -> reloading.set(false));
        return result;
    }

    private void requireRestartUnchanged(Settings next) {
        Settings old = get().settings();
        if (old.databaseQueue() != next.databaseQueue() || old.persistentMetins() != next.persistentMetins()) {
            throw new IllegalArgumentException("storage.queue-capacity and persistent-metins changes require a server restart");
        }
    }

    public void close() { closed = true; }
}
