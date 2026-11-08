package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ItemSpawnEvent;

import java.util.Set;

/**
 * Items au sol indestructibles (lave, feu, explosions), sauf les types exclus.
 */
public final class IndestructibleItems extends Module {

    private Set<Material> excluded;

    @Override
    protected void load(ConfigurationSection config) {
        excluded = Materials.parse(config.getStringList("excluded"), plugin.getLogger(), "indestructible-items.excluded");
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (!excluded.contains(item.getItemStack().getType())) {
            item.setInvulnerable(true);
        }
    }
}
