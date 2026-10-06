package com.wellsetups.wellmetin.spawn;

import com.wellsetups.wellmetin.config.MetinDefinition;
import java.util.Locale;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.World;

public final class SpawnSafety {
    private SpawnSafety() { }

    public static void validate(Location location, MetinDefinition definition) {
        World world = java.util.Objects.requireNonNull(location.getWorld());
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            throw new IllegalArgumentException("target chunk is not loaded");
        }
        if (location.getY() < world.getMinHeight() + 1 || location.getY() + 4 >= world.getMaxHeight()) {
            throw new IllegalArgumentException("target Y is outside the world's usable height");
        }
        if (!definition.rules().permitsWorld(world.getName())) {
            throw new IllegalArgumentException("world is not permitted by this Metin definition");
        }
        if (!definition.rules().biomes().isEmpty()) {
            Keyed biome = location.getBlock().getBiome();
            if (!definition.rules().biomes().contains(biome.getKey().getKey().toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("biome is not permitted by this Metin definition");
            }
        }
        validateGround(location, definition.spawn());
    }

    private static void validateGround(Location location, MetinDefinition.Spawn settings) {
        var feet = location.getBlock();
        var head = location.clone().add(0, 1, 0).getBlock();
        var upper = location.clone().add(0, 2, 0).getBlock();
        var ground = location.clone().add(0, -1, 0).getBlock();
        if (!settings.allowLiquids() && (feet.isLiquid() || head.isLiquid() || ground.isLiquid())) {
            throw new IllegalArgumentException("liquids are not allowed at this spawn");
        }
        if (settings.safeGround() && (!feet.isPassable() || !head.isPassable() || !upper.isPassable()
                || !ground.getType().isSolid())) {
            throw new IllegalArgumentException("spawn needs solid ground and three clear blocks");
        }
    }
}
