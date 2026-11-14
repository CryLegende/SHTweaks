package org.Cry_Legende.sHTweaks.modules;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Contenu des coffres AdvancedChests pour le module chestshop, lu par réflexion comme dans CollectorLink : le plugin
 * n'est publié sur aucun dépôt Maven et son jar ne se redistribue pas.
 *
 * <p>Un coffre AdvancedChests garde ses objets dans ses pages, chacune une inventaire Bukkit dont la dernière ligne
 * porte les boutons. L'inventaire du bloc posé ne contient que des ficelles marquées (AdvancedChest-Special-String),
 * mises là pour que les entonnoirs déclenchent toujours l'événement de transfert : c'est pourtant elle que ChestShop
 * lit, d'où un shop toujours vide à l'achat et toujours plein à la vente.</p>
 */
final class AdvancedChestStorage {

    private static final String BASE = "us.lynuxcraft.deadsilenceiv.advancedchests.";

    /** Délai entre deux avertissements d'erreur de lecture, pour ne pas inonder la console. */
    private static final long WARN_EVERY_MILLIS = 60_000L;

    /** Ce qu'il y a à un endroit, du point de vue d'AdvancedChests. */
    enum State {
        /** Pas un coffre AdvancedChests : ChestShop fait comme d'habitude. */
        NONE,
        /** Coffre chargé, contenu disponible. */
        READY,
        /** Coffre pas encore chargé (son chargement vient d'être demandé) ou en train d'être déchargé. */
        LOADING,
        /** Vente ou compression en cours : AdvancedChests est en train de modifier ses pages. */
        BUSY,
        /** Coffre dont le type n'existe plus, d'un type non géré, ou API illisible. */
        UNAVAILABLE
    }

    record Lookup(State state, AdvancedChestInventory inventory) {

        private static final Lookup NONE = new Lookup(State.NONE, null);
        private static final Lookup LOADING = new Lookup(State.LOADING, null);
        private static final Lookup BUSY = new Lookup(State.BUSY, null);
        private static final Lookup UNAVAILABLE = new Lookup(State.UNAVAILABLE, null);
    }

    private final Logger logger;
    private final Class<?> normalChestType;
    private final Class<?> baseChest;
    private final ItemStack filler;
    private final MethodHandle manager;
    private final MethodHandle loadedAt;
    private final MethodHandle waitingAt;
    private final MethodHandle brokenAt;
    private final MethodHandle setup;
    private final MethodHandle uniqueId;
    private final MethodHandle chestType;
    private final MethodHandle unloaded;
    private final MethodHandle beingUnloaded;
    private final MethodHandle beingSold;
    private final MethodHandle beingCompressed;
    private final MethodHandle orderedPages;
    private final MethodHandle pageInventory;
    private final MethodHandle markToSave;
    private final MethodHandle resetFullChecker;
    private final MethodHandle title;
    private final MethodHandle titleRefreshable;
    private final MethodHandle titleUpdate;

    private volatile long lastWarning;

    /**
     * @throws ReflectiveOperationException si l'API n'a pas la forme attendue (version d'AdvancedChests différente)
     */
    AdvancedChestStorage(Plugin advancedChests, Logger logger) throws ReflectiveOperationException {
        this.logger = logger;
        ClassLoader loader = advancedChests.getClass().getClassLoader();
        Class<?> api = Class.forName(BASE + "AdvancedChestsAPI", true, loader);
        Class<?> managerType = Class.forName(BASE + "managers.ChestsManager", true, loader);
        Class<?> chest = Class.forName(BASE + "chest.AdvancedChest", true, loader);
        Class<?> chestTypeType = Class.forName(BASE + "chest.ChestType", true, loader);
        Class<?> page = Class.forName(BASE + "chest.gui.page.ChestPage", true, loader);
        Class<?> titleType = Class.forName(BASE + "chest.title.ChestTitle", true, loader);
        Class<?> provider = Class.forName(BASE + "services.chest.ChestsProvider", true, loader);
        normalChestType = Class.forName(BASE + "chest.NormalChestType", true, loader);
        baseChest = Class.forName(BASE + "chest.BaseChest", true, loader);

        // Chaque poignée est ramenée à des types Object pour un appel invokeExact sans adaptation.
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodType byLocation = MethodType.methodType(Object.class, Object.class, Location.class);
        MethodType test = MethodType.methodType(boolean.class, Object.class);
        manager = lookup.findStatic(api, "getChestManager", MethodType.methodType(managerType))
                .asType(MethodType.methodType(Object.class));
        loadedAt = lookup.findVirtual(managerType, "getAdvancedChest", MethodType.methodType(chest, Location.class))
                .asType(byLocation);
        waitingAt = lookup.findVirtual(managerType, "getNonLoadedChest", MethodType.methodType(chest, Location.class))
                .asType(byLocation);
        brokenAt = lookup.findVirtual(managerType, "getNonLoadableChest", MethodType.methodType(chest, Location.class))
                .asType(byLocation);
        setup = lookup.findVirtual(managerType, "setupChest", MethodType.methodType(void.class, UUID.class))
                .asType(MethodType.methodType(void.class, Object.class, UUID.class));
        uniqueId = lookup.findVirtual(chest, "getUniqueId", MethodType.methodType(UUID.class))
                .asType(MethodType.methodType(UUID.class, Object.class));
        chestType = lookup.findVirtual(chest, "getChestType", MethodType.methodType(chestTypeType))
                .asType(MethodType.methodType(Object.class, Object.class));
        unloaded = lookup.findVirtual(chest, "isUnloaded", MethodType.methodType(boolean.class)).asType(test);
        beingUnloaded = lookup.findVirtual(chest, "isBeingUnloaded", MethodType.methodType(boolean.class)).asType(test);
        beingSold = lookup.findVirtual(chest, "isBeingSold", MethodType.methodType(boolean.class)).asType(test);
        beingCompressed = lookup.findVirtual(chest, "isBeingCompressed", MethodType.methodType(boolean.class)).asType(test);
        orderedPages = lookup.findVirtual(chest, "getOrderedPages", MethodType.methodType(page.arrayType()))
                .asType(MethodType.methodType(Object[].class, Object.class));
        pageInventory = lookup.findVirtual(page, "getBukkitInventory", MethodType.methodType(Inventory.class))
                .asType(MethodType.methodType(Inventory.class, Object.class));
        markToSave = lookup.findVirtual(chest, "setShouldBeSaved", MethodType.methodType(void.class, boolean.class))
                .asType(MethodType.methodType(void.class, Object.class, boolean.class));
        resetFullChecker = lookup.findVirtual(baseChest, "resetFullChecker", MethodType.methodType(void.class))
                .asType(MethodType.methodType(void.class, Object.class));
        title = lookup.findVirtual(chest, "getChestTitle", MethodType.methodType(titleType))
                .asType(MethodType.methodType(Object.class, Object.class));
        titleRefreshable = lookup.findVirtual(titleType, "isUsingRefreshablePlaceholders", MethodType.methodType(boolean.class))
                .asType(test);
        titleUpdate = lookup.findVirtual(titleType, "update", MethodType.methodType(void.class))
                .asType(MethodType.methodType(void.class, Object.class));
        MethodHandle specialString = lookup.findStatic(provider, "getSpecialString", MethodType.methodType(ItemStack.class));
        ItemStack special;
        try {
            special = (ItemStack) specialString.invokeExact();
        } catch (Throwable throwable) {
            throw new ReflectiveOperationException("ChestsProvider.getSpecialString", throwable);
        }
        filler = special == null ? null : special.clone();
    }

    /** Le coffre AdvancedChests posé à cet endroit (position du bloc), et son contenu s'il est disponible. */
    Lookup find(Location block) {
        try {
            Object chests = (Object) manager.invokeExact();
            if (chests == null) {
                return Lookup.UNAVAILABLE;
            }
            Object chest = (Object) loadedAt.invokeExact(chests, block);
            if (chest == null) {
                Object waiting = (Object) waitingAt.invokeExact(chests, block);
                if (waiting != null) {
                    // Même demande que fait AdvancedChests quand on clique sur un coffre pas encore chargé
                    setup.invokeExact(chests, (UUID) uniqueId.invokeExact(waiting));
                    return Lookup.LOADING;
                }
                return (Object) brokenAt.invokeExact(chests, block) != null ? Lookup.UNAVAILABLE : Lookup.NONE;
            }
            if (!normalChestType.isInstance((Object) chestType.invokeExact(chest)) || !baseChest.isInstance(chest)) {
                return Lookup.UNAVAILABLE;
            }
            if ((boolean) unloaded.invokeExact(chest) || (boolean) beingUnloaded.invokeExact(chest)) {
                return Lookup.LOADING;
            }
            if ((boolean) beingSold.invokeExact(chest) || (boolean) beingCompressed.invokeExact(chest)) {
                return Lookup.BUSY;
            }
            Object[] pages = (Object[]) orderedPages.invokeExact(chest);
            Inventory[] inventories = new Inventory[pages.length];
            for (int i = 0; i < pages.length; i++) {
                Inventory inventory = pages[i] == null ? null : (Inventory) pageInventory.invokeExact(pages[i]);
                if (inventory == null) {
                    return Lookup.LOADING;
                }
                inventories[i] = inventory;
            }
            return new Lookup(State.READY, new AdvancedChestInventory(this, chest, block, inventories));
        } catch (Throwable throwable) {
            warn(throwable);
            return Lookup.UNAVAILABLE;
        }
    }

    /**
     * Après un achat ou une vente : coffre à sauvegarder, compteur « coffre plein » remis à zéro (les entonnoirs
     * le consultent avant d'essayer d'y verser) et hologramme recalculé, comme après une vente AdvancedChests.
     */
    void changed(Object chest) {
        try {
            markToSave.invokeExact(chest, true);
            resetFullChecker.invokeExact(chest);
            Object chestTitle = (Object) title.invokeExact(chest);
            if (chestTitle != null && (boolean) titleRefreshable.invokeExact(chestTitle)) {
                titleUpdate.invokeExact(chestTitle);
            }
        } catch (Throwable throwable) {
            warn(throwable);
        }
    }

    /** Ficelle de remplissage d'AdvancedChests (ne doit jamais servir d'objet de shop). */
    boolean isFiller(ItemStack item) {
        return filler != null && item != null && item.getType() == filler.getType() && item.isSimilar(filler);
    }

    private void warn(Throwable throwable) {
        long now = System.currentTimeMillis();
        if (now - lastWarning >= WARN_EVERY_MILLIS) {
            lastWarning = now;
            logger.warning("chestshop : lecture du coffre AdvancedChests impossible : " + throwable);
        }
    }
}
