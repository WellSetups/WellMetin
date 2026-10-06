package com.wellsetups.wellmetin;

import com.wellsetups.wellmetin.api.WellMetinAPI;
import com.wellsetups.wellmetin.api.WellMetinService;
import com.wellsetups.wellmetin.command.Commands;
import com.wellsetups.wellmetin.command.CommandRouter;
import com.wellsetups.wellmetin.config.ConfigurationService;
import com.wellsetups.wellmetin.currency.CoinService;
import com.wellsetups.wellmetin.damage.DamagePipeline;
import com.wellsetups.wellmetin.damage.EncounterCombat;
import com.wellsetups.wellmetin.damage.WeaponRules;
import com.wellsetups.wellmetin.integration.Integrations;
import com.wellsetups.wellmetin.integration.ItemProviders;
import com.wellsetups.wellmetin.loot.RewardPlanner;
import com.wellsetups.wellmetin.loot.RewardService;
import com.wellsetups.wellmetin.metin.CombatListener;
import com.wellsetups.wellmetin.metin.EncounterActions;
import com.wellsetups.wellmetin.metin.EncounterPersistence;
import com.wellsetups.wellmetin.metin.LifecycleListener;
import com.wellsetups.wellmetin.metin.MetinLifecycle;
import com.wellsetups.wellmetin.metin.MetinRegistry;
import com.wellsetups.wellmetin.mob.MobWaves;
import com.wellsetups.wellmetin.persistence.SavedMetin;
import com.wellsetups.wellmetin.persistence.SqliteStore;
import com.wellsetups.wellmetin.platform.PlayerDirectory;
import com.wellsetups.wellmetin.platform.CarrierEvents;
import com.wellsetups.wellmetin.platform.Scheduler;
import com.wellsetups.wellmetin.spawn.SpawnCoordinator;
import com.wellsetups.wellmetin.visual.DisplayVisuals;
import com.wellsetups.wellmetin.visual.OwnedEntities;
import com.wellsetups.wellmetin.visual.VanillaHitbox;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

final class PluginRuntime implements AutoCloseable {
    private final PlayerDirectory players;
    private final RewardService rewards;
    private final MobWaves waves;
    private final MetinLifecycle lifecycle;
    private final SpawnCoordinator spawns;
    private final CoinService coins;
    private final MetinRegistry registry;
    private final OwnedEntities tags;
    private final EncounterCombat combat;
    private final WellMetinService api;
    private final CommandRouter commands;
    private final ItemProviders items;

    PluginRuntime(WellMetinPlugin plugin, ConfigurationService configuration, Scheduler scheduler,
                   SqliteStore store) {
        Consumer<String> errors = error -> plugin.getLogger().severe(configuration.get().messages()
                .text("log.operation-failed", Map.of("error", error)));
        items = new ItemProviders();
        Integrations.register(items, scheduler.platform().equals("Folia"), configuration.get().messages(), plugin.getLogger());
        players = new PlayerDirectory(scheduler);
        registry = new MetinRegistry();
        tags = new OwnedEntities(plugin);
        var planner = new RewardPlanner();
        var damage = new DamagePipeline();
        coins = new CoinService(store, () -> configuration.get().settings().currencyEnabled(), errors);
        rewards = new RewardService(store, scheduler, players, items, coins, configuration::get, errors);
        waves = new MobWaves(scheduler, tags, items, store, planner, rewards, errors);
        var actions = new EncounterActions(scheduler, players, waves, planner, errors);
        var persistence = new EncounterPersistence(store, rewards, errors);
        lifecycle = new MetinLifecycle(scheduler, configuration::get, registry, new VanillaHitbox(tags),
                new DisplayVisuals(tags, items), persistence, store, actions, waves, errors);
        combat = new EncounterCombat(scheduler, damage, new WeaponRules(items), actions, planner, persistence, lifecycle, errors);
        api = new WellMetinService(configuration::get, registry, lifecycle, coins, damage, items);
        commands = Commands.create(configuration, scheduler, players, api, coins, registry, lifecycle, errors);
        spawns = new SpawnCoordinator(configuration::get, lifecycle, players, scheduler);
    }

    void start(WellMetinPlugin plugin, ConfigurationService configuration, Scheduler scheduler,
               List<SavedMetin> saved, Map<String, Long> cooldowns) {
        var root = java.util.Objects.requireNonNull(plugin.getCommand("wellmetin"));
        root.setExecutor(commands);
        root.setTabCompleter(commands);
        Bukkit.getPluginManager().registerEvents(new CombatListener(registry, tags, combat, waves), plugin);
        Bukkit.getPluginManager().registerEvents(new LifecycleListener(registry, lifecycle, tags, waves, players, rewards), plugin);
        CarrierEvents.register(Bukkit.getPluginManager(), plugin, tags);
        Bukkit.getServicesManager().register(WellMetinAPI.class, api, plugin, ServicePriority.Normal);
        lifecycle.restore(saved, cooldowns);
        Bukkit.getOnlinePlayers().forEach(player -> scheduler.entity(player, () -> {
            players.join(player);
            rewards.pump(player.getUniqueId());
        }, () -> { }, 1, 0));
        scheduler.global(spawns::tick, 20, 100);
        scheduler.global(waves::maintenance, 20, 40);
        scheduler.global(() -> players.ids().forEach(rewards::pump), 100, 100);
        plugin.getLogger().info(configuration.get().messages().text("log.ready", Map.of(
                "version", plugin.getDescription().getVersion(), "platform", scheduler.platform(),
                "minecraft", Bukkit.getBukkitVersion(), "metins", configuration.get().metins().size(),
                "loot", configuration.get().loot().size(), "integrations", String.join(", ", items.names()))));
    }

    @Override
    public void close() {
        spawns.close();
        rewards.close();
        waves.shutdown();
        lifecycle.shutdown();
        players.close();
        coins.close();
    }
}
