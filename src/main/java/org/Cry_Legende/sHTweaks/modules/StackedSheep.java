package org.Cry_Legende.sHTweaks.modules;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Empêche de tondre un mouton qui en représente plusieurs (WildStacker), à la main ou au distributeur.
 */
public final class StackedSheep extends Module {

    @Override
    protected void load(ConfigurationSection config) {
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Sheep sheep
                && event.getPlayer().getInventory().getItemInMainHand().getType() == Material.SHEARS
                && WildStackerAPI.getEntityAmount(sheep) > 1) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getItem().getType() != Material.SHEARS || !(event.getBlock().getBlockData() instanceof Directional directional)) {
            return;
        }
        Block target = event.getBlock().getRelative(directional.getFacing());
        for (Entity entity : target.getWorld().getNearbyEntities(target.getLocation(), 1, 1, 1)) {
            if (entity instanceof Sheep sheep && WildStackerAPI.getEntityAmount(sheep) > 1) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
