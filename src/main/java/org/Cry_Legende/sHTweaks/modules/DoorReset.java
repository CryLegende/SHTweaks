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
 * Une double porte (ou deux trappes côte à côte) s'ouvre en même temps que celle qu'on clique.
 */
public final class DoorReset extends Module {

    private static final String BYPASS = "shinteractreset.bypass";

    /** Reset en attente : la tâche et l'état à remettre (celui d'avant le premier clic de la série). */
    private record Pending(BukkitTask task, BlockData initial) {
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

    private final Map<Location, Pending> pending = new HashMap<>();
    private int delayTicks;
    private Set<String> worlds;
    private boolean partnerSync;
    private boolean partnerOnlyInWorlds;

    @Override
    protected void load(ConfigurationSection config) {
        int seconds = config.getInt("delay-seconds", 3);
        if (seconds <= 0) {
            plugin.getLogger().warning("door-reset.delay-seconds doit être positif, 3 secondes utilisées.");
            seconds = 3;
        }
        delayTicks = seconds * 20;
        worlds = new HashSet<>(config.getStringList("worlds"));
        partnerSync = config.getBoolean("partner-sync.enabled", true);
        partnerOnlyInWorlds = config.getBoolean("partner-sync.only-in-worlds", false);
    }

    /** Remet tout de suite les blocs en attente : une porte ouverte juste avant l'arrêt resterait ouverte. */
    @Override
    protected void unload() {
        for (Map.Entry<Location, Pending> entry : pending.entrySet()) {
            entry.getValue().task().cancel();
            Location location = entry.getKey();
            if (location.getWorld() == null) {
                continue;
            }
            Block block = location.getBlock();
            BlockData initial = entry.getValue().initial();
            if (block.getType() == initial.getMaterial() && !block.getBlockData().matches(initial)) {
                block.setBlockData(initial, true);
            }
        }
        pending.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !(event.getClickedBlock().getBlockData() instanceof Openable openable)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS)) {
            return;
        }
        // Accroupi avec un objet en main : le serveur pose un bloc contre la porte sans l'ouvrir
        if (player.isSneaking() && (!player.getInventory().getItemInMainHand().isEmpty()
                || !player.getInventory().getItemInOffHand().isEmpty())) {
            return;
        }
        Block clicked = event.getClickedBlock();
        boolean resetHere = worlds.contains(clicked.getWorld().getName());
        BlockData before = clicked.getBlockData().clone();

        if (partnerSync && (resetHere || !partnerOnlyInWorlds)) {
            Block partner = findPartner(clicked, openable, before);
            if (partner != null && partner.getBlockData() instanceof Openable partnerData) {
                BlockData partnerBefore = partnerData.clone();
                // La porte cliquée va changer d'état : la partenaire prend le même
                boolean open = !openable.isOpen();
                if (partnerData.isOpen() != open) {
                    partnerData.setOpen(open);
                    partner.setBlockData(partnerData, true);
                    if (resetHere) {
                        schedule(partner, partnerBefore);
                    }
                }
            }
        }
        if (resetHere) {
            schedule(clicked, before);
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
            if (location.getWorld() == null) {
                return;
            }
            Block current = location.getBlock();
            if (current.getType() == target.getMaterial() && !current.getBlockData().matches(target)) {
                current.setBlockData(target, true);
                playSound(current, target);
            }
        }, delayTicks);
        pending.put(location, new Pending(task, target));
    }

    /** Partenaire d'une double porte (partie basse) ou trappe voisine de même type, dans le même état avant le clic. */
    private static Block findPartner(Block clicked, Openable data, BlockData before) {
        boolean wasOpen = ((Openable) before).isOpen();
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
                if (candidate.getType() != bottom.getType() || !(candidate.getBlockData() instanceof Door other)) {
                    continue;
                }
                if (other.getHalf() == Bisected.Half.TOP) {
                    candidate = candidate.getRelative(BlockFace.DOWN);
                    if (candidate.getType() != bottom.getType() || !(candidate.getBlockData() instanceof Door lowerOther)) {
                        continue;
                    }
                    other = lowerOther;
                }
                if (other.getFacing() == door.getFacing() && other.getHinge() != door.getHinge() && other.isOpen() == wasOpen) {
                    return candidate;
                }
            }
        } else if (data instanceof TrapDoor) {
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                Block candidate = clicked.getRelative(face);
                if (candidate.getType() == clicked.getType() && candidate.getBlockData() instanceof TrapDoor other
                        && other.isOpen() == wasOpen) {
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
