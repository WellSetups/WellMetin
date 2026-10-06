package com.wellsetups.wellmetin.platform;

import com.wellsetups.wellmetin.api.Position;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/** Online handles exist only between join and quit; position snapshots are safe across regions. */
public final class PlayerDirectory implements AutoCloseable {
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Scheduler scheduler;

    public PlayerDirectory(Scheduler scheduler) { this.scheduler = scheduler; }

    public void join(Player player) {
        Session session = new Session(player, Locations.snapshot(player.getLocation()));
        Session previous = sessions.put(player.getUniqueId(), session);
        if (previous != null && previous.task != null) {
            previous.task.cancel();
        }
        session.task = scheduler.entity(player, () -> session.position = Locations.snapshot(player.getLocation()),
                () -> sessions.remove(player.getUniqueId(), session), 20, 20);
    }

    public void quit(UUID id) {
        Session session = sessions.remove(id);
        if (session != null && session.task != null) {
            session.task.cancel();
        }
    }

    public boolean execute(UUID id, Consumer<Player> action, Runnable retired) {
        Session session = sessions.get(id);
        if (session == null) {
            retired.run();
            return false;
        }
        scheduler.entity(session.player, () -> action.accept(session.player), retired, 1, 0);
        return true;
    }

    public UUID byName(String name) {
        return sessions.entrySet().stream().filter(entry -> entry.getValue().name.equalsIgnoreCase(name))
                .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
    }

    public List<String> names() { return sessions.values().stream().map(session -> session.name).sorted().toList(); }
    public Collection<UUID> ids() { return List.copyOf(sessions.keySet()); }
    public Position position(UUID id) {
        Session session = sessions.get(id);
        return session == null ? null : session.position;
    }

    public void nearby(Position origin, double radius, Consumer<Player> action) {
        sessions.forEach((id, session) -> {
            if (origin.distanceSquared(session.position) <= radius * radius) {
                execute(id, player -> {
                    if (origin.distanceSquared(Locations.snapshot(player.getLocation())) <= radius * radius) {
                        action.accept(player);
                    }
                }, () -> { });
            }
        });
    }

    @Override
    public void close() {
        sessions.keySet().forEach(this::quit);
    }

    private static final class Session {
        private final Player player;
        private final String name;
        private volatile Position position;
        private volatile Task task;

        Session(Player player, Position position) {
            this.player = player;
            this.name = player.getName();
            this.position = position;
        }
    }
}
