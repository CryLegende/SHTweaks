package org.Cry_Legende.sHTweaks.modules;

import com.bgsoftware.wildtools.api.WildToolsAPI;
import com.bgsoftware.wildtools.api.events.SellWandUseEvent;
import com.bgsoftware.wildtools.api.objects.tools.SellTool;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Confirmation avant de vendre le contenu d'un coffre avec la sell wand de WildTools : un premier clic droit affiche
 * le prix, un second clic sur le même coffre dans les 10 secondes lance la vente. S'applique à tous les joueurs.
 * Le module n'a aucun réglage.
 */
public final class SellWandConfirmModule extends Module {

    private final Map<UUID, Confirmation> confirmations = new ConcurrentHashMap<>();

    @Override
    protected void load(ConfigurationSection config) {
        confirmations.clear();
    }

    @Override
    protected void unload() {
        confirmations.clear();
    }

    // Vide la confirmation en attente du joueur : sans ça la map garde une entrée par joueur pour toujours
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        confirmations.remove(event.getPlayer().getUniqueId());
    }

    /** Annule le clic avec la sell wand tant que le joueur n'a pas confirmé sur ce coffre. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        SellTool sellTool = WildToolsAPI.getSellTool(itemInHand);

        if (sellTool == null) {
            return;
        }

        if (confirmations.containsKey(player.getUniqueId()) && confirmations.get(player.getUniqueId()).isFor(event.getClickedBlock())) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    /** Premier usage : la vente est annulée et le prix demandé ; second usage sur le même coffre : elle passe. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSellWandUse(SellWandUseEvent event) {
        if (event.getPrice() <= 0) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Block containerBlock = event.getContainer().getBlock();

        if (confirmations.containsKey(playerUUID)) {
            Confirmation confirmation = confirmations.get(playerUUID);
            if (confirmation.isFor(containerBlock)) {
                confirmations.remove(playerUUID);
                return;
            }
        }

        event.setCancelled(true);
        confirmations.put(playerUUID, new Confirmation(containerBlock));

        // Prix final : prix de base et multiplicateur
        double finalPrice = event.getPrice() * event.getMultiplier();
        send(player, "confirmation", "price", String.format("%,.2f", finalPrice));
    }

    private static final class Confirmation {
        private static final long TIMEOUT = 10000;

        private final Block containerBlock;
        private final long timestamp;

        Confirmation(Block containerBlock) {
            this.containerBlock = containerBlock;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isFor(Block block) {
            if (block == null) return false;
            boolean sameWorld = this.containerBlock.getWorld().equals(block.getWorld());
            boolean sameLocation = this.containerBlock.getX() == block.getX() &&
                this.containerBlock.getY() == block.getY() &&
                this.containerBlock.getZ() == block.getZ();
            boolean notExpired = (System.currentTimeMillis() - timestamp) < TIMEOUT;
            return sameWorld && sameLocation && notExpired;
        }
    }
}
