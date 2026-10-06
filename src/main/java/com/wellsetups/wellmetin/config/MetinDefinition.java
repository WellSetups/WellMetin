package com.wellsetups.wellmetin.config;

import com.wellsetups.wellmetin.api.ItemReference;
import com.wellsetups.wellmetin.loot.RewardMode;
import com.wellsetups.wellmetin.metin.Thresholds;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record MetinDefinition(String id, String name, List<String> description, int level, double health,
                              Visual visual, Combat combat, Display display, Rewards rewards,
                              List<Phase> phases, Rules rules, Spawn spawn,
                              List<Action> onSpawn, List<Action> onDestroy, Map<String, String> tags) {
    public MetinDefinition {
        description = List.copyOf(description);
        phases = List.copyOf(phases);
        onSpawn = List.copyOf(onSpawn);
        onDestroy = List.copyOf(onDestroy);
        tags = Map.copyOf(tags);
    }

    public enum HitboxType { IRON_GOLEM, COW, SLIME }
    public record Hitbox(HitboxType type, int slimeSize, boolean baby) { }
    public record Visual(ItemReference item, float scale, Hitbox hitbox, double offsetY) { }
    public record Combat(double multiplier, Set<String> weapons, List<ItemReference> customWeapons,
                         int minimumEnchantmentLevel, int minimumAttackIntervalTicks) {
        public Combat {
            weapons = Set.copyOf(weapons);
            customWeapons = List.copyOf(customWeapons);
        }
    }
    public record Display(boolean enabled, List<String> lines, long updateTicks, double offsetY,
                          int barSegments, String filled, String empty) {
        public Display { lines = List.copyOf(lines); }
    }
    public record Rewards(RewardMode mode, double minimumContribution, List<Checkpoint> checkpoints,
                          String destructionTable) {
        public Rewards { checkpoints = List.copyOf(checkpoints); }
    }
    public record Checkpoint(String id, double percent, String table) implements Thresholds.Threshold { }
    public record Phase(String id, double percent, boolean once, List<Action> actions)
            implements Thresholds.Threshold {
        public Phase { actions = List.copyOf(actions); }
    }
    public record Rules(String permission, Set<String> worlds, Set<String> deniedWorlds, Set<String> biomes) {
        public Rules {
            worlds = Set.copyOf(worlds);
            deniedWorlds = Set.copyOf(deniedWorlds);
            biomes = Set.copyOf(biomes);
        }
        public boolean permitsWorld(String world) {
            return !deniedWorlds.contains(world) && (worlds.isEmpty() || worlds.contains(world));
        }
    }
    public enum SpawnMode { STATIC, RANDOM_REGION, COMMAND_ONLY }
    public record Spawn(Set<SpawnMode> modes, long respawnSeconds, int maximumActive,
                        boolean safeGround, boolean allowLiquids) {
        public Spawn { modes = Set.copyOf(modes); }
    }
    public enum ActionType { WAVE, SOUND, PARTICLE, CONSOLE_COMMAND, BROADCAST, NEARBY_MESSAGE,
        IMMUNITY, VISUAL, DAMAGE_MULTIPLIER, LOOT }
    public record Action(ActionType type, String value, double amount, long durationTicks,
                         long delayTicks, boolean lock, double radius) { }
}
