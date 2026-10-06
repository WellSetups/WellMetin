package com.wellsetups.wellmetin.command;

import com.wellsetups.wellmetin.api.Position;
import java.util.UUID;

public final class Arguments {
    private Arguments() { }

    public static UUID uuid(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("a full UUID is required");
        }
        return UUID.fromString(value);
    }

    public static Position position(String[] arguments, int offset) {
        if (arguments.length != offset + 4) {
            throw new IllegalArgumentException("expected world x y z");
        }
        try {
            return new Position(arguments[offset], Double.parseDouble(arguments[offset + 1]),
                    Double.parseDouble(arguments[offset + 2]), Double.parseDouble(arguments[offset + 3]), 0);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("coordinates must be finite numbers", failure);
        }
    }
}
