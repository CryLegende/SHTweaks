package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * La poudre d'os n'a plus d'effet sur les fleurs à deux blocs de haut : en vanilla elle lâche une copie de la fleur
 * (duplication). Un clic droit ou un distributeur visant l'une d'elles ne fait rien et ne consomme pas la poudre d'os,
 * comme sur n'importe quel bloc que la poudre d'os ne fait pas pousser.
 */
public final class BonemealModule extends Module {

    private Set<Material> blocks;

    @Override
    protected void load(ConfigurationSection config) {
        blocks = Materials.parse(config.getStringList("blocks"), plugin.getLogger(), path("blocks"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        Block block = event.getClickedBlock();
        if (item == null || item.getType() != Material.BONE_MEAL || block == null || !blocks.contains(block.getType())) {
            return;
        }
        // Seul l'objet en main est refusé : ces fleurs n'ont aucune interaction propre
        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getItem().getType() != Material.BONE_MEAL
                || !(event.getBlock().getBlockData() instanceof Directional dispenser)) {
            return;
        }
        // Le distributeur utilise la poudre d'os sur le bloc en face de lui ; annulé, la poudre d'os reste dedans
        if (blocks.contains(event.getBlock().getRelative(dispenser.getFacing()).getType())) {
            event.setCancelled(true);
        }
    }
}
