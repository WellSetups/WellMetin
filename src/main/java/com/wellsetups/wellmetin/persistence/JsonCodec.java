package com.wellsetups.wellmetin.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

public final class JsonCodec {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public String encode(Object value) { return gson.toJson(value); }

    public <T> T decode(String value, Class<T> type) {
        try {
            T result = gson.fromJson(value, type);
            if (result == null) {
                throw new IllegalArgumentException("null database payload");
            }
            return result;
        } catch (JsonParseException failure) {
            throw new IllegalArgumentException("invalid " + type.getSimpleName() + " payload", failure);
        }
    }
}
