package com.wellsetups.wellmetin.loot;

import com.wellsetups.wellmetin.metin.ActiveMetin;
import com.wellsetups.wellmetin.persistence.RewardDelivery;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

public final class RewardPlanner {
    private final LootRoller roller = new LootRoller();

    public List<RewardDelivery> plan(ActiveMetin metin, String checkpoint, String tableId) {
        var rules = metin.definition().rewards();
        Map<UUID, Double> contributions = metin.state().contributions();
        var recipients = Recipients.select(rules.mode(), contributions, metin.state().maximum(),
                rules.minimumContribution(), metin.view().owner(), metin.state().lastHit());
        List<RewardDelivery> result = new ArrayList<>();
        for (UUID player : recipients) {
            double contribution = Recipients.percent(contributions.getOrDefault(player, 0.0), metin.state().maximum());
            result.addAll(plan(metin.configuration().loot().get(tableId), metin.id(), metin.definition().name(),
                    checkpoint, player, contribution));
        }
        return List.copyOf(result);
    }

    public List<RewardDelivery> plan(LootTable table, UUID instance, String name, String checkpoint,
                                     UUID player, double contribution) {
        String prefix = instance + "/" + checkpoint + "/" + table.id() + "/" + player;
        UUID seed = UUID.nameUUIDFromBytes(prefix.getBytes(StandardCharsets.UTF_8));
        SplittableRandom random = new SplittableRandom(seed.getMostSignificantBits() ^ seed.getLeastSignificantBits());
        List<RewardDelivery> result = new ArrayList<>();
        for (LootRoller.Roll roll : roller.roll(table, random)) {
            if (contribution >= roll.entry().minimumContribution()) {
                result.add(new RewardDelivery(prefix + "/" + roll.ordinal() + "/" + roll.entry().id(), player,
                        instance, name, roll.entry(), roll.amount(), contribution));
            }
        }
        return List.copyOf(result);
    }
}
