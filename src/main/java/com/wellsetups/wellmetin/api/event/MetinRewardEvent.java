package com.wellsetups.wellmetin.api.event;

import com.wellsetups.wellmetin.persistence.RewardDelivery;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Runs on the recipient's region before claiming. Cancellation permanently discards this delivery. */
public final class MetinRewardEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final RewardDelivery reward;
    private int amount;
    private boolean cancelled;

    public MetinRewardEvent(RewardDelivery reward) {
        this.reward = reward;
        amount = reward.amount();
    }
    public RewardDelivery getReward() { return reward; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) {
        int maximum = reward.entry().kind() == com.wellsetups.wellmetin.loot.LootTable.Kind.ITEM ? 2304 : Integer.MAX_VALUE;
        if (amount < 1 || amount > maximum) {
            throw new IllegalArgumentException("reward amount must be between 1 and " + maximum);
        }
        this.amount = amount;
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
