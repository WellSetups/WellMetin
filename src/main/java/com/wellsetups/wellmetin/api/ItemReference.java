package com.wellsetups.wellmetin.api;

import com.wellsetups.wellmetin.util.Checks;
import java.util.Locale;

public record ItemReference(String provider, String id, int customModelData) {
    public ItemReference {
        provider = Checks.text("item.provider", provider).toUpperCase(Locale.ROOT);
        Checks.text("item.id", id);
        Checks.range("item.custom-model-data", customModelData, 0, Integer.MAX_VALUE);
    }
}
