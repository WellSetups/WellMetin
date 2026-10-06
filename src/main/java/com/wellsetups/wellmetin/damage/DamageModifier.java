package com.wellsetups.wellmetin.damage;

/** Runs synchronously on the target's region. Implementations must be fast and thread-safe. */
@FunctionalInterface
public interface DamageModifier {
    double modify(DamageContext context, double damage);
}
