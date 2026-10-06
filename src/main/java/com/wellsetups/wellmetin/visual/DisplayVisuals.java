package com.wellsetups.wellmetin.visual;

import com.wellsetups.wellmetin.api.ItemReference;
import com.wellsetups.wellmetin.api.MetinView;
import com.wellsetups.wellmetin.config.MetinDefinition;
import com.wellsetups.wellmetin.integration.ItemProviders;
import com.wellsetups.wellmetin.lang.Messages;
import com.wellsetups.wellmetin.platform.WorldSpawner;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;

public final class DisplayVisuals implements VisualProvider {
    private final OwnedEntities tags;
    private final ItemProviders items;

    public DisplayVisuals(OwnedEntities tags, ItemProviders items) {
        this.tags = tags;
        this.items = items;
    }

    @Override
    public Visual spawn(UUID instance, Location location, MetinDefinition definition) {
        var stack = items.create(definition.visual().item(), 1);
        ItemDisplay display = WorldSpawner.spawn(
                location.clone().add(0, definition.visual().offsetY(), 0), ItemDisplay.class, entity -> {
                    tags.tag(entity, instance, "visual");
                    entity.setItemStack(stack);
                    entity.setGravity(false);
                    entity.setInvulnerable(true);
                    Transformation transformation = entity.getTransformation();
                    transformation.getScale().set(definition.visual().scale());
                    entity.setTransformation(transformation);
                });
        try {
            TextDisplay text = definition.display().enabled() ? WorldSpawner.spawn(
                    location.clone().add(0, definition.display().offsetY(), 0), TextDisplay.class, entity -> {
                        tags.tag(entity, instance, "health");
                        entity.setBillboard(Display.Billboard.CENTER);
                        entity.setGravity(false);
                        entity.setInvulnerable(true);
                        entity.setSeeThrough(false);
                        entity.setShadowed(true);
                    }) : null;
            return new Handle(display, text, definition);
        } catch (RuntimeException failure) {
            display.remove();
            throw failure;
        }
    }

    private final class Handle implements Visual {
        private final ItemDisplay display;
        private final TextDisplay text;
        private final MetinDefinition definition;

        Handle(ItemDisplay display, TextDisplay text, MetinDefinition definition) {
            this.display = display;
            this.text = text;
            this.definition = definition;
        }

        @Override
        public List<UUID> entityIds() {
            return text == null ? List.of(display.getUniqueId()) : List.of(display.getUniqueId(), text.getUniqueId());
        }

        @Override
        public void update(MetinView metin) {
            if (text == null || !text.isValid()) {
                return;
            }
            MetinDefinition.Display settings = definition.display();
            int filled = (int) Math.round(metin.healthPercent() * settings.barSegments() / 100);
            String bar = settings.filled().repeat(filled) + settings.empty().repeat(settings.barSegments() - filled);
            Map<String, Object> values = Map.of("metin", metin.name(), "level", definition.level(),
                    "health", Math.round(metin.health()), "max_health", Math.round(metin.maxHealth()),
                    "percent", Math.round(metin.healthPercent()), "bar", bar);
            text.setText(Messages.format(String.join("\n", settings.lines()), values));
        }

        @Override public void change(ItemReference item) { display.setItemStack(items.create(item, 1)); }

        @Override
        public void remove() {
            display.remove();
            if (text != null) {
                text.remove();
            }
        }
    }
}
