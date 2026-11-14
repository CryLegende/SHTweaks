package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.MoistureChangeEvent;

import java.util.Set;

/**
 * Terres labourées toujours humides dans les mondes choisis. Elles ne sèchent pas et ne redeviennent pas de la terre,
 * ni en séchant ni sous un bloc posé dessus. Une terre labourée sèche est mouillée dès qu'un bloc voisin change (elle
 * vient d'être labourée, une culture est récoltée...) ou qu'une voisine essaie de sécher. Le piétinement n'est pas
 * concerné.
 * <p>
 * Une terre déjà humide qui essaie de sécher ne change pas : rien n'est posé, rien n'est envoyé aux joueurs.
 */
public final class FarmlandModule extends Module {

    // Ordre des mises à jour de voisins du jeu
    private static final BlockFace[] NEIGHBOURS = {BlockFace.WEST, BlockFace.EAST, BlockFace.DOWN, BlockFace.UP,
            BlockFace.NORTH, BlockFace.SOUTH};

    private Set<String> worlds;

    @Override
    protected void load(ConfigurationSection config) {
        worlds = Set.copyOf(config.getStringList("worlds"));
    }

    private boolean applies(World world) {
        return worlds.isEmpty() || worlds.contains(world.getName());
    }

    /** Humidité au maximum, voisins prévenus : une voisine sèche reçoit l'évènement de physique et se mouille à son tour. */
    private static void wetIfDry(Block block) {
        if (block.getBlockData() instanceof Farmland data && data.getMoisture() < data.getMaximumMoisture()) {
            data.setMoisture(data.getMaximumMoisture());
            block.setBlockData(data, true);
        }
    }

    // Tick aléatoire loin de l'eau : la terre essaie de perdre un point d'humidité.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoistureChange(MoistureChangeEvent event) {
        Block block = event.getBlock();
        BlockState next = event.getNewState();
        if (!(next.getBlockData() instanceof Farmland wanted) || wanted.getMoisture() >= wanted.getMaximumMoisture()
                || !(block.getBlockData() instanceof Farmland current) || !applies(block.getWorld())) {
            return;
        }
        boolean drying = wanted.getMoisture() < current.getMoisture();
        if (current.getMoisture() >= current.getMaximumMoisture()) {
            event.setCancelled(true);
        } else {
            wanted.setMoisture(wanted.getMaximumMoisture());
            next.setBlockData(wanted);
        }
        if (!drying) {
            return;
        }
        for (BlockFace face : NEIGHBOURS) {
            Block neighbour = block.getRelative(face);
            if (neighbour.getType() == Material.FARMLAND) {
                wetIfDry(neighbour);
            }
        }
    }

    // Terre labourée qui redevient de la terre sans entité : sèche sans culture dessus, ou bloc plein posé dessus.
    @EventHandler
    public void onFade(BlockFadeEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.FARMLAND || !applies(block.getWorld())) {
            return;
        }
        event.setCancelled(true);
        wetIfDry(block);
    }

    // Changement de la terre labourée elle-même (labour compris) ou d'un de ses voisins. Le type vient des données de
    // l'évènement, sans lecture du monde : cet évènement passe pour chaque mise à jour de bloc du serveur.
    @EventHandler
    public void onPhysics(BlockPhysicsEvent event) {
        if (event.getChangedType() != Material.FARMLAND) {
            return;
        }
        Block block = event.getBlock();
        if (!applies(block.getWorld())) {
            return;
        }
        event.setCancelled(true);
        wetIfDry(block);
    }
}
