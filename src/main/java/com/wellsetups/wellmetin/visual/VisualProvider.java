package com.wellsetups.wellmetin.visual;

import com.wellsetups.wellmetin.api.ItemReference;
import com.wellsetups.wellmetin.api.MetinView;
import com.wellsetups.wellmetin.config.MetinDefinition;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;

public interface VisualProvider {
    Visual spawn(UUID instance, Location location, MetinDefinition definition);

    /** All operations belong to the stationary encounter's region. */
    interface Visual {
        List<UUID> entityIds();
        void update(MetinView metin);
        void change(ItemReference item);
        void remove();
    }
}
