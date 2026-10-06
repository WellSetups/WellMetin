package com.wellsetups.wellmetin.api.event;

import com.wellsetups.wellmetin.api.MetinView;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Notification on the encounter/guardian owning region. Immutable; no cancellation. */
public final class MetinLifecycleEvent extends Event {
    public enum Kind { SPAWN, PHASE, DESTROY, MOB_SPAWN }
    private static final HandlerList HANDLERS = new HandlerList();
    private final Kind kind;
    private final MetinView metin;
    private final String detail;
    private final UUID entityId;

    public MetinLifecycleEvent(Kind kind, MetinView metin, String detail, UUID entityId) {
        this.kind = kind;
        this.metin = metin;
        this.detail = detail;
        this.entityId = entityId;
    }
    public Kind getKind() { return kind; }
    public MetinView getMetin() { return metin; }
    public String getDetail() { return detail; }
    public UUID getEntityId() { return entityId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
