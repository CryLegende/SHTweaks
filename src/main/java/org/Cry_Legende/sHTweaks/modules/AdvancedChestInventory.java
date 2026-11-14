package org.Cry_Legende.sHTweaks.modules;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Les pages d'un coffre AdvancedChests vues comme une seule inventaire : les emplacements de rangement de la page 0,
 * puis ceux de la page 1, etc. La dernière ligne de chaque page (boutons) n'en fait pas partie.
 *
 * <p>Chaque lecture et chaque écriture passe directement par l'inventaire Bukkit de la page : ce que ChestShop retire
 * ou ajoute est aussitôt dans le coffre, visible des joueurs qui ont la page ouverte. {@link #getItem(int)} rend,
 * comme une inventaire de Paper, une copie miroir : changer sa quantité change le coffre (ChestShop s'en sert quand il
 * retire des objets). Elle ne sert que le temps d'une transaction : le nombre de pages est celui du moment où elle a
 * été créée.</p>
 */
final class AdvancedChestInventory implements Inventory {

    /** Ligne de boutons en bas de chaque page. */
    private static final int BUTTON_ROW = 9;

    private final AdvancedChestStorage storage;
    private final Object chest;
    private final Location location;
    private final Inventory[] pages;
    /** Premier index de chaque page dans cette vue. */
    private final int[] starts;
    private final int size;

    AdvancedChestInventory(AdvancedChestStorage storage, Object chest, Location location, Inventory[] pages) {
        this.storage = storage;
        this.chest = chest;
        this.location = location.clone();
        this.pages = pages;
        this.starts = new int[pages.length];
        int total = 0;
        for (int i = 0; i < pages.length; i++) {
            starts[i] = total;
            total += Math.max(0, pages[i].getSize() - BUTTON_ROW);
        }
        this.size = total;
    }

    /** À appeler après la transaction : sauvegarde, compteur « coffre plein » et hologramme d'AdvancedChests. */
    void changed() {
        storage.changed(chest);
    }

    /** Premier objet rangé (ordre des pages puis des emplacements), ficelles de remplissage ignorées ; copie. */
    ItemStack firstItem() {
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = getItem(slot);
            if (!empty(item) && !storage.isFiller(item)) {
                return item.clone();
            }
        }
        return null;
    }

    private int page(int index) {
        if (index < 0 || index >= size) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        int low = 0;
        int high = starts.length - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (starts[middle] <= index) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        // Une page sans emplacement de rangement a le même début que la suivante : on prend la dernière des deux
        return low;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public int getMaxStackSize() {
        return pages.length == 0 ? 64 : pages[0].getMaxStackSize();
    }

    @Override
    public void setMaxStackSize(int size) {
        // Réglage des pages d'AdvancedChests : on n'y touche pas
    }

    @Override
    public ItemStack getItem(int index) {
        int page = page(index);
        return pages[page].getItem(index - starts[page]);
    }

    @Override
    public void setItem(int index, ItemStack item) {
        int page = page(index);
        pages[page].setItem(index - starts[page], empty(item) ? null : item);
    }

    @Override
    public HashMap<Integer, ItemStack> addItem(ItemStack... items) {
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null) {
                throw new IllegalArgumentException("ItemStack cannot be null");
            }
            while (true) {
                int partial = firstPartial(item);
                if (partial == -1) {
                    int free = firstEmpty();
                    if (free == -1) {
                        leftover.put(i, item);
                        break;
                    }
                    int maxStack = Math.min(item.getMaxStackSize(), getMaxStackSize());
                    if (item.getAmount() > maxStack) {
                        ItemStack stack = item.clone();
                        stack.setAmount(maxStack);
                        setItem(free, stack);
                        item.setAmount(item.getAmount() - maxStack);
                    } else {
                        setItem(free, item);
                        break;
                    }
                } else {
                    ItemStack stack = getItem(partial);
                    int amount = item.getAmount();
                    int present = stack.getAmount();
                    int maxAmount = stack.getMaxStackSize();
                    if (amount + present <= maxAmount) {
                        stack.setAmount(amount + present);
                        setItem(partial, stack);
                        break;
                    }
                    stack.setAmount(maxAmount);
                    setItem(partial, stack);
                    item.setAmount(amount + present - maxAmount);
                }
            }
        }
        return leftover;
    }

    private int firstPartial(ItemStack item) {
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = getItem(slot);
            if (!empty(stack) && stack.getAmount() < stack.getMaxStackSize() && stack.isSimilar(item)) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public HashMap<Integer, ItemStack> removeItem(ItemStack... items) {
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null) {
                throw new IllegalArgumentException("ItemStack cannot be null");
            }
            int toDelete = item.getAmount();
            while (toDelete > 0) {
                int slot = firstSimilar(item);
                if (slot == -1) {
                    item.setAmount(toDelete);
                    leftover.put(i, item);
                    break;
                }
                ItemStack stack = getItem(slot);
                int amount = stack.getAmount();
                if (amount <= toDelete) {
                    toDelete -= amount;
                    clear(slot);
                } else {
                    stack.setAmount(amount - toDelete);
                    setItem(slot, stack);
                    toDelete = 0;
                }
            }
        }
        return leftover;
    }

    @Override
    public HashMap<Integer, ItemStack> removeItemAnySlot(ItemStack... items) {
        return removeItem(items);
    }

    private int firstSimilar(ItemStack item) {
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = getItem(slot);
            if (!empty(stack) && stack.isSimilar(item)) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public ItemStack[] getContents() {
        ItemStack[] contents = new ItemStack[size];
        for (int slot = 0; slot < size; slot++) {
            contents[slot] = getItem(slot);
        }
        return contents;
    }

    @Override
    public void setContents(ItemStack[] items) {
        if (items.length > size) {
            throw new IllegalArgumentException("Invalid inventory size (" + items.length + "); expected " + size + " or less");
        }
        for (int slot = 0; slot < size; slot++) {
            setItem(slot, slot < items.length ? items[slot] : null);
        }
    }

    @Override
    public ItemStack[] getStorageContents() {
        return getContents();
    }

    @Override
    public void setStorageContents(ItemStack[] items) {
        setContents(items);
    }

    @Override
    public boolean contains(Material material) {
        return first(material) != -1;
    }

    @Override
    public boolean contains(ItemStack item) {
        return item != null && first(item) != -1;
    }

    @Override
    public boolean contains(Material material, int amount) {
        if (amount <= 0) {
            return true;
        }
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = getItem(slot);
            if (!empty(stack) && stack.getType() == material && (amount -= stack.getAmount()) <= 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(ItemStack item, int amount) {
        if (item == null) {
            return false;
        }
        if (amount <= 0) {
            return true;
        }
        for (int slot = 0; slot < size; slot++) {
            if (item.equals(getItem(slot)) && --amount <= 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsAtLeast(ItemStack item, int amount) {
        if (item == null) {
            return false;
        }
        if (amount <= 0) {
            return true;
        }
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = getItem(slot);
            if (!empty(stack) && stack.isSimilar(item) && (amount -= stack.getAmount()) <= 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public HashMap<Integer, ItemStack> all(Material material) {
        HashMap<Integer, ItemStack> found = new HashMap<>();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = getItem(slot);
            if (!empty(stack) && stack.getType() == material) {
                found.put(slot, stack);
            }
        }
        return found;
    }

    @Override
    public HashMap<Integer, ItemStack> all(ItemStack item) {
        HashMap<Integer, ItemStack> found = new HashMap<>();
        if (item == null) {
            return found;
        }
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = getItem(slot);
            if (item.equals(stack)) {
                found.put(slot, stack);
            }
        }
        return found;
    }

    @Override
    public int first(Material material) {
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = getItem(slot);
            if (!empty(stack) && stack.getType() == material) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public int first(ItemStack item) {
        if (item == null) {
            return -1;
        }
        for (int slot = 0; slot < size; slot++) {
            if (item.equals(getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public int firstEmpty() {
        for (int slot = 0; slot < size; slot++) {
            if (empty(getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public boolean isEmpty() {
        return firstOccupied() == -1;
    }

    private int firstOccupied() {
        for (int slot = 0; slot < size; slot++) {
            if (!empty(getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public void remove(Material material) {
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = getItem(slot);
            if (!empty(stack) && stack.getType() == material) {
                clear(slot);
            }
        }
    }

    @Override
    public void remove(ItemStack item) {
        if (item == null) {
            return;
        }
        for (int slot = 0; slot < size; slot++) {
            if (item.equals(getItem(slot))) {
                clear(slot);
            }
        }
    }

    @Override
    public void clear(int index) {
        setItem(index, null);
    }

    @Override
    public void clear() {
        for (int slot = 0; slot < size; slot++) {
            clear(slot);
        }
    }

    @Override
    public int close() {
        List<HumanEntity> viewers = getViewers();
        for (HumanEntity viewer : viewers) {
            viewer.closeInventory();
        }
        return viewers.size();
    }

    @Override
    public List<HumanEntity> getViewers() {
        Set<HumanEntity> viewers = new LinkedHashSet<>();
        for (Inventory page : pages) {
            viewers.addAll(page.getViewers());
        }
        return new ArrayList<>(viewers);
    }

    @Override
    public InventoryType getType() {
        return InventoryType.CHEST;
    }

    /** Pas de bloc à mettre à jour derrière : ChestShop ne touche ainsi jamais à l'inventaire du bloc posé. */
    @Override
    public InventoryHolder getHolder() {
        return null;
    }

    @Override
    public InventoryHolder getHolder(boolean useSnapshot) {
        return null;
    }

    @Override
    public Location getLocation() {
        return location.clone();
    }

    @Override
    public ListIterator<ItemStack> iterator() {
        return new Slots(0);
    }

    @Override
    public ListIterator<ItemStack> iterator(int index) {
        // Même convention que Paper : un index négatif part de la fin
        return new Slots(index < 0 ? index + size + 1 : index);
    }

    private final class Slots implements ListIterator<ItemStack> {

        private int next;
        private int last = -1;

        private Slots(int start) {
            if (start < 0 || start > size) {
                throw new IndexOutOfBoundsException(start);
            }
            next = start;
        }

        @Override
        public boolean hasNext() {
            return next < size;
        }

        @Override
        public ItemStack next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            last = next++;
            return getItem(last);
        }

        @Override
        public boolean hasPrevious() {
            return next > 0;
        }

        @Override
        public ItemStack previous() {
            if (!hasPrevious()) {
                throw new NoSuchElementException();
            }
            last = --next;
            return getItem(last);
        }

        @Override
        public int nextIndex() {
            return next;
        }

        @Override
        public int previousIndex() {
            return next - 1;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Can't change the size of an inventory!");
        }

        @Override
        public void set(ItemStack item) {
            if (last == -1) {
                throw new IllegalStateException("No current item!");
            }
            setItem(last, item);
        }

        @Override
        public void add(ItemStack item) {
            throw new UnsupportedOperationException("Can't change the size of an inventory!");
        }
    }
}
