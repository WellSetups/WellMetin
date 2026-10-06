package com.wellsetups.wellmetin.mob;

import com.wellsetups.wellmetin.config.WaveDefinition;
import com.wellsetups.wellmetin.integration.ItemProviders;
import com.wellsetups.wellmetin.lang.ColorParser;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class MobEquipment {
    private final ItemProviders items;

    MobEquipment(ItemProviders items) { this.items = items; }

    @SuppressWarnings("deprecation")
    void configure(LivingEntity entity, WaveDefinition.Mob settings) {
        entity.setRemoveWhenFarAway(false);
        entity.setCanPickupItems(false);
        entity.setAI(settings.ai());
        entity.setGlowing(settings.glowing());
        if (!settings.name().isEmpty()) {
            entity.setCustomName(ColorParser.parse(settings.name()));
            entity.setCustomNameVisible(true);
        }
        AttributeInstance health = attribute(entity, "max_health");
        if (health != null) {
            health.setBaseValue(Math.min(1024, health.getBaseValue() * settings.healthMultiplier()));
            entity.setHealth(health.getValue());
        }
        if (entity instanceof Mob mob && !settings.vanillaDrops()) {
            mob.setLootTable(null);
        }
        if (entity.getEquipment() != null) {
            settings.equipment().forEach((slot, item) -> {
                EquipmentSlot equipmentSlot = EquipmentSlot.valueOf(slot);
                entity.getEquipment().setItem(equipmentSlot, items.create(item, 1));
            });
            drops(entity.getEquipment(), settings.vanillaDrops() ? 1 : 0);
        }
        for (WaveDefinition.Effect effect : settings.effects()) {
            PotionEffectType type = PotionEffectType.getByName(effect.type());
            if (type != null) {
                entity.addPotionEffect(new PotionEffect(type, effect.ticks(), effect.amplifier()));
            }
        }
    }

    private void drops(org.bukkit.inventory.EntityEquipment equipment, float chance) {
        equipment.setItemInMainHandDropChance(chance);
        equipment.setItemInOffHandDropChance(chance);
        equipment.setHelmetDropChance(chance);
        equipment.setChestplateDropChance(chance);
        equipment.setLeggingsDropChance(chance);
        equipment.setBootsDropChance(chance);
    }

    private AttributeInstance attribute(LivingEntity entity, String name) {
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(name));
        if (attribute == null) {
            attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + name));
        }
        return attribute == null ? null : entity.getAttribute(attribute);
    }
}
