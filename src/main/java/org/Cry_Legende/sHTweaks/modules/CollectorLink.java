package org.Cry_Legende.sHTweaks.modules;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Lien avec l'API d'AdvancedChests, par réflexion : le plugin n'est pas publié sur un dépôt Maven et son jar
 * ne se redistribue pas, le projet se compile donc sans lui.
 *
 * <p>AdvancedChests range ses coffres chargés par chunk ({@code ChestsManager#getAdvancedChests(ChunkLocation)},
 * une table concurrente) et ramasse les objets avec {@code AdvancedChest#canCollectItemsFromChunk()} : c'est
 * exactement la question posée ici. Rien n'est mis en cache, l'état lu est toujours celui du ramassage en cours,
 * y compris juste après un basculement dans le menu du coffre (AdvancedChests n'envoie aucun événement).</p>
 */
final class CollectorLink {

    private static final String BASE = "us.lynuxcraft.deadsilenceiv.advancedchests.";

    /** Délai entre deux avertissements d'erreur de lecture, pour ne pas inonder la console. */
    private static final long WARN_EVERY_MILLIS = 60_000L;

    private final Logger logger;
    private final MethodHandle manager;
    private final MethodHandle chunkOf;
    private final MethodHandle chestsIn;
    private final MethodHandle canCollect;

    private long lastWarning;

    private CollectorLink(Logger logger, MethodHandle manager, MethodHandle chunkOf,
                          MethodHandle chestsIn, MethodHandle canCollect) {
        this.logger = logger;
        this.manager = manager;
        this.chunkOf = chunkOf;
        this.chestsIn = chestsIn;
        this.canCollect = canCollect;
    }

    /**
     * @throws ReflectiveOperationException si l'API n'a pas la forme attendue (version d'AdvancedChests différente)
     */
    static CollectorLink connect(Plugin advancedChests, Logger logger) throws ReflectiveOperationException {
        ClassLoader loader = advancedChests.getClass().getClassLoader();
        Class<?> api = Class.forName(BASE + "AdvancedChestsAPI", true, loader);
        Class<?> managerType = Class.forName(BASE + "managers.ChestsManager", true, loader);
        Class<?> chunkType = Class.forName(BASE + "utils.ChunkLocation", true, loader);
        Class<?> chestType = Class.forName(BASE + "chest.AdvancedChest", true, loader);

        // Chaque poignée est ramenée à des types Object pour un appel invokeExact sans adaptation.
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle manager = lookup.findStatic(api, "getChestManager", MethodType.methodType(managerType))
                .asType(MethodType.methodType(Object.class));
        MethodHandle chunkOf = lookup.findConstructor(chunkType, MethodType.methodType(void.class, Location.class))
                .asType(MethodType.methodType(Object.class, Location.class));
        MethodHandle chestsIn = lookup.findVirtual(managerType, "getAdvancedChests", MethodType.methodType(Set.class, chunkType))
                .asType(MethodType.methodType(Set.class, Object.class, Object.class));
        MethodHandle canCollect = lookup.findVirtual(chestType, "canCollectItemsFromChunk", MethodType.methodType(boolean.class))
                .asType(MethodType.methodType(boolean.class, Object.class));
        return new CollectorLink(logger, manager, chunkOf, chestsIn, canCollect);
    }

    /**
     * Le chunk de cette position contient-il un coffre qui y ramasse les objets en ce moment ?
     * Un coût constant : une table de hachage et les quelques coffres du chunk, jamais un parcours des coffres.
     */
    boolean hasActiveCollector(Location at) {
        try {
            Object chests = (Object) manager.invokeExact();
            if (chests == null) {
                return false;
            }
            Set<?> inChunk = (Set<?>) chestsIn.invokeExact(chests, (Object) chunkOf.invokeExact(at));
            if (inChunk == null || inChunk.isEmpty()) {
                return false;
            }
            for (Object chest : inChunk) {
                if ((boolean) canCollect.invokeExact(chest)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable throwable) {
            warn(throwable);
            return false;
        }
    }

    private void warn(Throwable throwable) {
        long now = System.currentTimeMillis();
        if (now - lastWarning >= WARN_EVERY_MILLIS) {
            lastWarning = now;
            logger.warning("collecteurs : lecture des coffres AdvancedChests impossible : " + throwable);
        }
    }
}
