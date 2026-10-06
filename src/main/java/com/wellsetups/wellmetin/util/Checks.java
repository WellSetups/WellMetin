package com.wellsetups.wellmetin.util;

import java.util.Locale;

public final class Checks {
    private Checks() { }

    public static double range(String path, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    public static String id(String path, String value) {
        if (value == null || !value.matches("[a-z0-9_][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException(path + " must be a lowercase ID (1-64 characters)");
        }
        return value;
    }

    public static String text(String path, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(path + " is missing");
        }
        return value;
    }

    public static String permission(String value) {
        if (!value.isEmpty() && !value.matches("wellmetin\\.[a-z0-9_.*-]+")) {
            throw new IllegalArgumentException("permission must use the wellmetin. namespace");
        }
        return value;
    }

    public static <E extends Enum<E>> E enumeration(Class<E> type, String path, String value) {
        try {
            return Enum.valueOf(type, text(path, value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(path + " has an invalid " + type.getSimpleName() + ": " + value, failure);
        }
    }
}
