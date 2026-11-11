package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Cat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * /kittycannon : lance un chat qui explose (permission shtweaks.kittycannon.use). La commande est déclarée par
 * SHTweaks : SHTweaks se charge après EssentialsX, dont /kittycannon reste joignable par /ekittycannon.
 */
public final class KittycannonModule extends Module implements CommandExecutor {

    private int babyCatChance;

    @Override
    protected void load(ConfigurationSection config) {
        // Chance de faire apparaître un bébé chat, en pourcentage
        babyCatChance = config.getInt("baby-cat-chance", 10);
        PluginCommand command = plugin.getCommand("kittycannon");
        if (command != null) {
            command.setExecutor(this);
        }
    }

    @Override
    protected void unload() {
        PluginCommand command = plugin.getCommand("kittycannon");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (has(player, "use")) {
                launchKitty(player);
            } else {
                send(player, "no-permission");
            }
            return true;
        }
        send(sender, "player-only");
        return false;
    }

    private void launchKitty(Player player) {
        // Positionner le chat un bloc au-dessus et à une distance légèrement réduite
        Location spawnLocation = player.getLocation().add(player.getLocation().getDirection().multiply(1.2)).add(0, 1, 0);
        Cat cat = (Cat) player.getWorld().spawnEntity(spawnLocation, EntityType.CAT);
        cat.setOwner(player);
        cat.setSitting(true);

        // Jouer le son de miaulement lorsque le chat apparaît avec un volume réduit
        cat.getWorld().playSound(cat.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.3F, 1.0F);

        // Générer un nombre aléatoire pour décider si le chat sera un bébé
        if (Math.random() * 100 < babyCatChance) {
            cat.setBaby();
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (cat.isValid()) {
                    Location catLocation = cat.getLocation();
                    cat.getWorld().spawnParticle(Particle.POOF, catLocation, 1, 0, 0, 0, 0);
                    cat.getWorld().playSound(catLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.1F, 1.6F);
                    cat.remove();
                }
            }
        }.runTaskLater(plugin, 40L); // 40L = 2 secondes (20 ticks par seconde)

        // Réduire la vélocité du chat pour le lancer moins fort
        cat.setVelocity(player.getLocation().getDirection().multiply(1.2));
    }

    /** Un chat lancé ne déclenche ni plaque de pression ni fil de déclenchement. */
    @EventHandler
    public void onEntityInteract(EntityInteractEvent event) {
        if (event.getEntity() instanceof Cat) {
            Material material = event.getBlock().getType();

            if (material.name().contains("PRESSURE_PLATE") || material.name().contains("TRIPWIRE")) {
                event.setCancelled(true);
            }
        }
    }
}
