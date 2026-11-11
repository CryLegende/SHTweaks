package org.Cry_Legende.sHTweaks.modules;

import io.papermc.paper.event.block.BlockBreakBlockEvent;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nombre d'éclats d'améthyste lâchés par une grappe cassée sans Toucher de soie, par un joueur
 * comme par un bloc (piston, liquide).
 * Priorité HIGH : SHTPDrops (HIGHEST) ramasse ensuite la liste modifiée.
 */
public final class AmethystModule extends Module {

    private int minDrops;
    private int maxDrops;
    private boolean fortuneAffectsAmount;
    private boolean sendMessage;

    @Override
    protected void load(ConfigurationSection config) {
        minDrops = Math.max(0, config.getInt("min-drops", 1));
        maxDrops = Math.max(minDrops, config.getInt("max-drops", 1));
        fortuneAffectsAmount = config.getBoolean("fortune-affects-amount", false);
        sendMessage = config.getBoolean("send-message", false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClusterDrop(BlockDropItemEvent event) {
        if (event.getBlockState().getType() != Material.AMETHYST_CLUSTER) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (player.getGameMode() == GameMode.CREATIVE || tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }
        int amount = roll();
        if (fortuneAffectsAmount) {
            amount = Math.min(amount + tool.getEnchantmentLevel(Enchantment.FORTUNE), maxDrops);
        }

        List<Item> items = event.getItems();
        if (amount <= 0) {
            items.clear();
        } else {
            Item shards = items.stream().filter(item -> item.getItemStack().getType() == Material.AMETHYST_SHARD).findFirst().orElse(null);
            if (shards == null) {
                return;
            }
            // Le premier item d'éclats est réutilisé avec la nouvelle quantité, les autres drops disparaissent
            shards.setItemStack(ItemStack.of(Material.AMETHYST_SHARD, amount));
            items.clear();
            items.add(shards);
        }
        if (sendMessage) {
            send(player, "break", "amount", String.valueOf(amount));
        }
    }

    /** Grappe cassée par un piston ou un liquide : même quantité que cassée à la main, sans Fortune. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClusterBrokenByBlock(BlockBreakBlockEvent event) {
        if (event.getBlock().getType() != Material.AMETHYST_CLUSTER) {
            return;
        }
        List<ItemStack> drops = event.getDrops();
        if (drops.stream().noneMatch(drop -> drop.getType() == Material.AMETHYST_SHARD)) {
            return;
        }
        int amount = roll();
        drops.clear();
        if (amount > 0) {
            drops.add(ItemStack.of(Material.AMETHYST_SHARD, amount));
        }
    }

    private int roll() {
        return minDrops == maxDrops ? minDrops : ThreadLocalRandom.current().nextInt(minDrops, maxDrops + 1);
    }
}
