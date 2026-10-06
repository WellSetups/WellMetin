package com.wellsetups.wellmetin.api.event;

import com.wellsetups.wellmetin.api.MetinView;
import com.wellsetups.wellmetin.util.Checks;
import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Runs after modifiers, before health changes, on the target's region. Cancellation consumes no HP. */
public final class MetinPreDamageEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MetinView metin;
    private final UUID player;
    private double damage;
    private boolean cancelled;

    public MetinPreDamageEvent(MetinView metin, UUID player, double damage) {
        this.metin = metin;
        this.player = player;
        setDamage(damage);
    }
    public MetinView getMetin() { return metin; }
    public UUID getPlayerId() { return player; }
    public double getDamage() { return damage; }
    public void setDamage(double damage) { this.damage = Checks.range("damage", damage, 0, Double.MAX_VALUE); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
