package com.wellsetups.wellmetin.metin;

import com.wellsetups.wellmetin.api.MetinView;
import com.wellsetups.wellmetin.config.Configuration;
import com.wellsetups.wellmetin.config.MetinDefinition;
import com.wellsetups.wellmetin.persistence.SavedMetin;
import com.wellsetups.wellmetin.persistence.RewardDelivery;
import com.wellsetups.wellmetin.platform.Task;
import com.wellsetups.wellmetin.visual.VisualProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.LivingEntity;

/** Entity references are intentionally bounded by this loaded encounter's lifecycle. */
public final class ActiveMetin {
    private final SavedMetin origin;
    private final Configuration configuration;
    private final MetinDefinition definition;
    private final EncounterState state;
    private final LivingEntity carrier;
    private final VisualProvider.Visual visual;
    private final ConcurrentHashMap<UUID, Long> guardians = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> attackTicks = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile MetinView view;
    private volatile SavedMetin saved;
    private volatile boolean failed;
    private volatile boolean saving;
    private final ConcurrentHashMap<String, RewardDelivery> unsettledRewards = new ConcurrentHashMap<>();
    private boolean ready;
    private boolean ending;
    private boolean processing;
    private long version;
    private double multiplier;
    private long immuneUntil;
    private Task displayUpdate;
    private Task maintenance;

    public ActiveMetin(SavedMetin origin, Configuration configuration, LivingEntity carrier, VisualProvider.Visual visual) {
        this.origin = origin;
        this.configuration = configuration;
        definition = configuration.metins().get(origin.definition());
        state = new EncounterState(origin.maximum(), origin.health(), origin.contributions(), origin.triggered(), origin.lastHit());
        this.carrier = carrier;
        this.visual = visual;
        version = origin.version();
        multiplier = origin.multiplier();
        immuneUntil = origin.immuneUntil();
        saved = origin;
        refresh();
    }

    public void refresh() {
        view = new MetinView(origin.id(), definition.id(), definition.name(), origin.position(),
                state.health(), state.maximum(), locked(), origin.owner());
    }

    public SavedMetin snapshot(boolean alive) {
        saved = new SavedMetin(origin.id(), definition.id(), origin.position(), alive && !ending ? state.health() : 0,
                state.maximum(), state.contributions(), state.triggered(), origin.owner(), state.lastHit(),
                origin.spawnId(), ++version, multiplier, immuneUntil);
        return saved;
    }

    public boolean acceptAttack(UUID player, int tick) {
        Integer previous = attackTicks.get(player);
        if (previous != null && tick - previous < definition.combat().minimumAttackIntervalTicks()) {
            return false;
        }
        attackTicks.put(player, tick);
        return true;
    }

    public boolean locked() { return !ready || ending || saving || failed || immuneUntil > System.currentTimeMillis() || !guardians.isEmpty(); }
    public void saving(boolean value) { saving = value; }
    public ConcurrentHashMap<String, RewardDelivery> unsettledRewards() { return unsettledRewards; }
    public void ready() { ready = true; }
    public void ending() { ending = true; }
    public boolean isEnding() { return ending; }
    public boolean processing() { return processing; }
    public void processing(boolean value) { processing = value; }
    public void immunity(long ticks) { immuneUntil = Math.max(immuneUntil, System.currentTimeMillis() + ticks * 50); }
    public void multiplier(double value) { multiplier = value; }
    public double multiplier() { return multiplier; }
    public void fail() { failed = true; }
    public boolean failed() { return failed; }
    public boolean close() { return closed.compareAndSet(false, true); }
    public boolean closed() { return closed.get(); }
    public UUID id() { return origin.id(); }
    public String spawnId() { return origin.spawnId(); }
    public Configuration configuration() { return configuration; }
    public MetinDefinition definition() { return definition; }
    public EncounterState state() { return state; }
    public LivingEntity carrier() { return carrier; }
    public VisualProvider.Visual visual() { return visual; }
    public MetinView view() { return view; }
    public SavedMetin saved() { return saved; }
    public ConcurrentHashMap<UUID, Long> guardians() { return guardians; }
    public Task displayUpdate() { return displayUpdate; }
    public void displayUpdate(Task task) { displayUpdate = task; }
    public void maintenance(Task task) { maintenance = task; }

    public void cancelTasks() {
        if (displayUpdate != null) {
            displayUpdate.cancel();
        }
        if (maintenance != null) {
            maintenance.cancel();
        }
        attackTicks.clear();
    }
}
