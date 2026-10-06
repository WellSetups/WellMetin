package com.wellsetups.wellmetin.damage;

import com.wellsetups.wellmetin.config.MetinDefinition;
import com.wellsetups.wellmetin.integration.ItemProviders;
import org.bukkit.inventory.ItemStack;

public final class WeaponRules {
    private final ItemProviders items;

    public WeaponRules(ItemProviders items) { this.items = items; }

    public boolean allows(MetinDefinition.Combat settings, ItemStack weapon) {
        int highest = weapon.getEnchantments().values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (highest < settings.minimumEnchantmentLevel()) {
            return false;
        }
        if (settings.weapons().contains(weapon.getType().name())) {
            return true;
        }
        return settings.customWeapons().stream()
                .filter(item -> items.available(item.provider())).anyMatch(item -> items.matches(item, weapon));
    }
}
