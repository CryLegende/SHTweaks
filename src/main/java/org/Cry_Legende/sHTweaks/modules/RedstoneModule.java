package org.Cry_Legende.sHTweaks.modules;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.handlers.GridManager;
import com.bgsoftware.superiorskyblock.api.island.Island;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.CopperBulb;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Relance la redstone que SuperiorSkyblock2 a refusée pendant qu'aucun membre de l'île n'était connecté
 * (disable-redstone-offline). Le jeu ne retente jamais un allumage refusé : un répéteur reste éteint alors que
 * le courant arrive, une horloge perd son impulsion et s'arrête, jusqu'à ce qu'un joueur casse et repose le bloc.
 * <p>
 * Chaque bloc refusé est noté, avec les données du chunk quand celui-ci se décharge (la liste survit aux
 * redémarrages), puis rejoué dès que son île a de nouveau un membre connecté et que son chunk tourne.
 */
public final class RedstoneModule extends Module {

    /** Courant demandé avant le passage des autres plugins, relu en MONITOR (un par thread de monde). */
    private static final class Requested {
        BlockRedstoneEvent event;
        int current;
    }

    private record ChunkRef(UUID world, int x, int z) {
        static ChunkRef of(Chunk chunk) {
            return new ChunkRef(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }

    private final ThreadLocal<Requested> requested = ThreadLocal.withInitial(Requested::new);
    /** Blocs refusés des chunks chargés, positions compactées dans le chunk. */
    private final Map<ChunkRef, Set<Integer>> pending = new ConcurrentHashMap<>();

    private NamespacedKey key;
    private GridManager grid;
    private int maxPerChunk;
    private boolean log;
    private BukkitTask sweep;

    @Override
    protected void load(ConfigurationSection config) {
        key = new NamespacedKey(plugin, "redstone_pending");
        grid = SuperiorSkyblockAPI.getGrid();
        maxPerChunk = Math.max(1, config.getInt("max-per-chunk", 1024));
        long interval = Math.max(1, config.getLong("interval-ticks", 20));
        log = config.getBoolean("log", true);
        // Rechargement : relit les chunks déjà chargés
        for (World world : plugin.getServer().getWorlds()) {
            if (!grid.isIslandsWorld(world)) {
                continue;
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                readChunk(chunk);
            }
        }
        sweep = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweepActiveIslands, interval, interval);
    }

    @Override
    protected void unload() {
        if (sweep != null) {
            sweep.cancel();
            sweep = null;
        }
        for (Map.Entry<ChunkRef, Set<Integer>> entry : pending.entrySet()) {
            ChunkRef ref = entry.getKey();
            World world = plugin.getServer().getWorld(ref.world());
            if (world != null && world.isChunkLoaded(ref.x(), ref.z())) {
                writeChunk(world.getChunkAt(ref.x(), ref.z()), entry.getValue());
            }
        }
        pending.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRedstoneRequested(BlockRedstoneEvent event) {
        Requested r = requested.get();
        r.event = event;
        r.current = event.getNewCurrent();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstoneApplied(BlockRedstoneEvent event) {
        Requested r = requested.get();
        if (r.event != event) {
            return;
        }
        r.event = null;
        if (event.getNewCurrent() >= r.current) {
            return;
        }
        Block block = event.getBlock();
        if (!replayable(block)) {
            return;
        }
        Island island = SuperiorSkyblockAPI.getIslandAt(block.getLocation());
        if (island == null || island.isSpawn() || island.isCurrentlyActive()) {
            return;
        }
        Set<Integer> blocks = pending.computeIfAbsent(ChunkRef.of(block.getChunk()), ref -> ConcurrentHashMap.newKeySet());
        if (blocks.size() < maxPerChunk) {
            blocks.add(pack(block.getX(), block.getY(), block.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() && grid.isIslandsWorld(event.getWorld())) {
            readChunk(event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!grid.isIslandsWorld(event.getWorld())) {
            return;
        }
        Set<Integer> blocks = pending.remove(ChunkRef.of(event.getChunk()));
        writeChunk(event.getChunk(), blocks);
    }

    /** Charge en mémoire les blocs notés dans le chunk. */
    private void readChunk(Chunk chunk) {
        int[] stored = chunk.getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        if (stored == null || stored.length == 0) {
            return;
        }
        Set<Integer> blocks = pending.computeIfAbsent(ChunkRef.of(chunk), ref -> ConcurrentHashMap.newKeySet());
        for (int packed : stored) {
            blocks.add(packed);
        }
    }

    private void writeChunk(Chunk chunk, Set<Integer> blocks) {
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        if (blocks == null || blocks.isEmpty()) {
            if (data.has(key)) {
                data.remove(key);
            }
            return;
        }
        data.set(key, PersistentDataType.INTEGER_ARRAY, blocks.stream().mapToInt(Integer::intValue).toArray());
    }

    /**
     * Rejoue les blocs notés dont l'île a de nouveau un membre connecté. Thread principal, pendant que les mondes
     * ne tournent pas. Un chunk en bordure de chargement attend de tourner : ses voisins doivent être là.
     */
    private void sweepActiveIslands() {
        if (pending.isEmpty()) {
            return;
        }
        int replayed = 0;
        for (ChunkRef ref : List.copyOf(pending.keySet())) {
            Set<Integer> blocks = pending.get(ref);
            World world = plugin.getServer().getWorld(ref.world());
            if (blocks == null || world == null || !world.isChunkLoaded(ref.x(), ref.z())) {
                continue;
            }
            Chunk chunk = world.getChunkAt(ref.x(), ref.z());
            Chunk.LoadLevel level = chunk.getLoadLevel();
            if (level != Chunk.LoadLevel.TICKING && level != Chunk.LoadLevel.ENTITY_TICKING) {
                continue;
            }
            int before = blocks.size();
            for (Integer packed : List.copyOf(blocks)) {
                Block block = world.getBlockAt((ref.x() << 4) | (packed & 15), unpackY(packed), (ref.z() << 4) | ((packed >> 4) & 15));
                Island island = SuperiorSkyblockAPI.getIslandAt(block.getLocation());
                if (island != null && !island.isCurrentlyActive()) {
                    continue;
                }
                blocks.remove(packed);
                if (island != null && replay(block)) {
                    replayed++;
                }
            }
            if (blocks.size() != before) {
                if (blocks.isEmpty()) {
                    pending.remove(ref, blocks);
                }
                writeChunk(chunk, blocks);
            }
        }
        if (log && replayed > 0) {
            plugin.getLogger().info("Redstone relancée sur les îles actives : " + replayed + " bloc(s).");
        }
    }

    private static boolean replayable(Block block) {
        return switch (block.getType()) {
            case REPEATER, COMPARATOR, OBSERVER, REDSTONE_TORCH, REDSTONE_WALL_TORCH, REDSTONE_WIRE, REDSTONE_LAMP,
                 POWERED_RAIL, ACTIVATOR_RAIL -> true;
            default -> {
                BlockData data = block.getBlockData();
                yield data instanceof Door || data instanceof TrapDoor || data instanceof Gate || data instanceof CopperBulb;
            }
        };
    }

    /**
     * Refait le changement refusé, avec la règle du jeu pour ce bloc. Les blocs qui ne sont plus dans l'état
     * laissé par le refus (cassés, remplacés, déjà rallumés) ne bougent pas.
     *
     * @return true si le bloc a été rejoué
     */
    private static boolean replay(Block block) {
        BlockData data = block.getBlockData();
        switch (block.getType()) {
            case REPEATER, OBSERVER -> {
                // Le tick allume le bloc, puis programme son extinction si l'entrée est retombée : l'impulsion perdue repart
                if (((Powerable) data).isPowered()) {
                    return false;
                }
                block.tick();
                return true;
            }
            case REDSTONE_TORCH, REDSTONE_WALL_TORCH -> {
                if (((Lightable) data).isLit()) {
                    return false;
                }
                block.tick();
                return true;
            }
            case COMPARATOR -> {
                // Le comparateur garde la valeur refusée et ne la recompare plus en soustraction :
                // reposé, il repart de zéro et le tick recalcule sa sortie
                if (((Powerable) data).isPowered()) {
                    return false;
                }
                replace(block, data, false);
                block.tick();
                return true;
            }
            case REDSTONE_WIRE -> {
                // Reposée, la poudre recalcule tout son réseau
                replace(block, data, true);
                return true;
            }
            case POWERED_RAIL, ACTIVATOR_RAIL -> {
                if (((Powerable) data).isPowered()) {
                    return false;
                }
                replace(block, data, true);
                return true;
            }
            case REDSTONE_LAMP -> {
                Lightable lamp = (Lightable) data;
                if (lamp.isLit() || !block.isBlockIndirectlyPowered()) {
                    return false;
                }
                lamp.setLit(true);
                block.setBlockData(lamp, true);
                return true;
            }
            default -> {
            }
        }
        if (data instanceof CopperBulb bulb) {
            // Front montant perdu : l'ampoule bascule, puis suit le courant actuel
            if (bulb.isPowered()) {
                return false;
            }
            bulb.setLit(!bulb.isLit());
            bulb.setPowered(block.isBlockIndirectlyPowered());
            block.setBlockData(bulb, true);
            return true;
        }
        if (data instanceof Door) {
            Block lower = ((Door) data).getHalf() == Bisected.Half.BOTTOM ? block : block.getRelative(BlockFace.DOWN);
            Block upper = lower.getRelative(BlockFace.UP);
            if (!(lower.getBlockData() instanceof Door door) || !(upper.getBlockData() instanceof Door)) {
                return false;
            }
            boolean signal = lower.isBlockIndirectlyPowered() || upper.isBlockIndirectlyPowered();
            if (!signal || door.isPowered()) {
                return false;
            }
            door.setPowered(true);
            door.setOpen(true);
            // La moitié du haut suit par la mise à jour des formes
            lower.setBlockData(door, true);
            return true;
        }
        if (data instanceof TrapDoor || data instanceof Gate) {
            Powerable powerable = (Powerable) data;
            if (powerable.isPowered() || !block.isBlockIndirectlyPowered()) {
                return false;
            }
            powerable.setPowered(true);
            ((Openable) powerable).setOpen(true);
            block.setBlockData(powerable, true);
            return true;
        }
        return false;
    }

    /** Retire puis repose le bloc, sans objet lâché ni événement. */
    private static void replace(Block block, BlockData data, boolean applyPhysics) {
        block.setType(Material.AIR, false);
        block.setBlockData(data, applyPhysics);
    }

    private static int pack(int x, int y, int z) {
        return ((y + 2048) & 0xFFF) << 8 | (z & 15) << 4 | (x & 15);
    }

    private static int unpackY(int packed) {
        return ((packed >> 8) & 0xFFF) - 2048;
    }
}
