package org.Cry_Legende.sHTweaks.modules;

import com.destroystokyo.paper.event.block.AnvilDamagedEvent;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.HashSet;
import java.util.Set;

/**
 * Les enclumes ne s'usent pas dans les mondes configurés : l'usure est annulée avant d'être appliquée,
 * y compris quand l'enclume allait se briser.
 */
public final class InfiniteAnvils extends Module {

    private Set<String> worlds;

    @Override
    protected void load(ConfigurationSection config) {
        worlds = new HashSet<>(config.getStringList("worlds"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilDamaged(AnvilDamagedEvent event) {
        Location location = event.getInventory().getLocation();
        // Inventaire d'enclume virtuel : pas de bloc à protéger
        if (location != null && location.getWorld() != null && worlds.contains(location.getWorld().getName())) {
            event.setCancelled(true);
        }
    }
}
