package com.wellsetups.wellmetin.loot;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class InventoryRewards {
    private InventoryRewards() { }

    static List<ItemStack> prepare(ItemStack prototype, int amount) {
        if (amount < 1 || amount > 2304) {
            throw new IllegalArgumentException("item reward amount must be between 1 and 2304");
        }
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = prototype.clone();
            int size = Math.min(remaining, stack.getMaxStackSize());
            stack.setAmount(size);
            remaining -= size;
            stacks.add(stack);
        }
        return List.copyOf(stacks);
    }

    static boolean fits(Player player, ItemStack prototype, int amount) {
        long space = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                space += prototype.getMaxStackSize();
            } else if (slot.isSimilar(prototype)) {
                space += Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            }
        }
        return space >= amount;
    }
}
