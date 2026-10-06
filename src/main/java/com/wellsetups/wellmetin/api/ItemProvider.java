package com.wellsetups.wellmetin.api;

import org.bukkit.inventory.ItemStack;

/** Called on the receiving player's or visual's owning region. Return a fresh stack. */
public interface ItemProvider {
    ItemStack create(ItemReference reference, int amount);

    /** Optional custom weapon matching, also called on the owning region. */
    boolean matches(ItemReference reference, ItemStack stack);
}
