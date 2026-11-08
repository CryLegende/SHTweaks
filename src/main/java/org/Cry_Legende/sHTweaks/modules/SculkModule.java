package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Casser un catalyseur de sculk sans Toucher de soie demande un second coup sur le même bloc
 * dans le délai configuré. Le créatif n'est pas concerné.
 */
public final class SculkModule extends Module {

    /** Premier coup en attente de confirmation : le bloc visé et l'heure. */
    private record Pending(Location block, long time) {
    }

    private final Map<UUID, Pending> pending = new HashMap<>();
    private long confirmationMs;

    @Override
    protected void load(ConfigurationSection config) {
        confirmationMs = Math.max(1, config.getLong("confirmation-seconds", 10)) * 1000L;
    }

    @Override
    protected void unload() {
        pending.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SCULK_CATALYST) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            pending.remove(uuid);
            return;
        }
        Location block = event.getBlock().getLocation();
        long now = System.currentTimeMillis();
        Pending first = pending.remove(uuid);
        if (first != null && first.block().equals(block) && now - first.time() <= confirmationMs) {
            // Second coup sur le même catalyseur dans le délai : le cassage passe
            return;
        }
        event.setCancelled(true);
        pending.put(uuid, new Pending(block, now));
        send(player, "confirm", "seconds", String.valueOf(confirmationMs / 1000));
    }
}
