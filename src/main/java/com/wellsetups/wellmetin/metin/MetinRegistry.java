package com.wellsetups.wellmetin.metin;

import com.wellsetups.wellmetin.api.MetinView;
import com.wellsetups.wellmetin.api.Position;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MetinRegistry {
    private final ConcurrentHashMap<UUID, ActiveMetin> instances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ActiveMetin> entities = new ConcurrentHashMap<>();

    public boolean add(ActiveMetin metin) {
        if (instances.putIfAbsent(metin.id(), metin) != null) {
            return false;
        }
        entities.put(metin.carrier().getUniqueId(), metin);
        metin.visual().entityIds().forEach(id -> entities.put(id, metin));
        return true;
    }

    public void remove(ActiveMetin metin) {
        instances.remove(metin.id(), metin);
        entities.remove(metin.carrier().getUniqueId(), metin);
        metin.visual().entityIds().forEach(id -> entities.remove(id, metin));
    }

    public ActiveMetin byEntity(UUID id) { return entities.get(id); }
    public ActiveMetin get(UUID id) { return instances.get(id); }
    public Collection<ActiveMetin> active() { return List.copyOf(instances.values()); }
    public List<MetinView> views() { return instances.values().stream().map(ActiveMetin::view).toList(); }
    public int size() { return instances.size(); }
    public void clear() {
        instances.clear();
        entities.clear();
    }
    public long count(String definition) {
        return instances.values().stream().filter(metin -> metin.definition().id().equals(definition)).count();
    }
    public MetinView nearest(Position position, double radius) {
        return instances.values().stream().map(ActiveMetin::view)
                .filter(view -> view.position().distanceSquared(position) <= radius * radius)
                .min(Comparator.comparingDouble(view -> view.position().distanceSquared(position))).orElse(null);
    }
}
