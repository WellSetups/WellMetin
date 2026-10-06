package com.wellsetups.wellmetin.integration;

import com.wellsetups.wellmetin.api.ItemProvider;
import com.wellsetups.wellmetin.api.ItemReference;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemProviders {
    private final ConcurrentHashMap<String, ItemProvider> providers = new ConcurrentHashMap<>();

    public ItemProviders() { providers.put("VANILLA", new Vanilla()); }

    public AutoCloseable register(String id, ItemProvider provider) {
        String key = id.toUpperCase(Locale.ROOT);
        if (providers.putIfAbsent(key, java.util.Objects.requireNonNull(provider)) != null) {
            throw new IllegalArgumentException("item provider already registered: " + key);
        }
        return () -> providers.remove(key, provider);
    }

    public ItemStack create(ItemReference item, int amount) {
        ItemStack stack = provider(item).create(item, amount);
        if (stack == null || stack.getType().isAir()) {
            throw new IllegalArgumentException("item provider returned no item: " + item.provider() + ":" + item.id());
        }
        return stack;
    }

    public boolean matches(ItemReference item, ItemStack stack) { return provider(item).matches(item, stack); }
    public boolean available(String id) { return providers.containsKey(id); }
    public Set<String> names() { return Set.copyOf(providers.keySet()); }

    private ItemProvider provider(ItemReference item) {
        ItemProvider provider = providers.get(item.provider());
        if (provider == null) {
            throw new IllegalArgumentException("item provider is unavailable: " + item.provider());
        }
        return provider;
    }

    private static final class Vanilla implements ItemProvider {
        @Override
        public ItemStack create(ItemReference reference, int amount) {
            Material material = Material.matchMaterial(reference.id());
            if (material == null || !material.isItem() || material.isAir()) {
                throw new IllegalArgumentException("unknown vanilla item: " + reference.id());
            }
            ItemStack stack = new ItemStack(material, amount);
            if (reference.customModelData() > 0) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(reference.customModelData());
                    stack.setItemMeta(meta);
                }
            }
            return stack;
        }

        @Override
        public boolean matches(ItemReference reference, ItemStack stack) {
            if (stack.getType() != Material.matchMaterial(reference.id())) {
                return false;
            }
            ItemMeta meta = stack.getItemMeta();
            return reference.customModelData() == 0 || (meta != null && meta.hasCustomModelData()
                    && meta.getCustomModelData() == reference.customModelData());
        }
    }
}
