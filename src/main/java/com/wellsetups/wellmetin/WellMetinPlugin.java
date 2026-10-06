package com.wellsetups.wellmetin;

import com.wellsetups.wellmetin.config.ConfigurationLoader;
import com.wellsetups.wellmetin.config.ConfigurationService;
import com.wellsetups.wellmetin.config.DefaultResources;
import com.wellsetups.wellmetin.lang.Messages;
import com.wellsetups.wellmetin.persistence.SavedMetin;
import com.wellsetups.wellmetin.persistence.SqliteStore;
import com.wellsetups.wellmetin.platform.Scheduler;
import com.wellsetups.wellmetin.platform.Schedulers;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class WellMetinPlugin extends JavaPlugin {
    private record RestoreData(List<SavedMetin> metins, Map<String, Long> cooldowns) { }
    private volatile boolean stopping;
    private volatile SqliteStore store;
    private Scheduler scheduler;
    private ConfigurationService configurations;
    private PluginRuntime runtime;
    private Messages english;

    @Override
    public void onEnable() {
        english = DefaultResources.english();
        scheduler = Schedulers.create(this);
        java.util.Objects.requireNonNull(getCommand("wellmetin")).setExecutor((sender, command, label, arguments) -> {
            english.send(sender, "command.starting", Map.of());
            return true;
        });
        scheduler.async(this::initialize);
    }

    private void initialize() {
        try {
            var directory = getDataFolder().toPath();
            DefaultResources.install(directory);
            ConfigurationLoader loader = new ConfigurationLoader(directory);
            ConfigurationLoader.Result loaded = loader.load(false);
            loaded.warnings().forEach(warning -> getLogger().warning(loaded.configuration().messages()
                    .text("log.configuration-warning", Map.of("error", warning))));
            SqliteStore database = new SqliteStore(directory.resolve("wellmetin.db"), loaded.configuration().settings().databaseQueue(),
                    error -> getLogger().severe(english.text("log.operation-failed", Map.of("error", error))));
            store = database;
            if (stopping) {
                database.close();
                return;
            }
            database.initialize().thenCompose(ignored -> database.restore())
                    .thenCombine(database.cooldowns(), RestoreData::new).whenComplete((restored, failure) -> {
                        if (failure != null) {
                            failed(failure);
                        } else if (!stopping) {
                            scheduler.global(() -> activate(loader, loaded, restored), 1, 0);
                        }
                    });
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            failed(failure);
        }
    }

    private void activate(ConfigurationLoader loader, ConfigurationLoader.Result loaded, RestoreData restored) {
        if (stopping) {
            return;
        }
        configurations = new ConfigurationService(loaded.configuration(), loader, scheduler);
        try {
            runtime = new PluginRuntime(this, configurations, scheduler, store);
            runtime.start(this, configurations, scheduler,
                    loaded.configuration().settings().persistentMetins() ? restored.metins() : List.of(), restored.cooldowns());
        } catch (RuntimeException failure) {
            failed(failure);
        }
    }

    private void failed(Throwable failure) {
        getLogger().severe(english.text("log.startup-failed", Map.of("error", failure.toString())));
        if (!stopping) {
            scheduler.global(() -> Bukkit.getPluginManager().disablePlugin(this), 1, 0);
        }
    }

    @Override
    public void onDisable() {
        stopping = true;
        if (configurations != null) {
            configurations.close();
        }
        if (runtime != null) {
            runtime.close();
        }
        if (scheduler != null) {
            scheduler.close();
        }
        if (store != null) {
            store.close();
        }
        HandlerList.unregisterAll(this);
        Bukkit.getServicesManager().unregisterAll(this);
    }
}
