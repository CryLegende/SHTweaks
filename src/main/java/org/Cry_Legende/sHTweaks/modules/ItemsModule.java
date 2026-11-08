package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ItemSpawnEvent;

import java.util.Set;

/**
 * Items au sol insensibles à la lave, au feu, aux explosions et aux cactus, sauf les types exclus.
 * Le vide et /kill les retirent toujours (ces dégâts passent outre l'invulnérabilité).
 */
public final class ItemsModule extends Module {

    private Set<Material> excluded;

    @Override
    protected void load(ConfigurationSection config) {
        excluded = Materials.parse(config.getStringList("excluded"), plugin.getLogger(), path("excluded"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (!excluded.contains(item.getItemStack().getType())) {
            item.setInvulnerable(true);
        }
    }
}
