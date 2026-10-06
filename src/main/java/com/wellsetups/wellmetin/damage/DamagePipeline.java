package com.wellsetups.wellmetin.damage;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

public final class DamagePipeline {
    private record Entry(String id, int priority, DamageModifier modifier) { }
    private volatile List<Entry> modifiers = List.of();

    /** Lower priorities execute first; IDs break ties deterministically. Close on plugin disable. */
    public synchronized AutoCloseable register(String id, int priority, DamageModifier modifier) {
        if (modifiers.stream().anyMatch(entry -> entry.id().equals(id))) {
            throw new IllegalArgumentException("duplicate damage modifier: " + id);
        }
        Entry entry = new Entry(java.util.Objects.requireNonNull(id), priority,
                java.util.Objects.requireNonNull(modifier));
        List<Entry> replacement = new ArrayList<>(modifiers);
        replacement.add(entry);
        replacement.sort(Comparator.comparingInt(Entry::priority).thenComparing(Entry::id));
        modifiers = List.copyOf(replacement);
        return () -> unregister(entry);
    }

    private synchronized void unregister(Entry entry) {
        modifiers = modifiers.stream().filter(existing -> existing != entry).toList();
    }

    public double calculate(DamageContext context, double vanilla, double base, double type) {
        double damage = finite(vanilla * base * type);
        for (Entry entry : modifiers) {
            damage = finite(entry.modifier().modify(context, damage));
        }
        return damage;
    }

    private static double finite(double damage) {
        if (!Double.isFinite(damage) || damage < 0) {
            throw new IllegalArgumentException("damage must be finite and nonnegative");
        }
        return damage;
    }
}
