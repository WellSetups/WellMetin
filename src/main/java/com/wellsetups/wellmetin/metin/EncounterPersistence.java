package com.wellsetups.wellmetin.metin;

import com.wellsetups.wellmetin.loot.RewardService;
import com.wellsetups.wellmetin.persistence.RewardDelivery;
import com.wellsetups.wellmetin.persistence.SqliteStore;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class EncounterPersistence {
    private final SqliteStore store;
    private final RewardService rewards;
    private final Consumer<String> errors;

    public EncounterPersistence(SqliteStore store, RewardService rewards, Consumer<String> errors) {
        this.store = store;
        this.rewards = rewards;
        this.errors = errors;
    }

    public CompletableFuture<Boolean> save(ActiveMetin metin, boolean alive, List<RewardDelivery> deliveries) {
        deliveries.forEach(reward -> metin.unsettledRewards().putIfAbsent(reward.id(), reward));
        List<RewardDelivery> unsettled = List.copyOf(metin.unsettledRewards().values());
        metin.saving(true);
        long respawnAt = alive ? 0 : System.currentTimeMillis() + metin.definition().spawn().respawnSeconds() * 1000;
        return store.save(metin.snapshot(alive), unsettled, metin.configuration().settings().persistentMetins(), respawnAt)
                .whenComplete((written, failure) -> {
                    metin.saving(false);
                    if (failure != null && !metin.failed()) {
                        metin.fail();
                        errors.accept("Persistence failed for encounter " + metin.id() + "; combat frozen: " + failure);
                    } else if (Boolean.TRUE.equals(written)) {
                        unsettled.forEach(reward -> metin.unsettledRewards().remove(reward.id(), reward));
                        unsettled.stream().map(RewardDelivery::player).distinct().forEach(rewards::pump);
                    }
                });
    }
}
