package org.Cry_Legende.sHTweaks.modules;

import com.Acrobot.Breeze.Utils.InventoryUtil;
import com.Acrobot.Breeze.Utils.StringUtil;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.Acrobot.ChestShop.Events.PreShopCreationEvent;
import com.Acrobot.ChestShop.Events.PreTransactionEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import com.Acrobot.ChestShop.Utils.ItemUtil;
import com.Acrobot.ChestShop.Utils.uBlock;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Shops ChestShop posés sur des coffres AdvancedChests.
 *
 * <p>ChestShop lit et remplit l'inventaire du bloc auquel la pancarte est accrochée. Sur un coffre AdvancedChests,
 * cette inventaire ne contient que des ficelles de remplissage : le contenu est dans les pages du coffre. Avant que
 * ChestShop ne vérifie le stock ou la place, le module remplace l'inventaire du shop par la vue
 * {@link AdvancedChestInventory} sur ces pages ; tout le reste (prix, argent, achats partiels, messages, historique)
 * reste celui de ChestShop.</p>
 *
 * <p>Deux calculs de ChestShop se font avant tout événement, sur l'inventaire du bloc, et sont refaits ici :
 * l'achat d'un stack en s'accroupissant (ChestShop y voyait un coffre vide) et le « ? » de la pancarte qui prend le
 * premier objet du coffre (ChestShop y prenait une ficelle de remplissage).</p>
 */
public final class ChestShopModule extends Module {

    /** Pancarte « ? » posée contre un coffre AdvancedChests vide, entre l'écoute LOW et la création du shop. */
    private final ThreadLocal<Location> emptyAutofill = new ThreadLocal<>();

    private AdvancedChestStorage storage;

    @Override
    protected void load(ConfigurationSection config) {
        Plugin advancedChests = plugin.getServer().getPluginManager().getPlugin("AdvancedChests");
        if (advancedChests == null) {
            throw new IllegalStateException("AdvancedChests absent");
        }
        try {
            storage = new AdvancedChestStorage(advancedChests, plugin.getLogger());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("API d'AdvancedChests inattendue : " + e);
        }
    }

    /**
     * Avant les contrôles de stock et de place de ChestShop (achats partiels en LOW, stock et place en NORMAL) :
     * l'inventaire du shop devient le contenu du coffre AdvancedChests.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreTransaction(PreTransactionEvent event) {
        Inventory owner = event.getOwnerInventory();
        if (owner == null || owner instanceof AdvancedChestInventory || ChestShopSign.isAdminShop(owner)) {
            return;
        }
        Container container = uBlock.findConnectedContainer(event.getSign());
        if (container == null) {
            return;
        }
        AdvancedChestStorage.Lookup found = storage.find(container.getLocation());
        switch (found.state()) {
            case NONE -> {
                return;
            }
            case READY -> {
                event.setOwnerInventory(found.inventory());
                if (event.getTransactionType() == TransactionEvent.TransactionType.BUY && event.getClient().isSneaking()
                        && !ChestShopSign.isAdminShop(event.getSign())) {
                    shiftBuy(event, found.inventory());
                }
            }
            case LOADING -> refuse(event, "loading");
            case BUSY -> refuse(event, "busy");
            case UNAVAILABLE -> refuse(event, "unavailable");
        }
    }

    /** Coffre présent mais pas lisible maintenant : ChestShop aurait lu les ficelles de remplissage. */
    private void refuse(PreTransactionEvent event, String message) {
        event.setCancelled(PreTransactionEvent.TransactionOutcome.OTHER);
        send(event.getClient(), message);
    }

    /**
     * Achat accroupi (SHIFT_SELLS_IN_STACKS ou SHIFT_SELLS_EVERYTHING) : ChestShop a calculé la quantité sur
     * l'inventaire du bloc, a trouvé 0 et a gardé celle de la pancarte. Même calcul que le sien, sur les pages : un
     * stack (ou moins s'il n'y en a pas assez), ou tout le stock. Le prix suit la quantité, réductions comprises.
     */
    private static void shiftBuy(PreTransactionEvent event, Inventory inventory) {
        boolean stacks = Properties.SHIFT_SELLS_IN_STACKS;
        if (!stacks && !Properties.SHIFT_SELLS_EVERYTHING) {
            return;
        }
        String allowed = Properties.SHIFT_ALLOWS;
        if (!"ALL".equalsIgnoreCase(allowed) && !"BUY".equalsIgnoreCase(allowed)) {
            return;
        }
        ItemStack[] stock = event.getStock();
        int asked = InventoryUtil.countItems(stock);
        BigDecimal price = event.getExactPrice();
        if (stock.length == 0 || asked <= 0 || price.signum() < 0) {
            return;
        }
        ItemStack item = stock[0].clone();
        int available = InventoryUtil.getAmount(item, inventory);
        int amount = stacks ? Math.min(InventoryUtil.getMaxStackSize(item), available) : available;
        if (amount <= 0 || amount == asked) {
            return;
        }
        item.setAmount(amount);
        event.setStock(InventoryUtil.getItemsStacked(item));
        event.setExactPrice(price.divide(BigDecimal.valueOf(asked), MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(amount))
                .setScale(Properties.PRICE_PRECISION, RoundingMode.HALF_UP));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransaction(TransactionEvent event) {
        if (event.getOwnerInventory() instanceof AdvancedChestInventory inventory) {
            inventory.changed();
        }
    }

    /**
     * Pancarte de shop avec « ? » en 4e ligne, avant ChestShop (NORMAL) : il prendrait le premier objet de
     * l'inventaire du bloc, une ficelle de remplissage. Coffre non vide : la ligne reçoit le code du premier objet
     * des pages, ChestShop crée le shop comme si on l'avait écrit. Coffre vide : la création est marquée pour
     * {@link #onPreShopCreation}.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        emptyAutofill.remove();
        if (!Properties.ALLOW_AUTO_ITEM_FILL || event.getSide() != Side.FRONT) {
            return;
        }
        String[] lines = StringUtil.stripColourCodes(event.getLines());
        if (!ChestShopSign.isValidPreparedSign(lines) || !"?".equals(ChestShopSign.getItem(lines))) {
            return;
        }
        Container container = uBlock.findConnectedContainer(event.getBlock());
        if (container == null) {
            return;
        }
        AdvancedChestStorage.Lookup found = storage.find(container.getLocation());
        if (found.state() == AdvancedChestStorage.State.NONE) {
            return;
        }
        ItemStack first = found.state() == AdvancedChestStorage.State.READY ? found.inventory().firstItem() : null;
        if (first != null) {
            try {
                event.setLine(3, ItemUtil.getSignName(first));
                return;
            } catch (IllegalArgumentException e) {
                // Pas de code pour cet objet : on retombe sur le « ? » à remplir d'un clic, comme un coffre vide
            }
        }
        emptyAutofill.set(event.getBlock().getLocation());
    }

    /**
     * Coffre vide (ou pas lisible) : ChestShop a pris une ficelle de remplissage pour un objet. La pancarte repasse
     * en « ? » à remplir d'un clic avec l'objet en main, exactement ce que fait ChestShop devant un coffre vide.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPreShopCreation(PreShopCreationEvent event) {
        Location pending = emptyAutofill.get();
        if (pending == null || !pending.equals(event.getSign().getLocation())) {
            return;
        }
        emptyAutofill.remove();
        event.setSignLine((byte) 3, "§l?");
        PreShopCreationEvent.CreationOutcome outcome = event.getOutcome();
        if (outcome == PreShopCreationEvent.CreationOutcome.SHOP_CREATED_SUCCESSFULLY
                || outcome == PreShopCreationEvent.CreationOutcome.INVALID_ITEM) {
            event.setOutcome(PreShopCreationEvent.CreationOutcome.ITEM_AUTOFILL);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSignChangeDone(SignChangeEvent event) {
        emptyAutofill.remove();
    }

    @Override
    protected void unload() {
        emptyAutofill.remove();
    }
}
