package com.wellsetups.wellmetin.api.event;

import com.wellsetups.wellmetin.api.MetinView;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Region-local notification of accepted, overkill-clamped damage; persistence is asynchronous. */
public final class MetinDamageEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MetinView metin;
    private final UUID player;
    private final double damage;
    public MetinDamageEvent(MetinView metin, UUID player, double damage) {
        this.metin = metin;
        this.player = player;
        this.damage = damage;
    }
    public MetinView getMetin() { return metin; }
    public UUID getPlayerId() { return player; }
    public double getDamage() { return damage; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
