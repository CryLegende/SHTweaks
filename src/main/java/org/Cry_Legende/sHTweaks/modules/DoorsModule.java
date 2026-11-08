package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Portes, trappes et portillons reprennent leur état d'origine après un délai dans les mondes configurés.
 * Les deux battants d'une double porte, ou deux trappes identiques côte à côte, s'ouvrent ensemble.
 * L'état du partenaire est relevé avant tous les plugins : si DoorsReloaded le bouge aussi, il lui donne
 * le même état et le partenaire reprend quand même son état d'origine avec le bloc cliqué.
 * Les portes et trappes en fer ne s'ouvrent pas à la main : elles sont ignorées.
 */
public final class DoorsModule extends Module {

    /** Reset en attente : la tâche et l'état à remettre (celui d'avant le premier clic de la série). */
    private record Pending(BukkitTask task, BlockData initial) {
    }

    /** Clic relevé avant tous les plugins : le bloc, son partenaire éventuel et leurs états d'avant. */
    private record Snapshot(PlayerInteractEvent event, Block clicked, BlockData clickedBefore,
                            Block partner, BlockData partnerBefore) {
    }

    /** Position du partenaire d'une porte (décalage x/z, charnière et orientation de la porte cliquée). */
    private record PartnerRule(int dx, int dz, Door.Hinge hinge, BlockFace facing) {
    }

    private static final PartnerRule[] PARTNER_RULES = {
            new PartnerRule(0, -1, Door.Hinge.RIGHT, BlockFace.EAST),
            new PartnerRule(0, 1, Door.Hinge.LEFT, BlockFace.EAST),
            new PartnerRule(1, 0, Door.Hinge.RIGHT, BlockFace.SOUTH),
            new PartnerRule(-1, 0, Door.Hinge.LEFT, BlockFace.SOUTH),
            new PartnerRule(0, 1, Door.Hinge.RIGHT, BlockFace.WEST),
            new PartnerRule(0, -1, Door.Hinge.LEFT, BlockFace.WEST),
            new PartnerRule(-1, 0, Door.Hinge.RIGHT, BlockFace.NORTH),
            new PartnerRule(1, 0, Door.Hinge.LEFT, BlockFace.NORTH)
    };

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final Map<Location, Pending> pending = new HashMap<>();
    private Snapshot snapshot;
    private int delayTicks;
    private Set<String> worlds;
    private boolean pairDoors;
    private boolean pairTrapdoors;
    private boolean pairsOnlyInWorlds;

    @Override
    protected void load(ConfigurationSection config) {
        int seconds = config.getInt("delay-seconds", 3);
        if (seconds <= 0) {
            plugin.getLogger().warning(path("delay-seconds") + " doit être positif, 3 secondes utilisées.");
            seconds = 3;
        }
        delayTicks = seconds * 20;
        worlds = new HashSet<>(config.getStringList("worlds"));
        pairDoors = config.getBoolean("pairs.doors", true);
        pairTrapdoors = config.getBoolean("pairs.trapdoors", true);
        pairsOnlyInWorlds = config.getBoolean("pairs.only-in-worlds", false);
    }

    /** Remet tout de suite les blocs en attente : une porte ouverte juste avant l'arrêt resterait ouverte. */
    @Override
    protected void unload() {
        for (Map.Entry<Location, Pending> entry : pending.entrySet()) {
            entry.getValue().task().cancel();
            revert(entry.getKey(), entry.getValue().initial(), false);
        }
        pending.clear();
        snapshot = null;
    }

    /** Avant tous les plugins : état du bloc cliqué et de son partenaire, que DoorsReloaded n'a pas encore bougé. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void capture(PlayerInteractEvent event) {
        snapshot = null;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !(event.getClickedBlock().getBlockData() instanceof Openable)) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked.getType() == Material.IRON_DOOR || clicked.getType() == Material.IRON_TRAPDOOR) {
            return;
        }
        Block partner = findPartner(clicked);
        snapshot = new Snapshot(event, clicked, clicked.getBlockData().clone(), partner,
                partner == null ? null : partner.getBlockData().clone());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Snapshot shot = snapshot;
        snapshot = null;
        if (shot == null || shot.event() != event) {
            return;
        }
        Player player = event.getPlayer();
        // Accroupi avec un objet en main : le serveur pose un bloc contre la porte sans l'ouvrir
        if (has(player, "bypass") || player.isSneaking() && (!player.getInventory().getItemInMainHand().isEmpty()
                || !player.getInventory().getItemInOffHand().isEmpty())) {
            return;
        }
        boolean resetHere = worlds.contains(shot.clicked().getWorld().getName());
        boolean pairEnabled = shot.clickedBefore() instanceof TrapDoor ? pairTrapdoors : pairDoors;
        if (shot.partner() != null && pairEnabled && (resetHere || !pairsOnlyInWorlds)) {
            // Le bloc cliqué va basculer : son partenaire prend le même état
            Openable partner = (Openable) shot.partnerBefore().clone();
            partner.setOpen(!((Openable) shot.clickedBefore()).isOpen());
            shot.partner().setBlockData(partner, true);
        }
        if (!resetHere) {
            return;
        }
        schedule(shot.clicked(), shot.clickedBefore());
        if (shot.partner() != null) {
            schedule(shot.partner(), shot.partnerBefore());
        }
    }

    private void schedule(Block block, BlockData before) {
        Location location = block.getLocation();
        Pending previous = pending.remove(location);
        if (previous != null) {
            previous.task().cancel();
        }
        // Clics rapprochés : on revient à l'état d'avant le premier clic de la série
        BlockData target = previous != null ? previous.initial() : before;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(location);
            revert(location, target, true);
        }, delayTicks);
        pending.put(location, new Pending(task, target));
    }

    /** Remet le bloc dans son état d'avant s'il est toujours du même type et a changé. */
    private static void revert(Location location, BlockData target, boolean sound) {
        if (location.getWorld() == null || !location.isChunkLoaded()) {
            return;
        }
        Block block = location.getBlock();
        if (block.getType() == target.getMaterial() && !block.getBlockData().matches(target)) {
            block.setBlockData(target, true);
            if (sound) {
                playSound(block, target);
            }
        }
    }

    /** Partenaire d'une double porte (partie basse) ou trappe voisine de même type, dans le même état. */
    private static Block findPartner(Block clicked) {
        BlockData data = clicked.getBlockData();
        if (data instanceof Door door) {
            Block bottom = clicked;
            if (door.getHalf() == Bisected.Half.TOP) {
                bottom = clicked.getRelative(BlockFace.DOWN);
                if (bottom.getType() != clicked.getType() || !(bottom.getBlockData() instanceof Door lower)) {
                    return null;
                }
                door = lower;
            }
            for (PartnerRule rule : PARTNER_RULES) {
                if (door.getFacing() != rule.facing() || door.getHinge() != rule.hinge()) {
                    continue;
                }
                Block candidate = bottom.getRelative(rule.dx(), 0, rule.dz());
                if (candidate.getType() == bottom.getType() && candidate.getBlockData() instanceof Door other
                        && other.getHalf() == Bisected.Half.BOTTOM && other.getFacing() == door.getFacing()
                        && other.getHinge() != door.getHinge() && other.isOpen() == door.isOpen()) {
                    return candidate;
                }
            }
        } else if (data instanceof TrapDoor trapDoor) {
            for (BlockFace face : SIDES) {
                Block candidate = clicked.getRelative(face);
                if (candidate.getType() == clicked.getType() && candidate.getBlockData() instanceof TrapDoor other
                        && other.isOpen() == trapDoor.isOpen()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Son du bloc qui reprend son état, comme si un joueur l'avait actionné. */
    private static void playSound(Block block, BlockData state) {
        boolean open = ((Openable) state).isOpen();
        Material type = block.getType();
        String name = type.name();
        Sound sound;
        float pitch = 1.0f;
        if (name.endsWith("_TRAPDOOR")) {
            sound = type == Material.IRON_TRAPDOOR
                    ? (open ? Sound.BLOCK_IRON_TRAPDOOR_OPEN : Sound.BLOCK_IRON_TRAPDOOR_CLOSE)
                    : (open ? Sound.BLOCK_WOODEN_TRAPDOOR_OPEN : Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE);
            if (type != Material.IRON_TRAPDOOR) {
                pitch = open ? 1.15f : 0.85f;
            }
        } else if (name.endsWith("_DOOR")) {
            sound = type == Material.IRON_DOOR
                    ? (open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE)
                    : (open ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE);
        } else if (name.endsWith("_FENCE_GATE")) {
            sound = open ? Sound.BLOCK_FENCE_GATE_OPEN : Sound.BLOCK_FENCE_GATE_CLOSE;
        } else {
            return;
        }
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), sound, SoundCategory.BLOCKS, 1.0f, pitch);
    }
}
