package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockFormEvent;

/**
 * Prévient les joueurs proches quand de la lave se transforme en obsidienne : message (messages.yml, lavawarn.warning)
 * et son, dans un rayon autour du bloc. Le message n'est soumis à aucune permission.
 */
public final class LavaWarnModule extends Module {

    private Sound sound;
    private float volume;
    private float pitch;
    private double radius;

    @Override
    protected void load(ConfigurationSection config) {
        String soundName = config.getString("sound.name", "BLOCK_END_PORTAL_SPAWN");
        volume = (float) config.getDouble("sound.volume", 1.0);
        pitch = (float) config.getDouble("sound.pitch", 2.0);

        // Nom ou clé du son ; un nom inconnu retombe sur un son qui marche
        sound = Sounds.sound(soundName);
        if (sound == null) {
            plugin.getLogger().warning(path("sound.name") + " : son inconnu « " + soundName
                + " », ENTITY_EXPERIENCE_ORB_PICKUP utilisé.");
            sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }

        radius = config.getDouble("radius", 15.0);
        if (radius < 0) {
            plugin.getLogger().warning(path("radius") + " : le rayon ne peut pas être négatif, 15.0 utilisé.");
            radius = 15.0;
        }
    }

    @EventHandler
    public void onObsidianForm(BlockFormEvent event) {
        if (event.getNewState().getType() != Material.OBSIDIAN) {
            return;
        }
        Block obsidianBlock = event.getBlock();
        Location obsidianLocation = obsidianBlock.getLocation();

        // Rayon carré pour une comparaison plus efficace
        double radiusSquared = radius * radius;

        for (Player player : obsidianBlock.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(obsidianLocation) <= radiusSquared) {
                send(player, "warning",
                    "x", String.valueOf(obsidianLocation.getBlockX()),
                    "y", String.valueOf(obsidianLocation.getBlockY()),
                    "z", String.valueOf(obsidianLocation.getBlockZ()));
                // Le son est joué à la position du joueur pour une meilleure spatialisation
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }
}
