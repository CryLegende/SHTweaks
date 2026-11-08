package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
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
 * Casser un catalyseur de sculk sans Toucher de soie demande un second coup dans le délai configuré.
 */
public final class SculkConfirm extends Module {

    private final Map<UUID, Long> pending = new HashMap<>();
    private long confirmationMs;

    @Override
    protected void load(ConfigurationSection config) {
        confirmationMs = config.getLong("confirmation-seconds", 10) * 1000L;
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
        if (player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            pending.remove(uuid);
            return;
        }
        long now = System.currentTimeMillis();
        Long first = pending.get(uuid);
        if (first != null && now - first <= confirmationMs) {
            // Second coup dans le délai : le cassage passe, le prochain redemandera confirmation
            pending.remove(uuid);
            return;
        }
        event.setCancelled(true);
        pending.put(uuid, now);
        plugin.messages().send(player, "sculk-confirm");
    }
}
