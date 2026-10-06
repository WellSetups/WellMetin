package com.wellsetups.wellmetin.metin;

import com.wellsetups.wellmetin.api.Position;
import com.wellsetups.wellmetin.persistence.SavedMetin;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PendingMetins {
    private record ChunkKey(String world, int x, int z) { }
    private final ConcurrentHashMap<UUID, SavedMetin> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkKey, ConcurrentHashMap<UUID, SavedMetin>> chunks = new ConcurrentHashMap<>();

    synchronized void put(SavedMetin state) {
        states.put(state.id(), state);
        chunks.computeIfAbsent(key(state.position()), ignored -> new ConcurrentHashMap<>()).put(state.id(), state);
    }

    synchronized SavedMetin remove(UUID id) {
        SavedMetin state = states.remove(id);
        if (state != null) {
            chunks.computeIfPresent(key(state.position()), (key, values) -> {
                values.remove(id);
                return values.isEmpty() ? null : values;
            });
        }
        return state;
    }

    Collection<SavedMetin> chunk(String world, int x, int z) {
        Map<UUID, SavedMetin> values = chunks.get(new ChunkKey(world, x, z));
        return values == null ? List.of() : List.copyOf(values.values());
    }

    Collection<SavedMetin> all() { return List.copyOf(states.values()); }
    SavedMetin get(UUID id) { return states.get(id); }
    int size() { return states.size(); }
    void clear() {
        states.clear();
        chunks.clear();
    }

    private ChunkKey key(Position position) { return new ChunkKey(position.world(), position.chunkX(), position.chunkZ()); }
}
