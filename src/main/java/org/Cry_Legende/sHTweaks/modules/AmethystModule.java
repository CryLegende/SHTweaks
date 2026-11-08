package org.Cry_Legende.sHTweaks.modules;

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
 * Nombre d'éclats d'améthyste lâchés par une grappe cassée sans Toucher de soie.
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
        int amount = minDrops == maxDrops ? minDrops : ThreadLocalRandom.current().nextInt(minDrops, maxDrops + 1);
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
}
