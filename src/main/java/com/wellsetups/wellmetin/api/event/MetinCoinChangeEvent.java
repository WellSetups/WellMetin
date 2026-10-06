package com.wellsetups.wellmetin.api.event;

import com.wellsetups.wellmetin.persistence.SqliteStore;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** ASYNCHRONOUS post-commit notification. Listeners must schedule all Bukkit entity/world work. */
public final class MetinCoinChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final SqliteStore.CoinChange change;
    public MetinCoinChangeEvent(SqliteStore.CoinChange change) {
        super(true);
        this.change = change;
    }
    public SqliteStore.CoinChange getChange() { return change; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
