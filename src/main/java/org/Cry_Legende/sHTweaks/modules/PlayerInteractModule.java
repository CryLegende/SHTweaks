package org.Cry_Legende.sHTweaks.modules;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.logging.Level;

/**
 * Shift + clic droit sur un autre joueur : lance les commandes configurées, dans les mondes en liste blanche et hors
 * des régions WorldGuard en liste noire. Le clic est annulé quand les commandes ont été lancées. Réservé à ceux qui ont
 * shtweaks.playerinteract.use.
 */
public final class PlayerInteractModule extends Module {

    private boolean debug;
    private List<String> worlds;
    private List<String> regions;
    private List<String> playerCommands;
    private List<String> consoleCommands;

    @Override
    protected void load(ConfigurationSection config) {
        debug = config.getBoolean("debug", false);
        worlds = list(config, "whitelist-worlds", List.of("spawn", "SuperiorWorld"));
        regions = list(config, "blacklist-regions", List.of("1vs1", "minikoth"));
        playerCommands = list(config, "player-commands", List.of("infos %player%"));
        consoleCommands = list(config, "console-commands", List.of());

        if (worlds.isEmpty()) {
            plugin.getLogger().warning(path("whitelist-worlds") + " est vide : le shift + clic droit ne marchera dans aucun monde.");
        }
        if (debug) {
            plugin.getLogger().info("playerinteract : mondes " + String.join(", ", worlds) + " ; régions interdites "
                + String.join(", ", regions) + " ; commandes joueur " + String.join(", ", playerCommands)
                + " ; commandes console " + String.join(", ", consoleCommands));
        }
    }

    /** Liste de config.yml ; la clé absente donne la valeur par défaut, une liste vide reste vide. */
    private static List<String> list(ConfigurationSection config, String key, List<String> fallback) {
        return config.isSet(key) ? List.copyOf(config.getStringList(key)) : fallback;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        // Main principale seulement, pour éviter une double exécution
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player clicker = event.getPlayer();

        if (!clicker.isSneaking()) {
            return;
        }

        if (!(event.getRightClicked() instanceof Player clickedPlayer)) {
            return;
        }

        // Le joueur cliqué doit être un joueur en ligne : on exclut les PNJ
        if (!Bukkit.getOnlinePlayers().contains(clickedPlayer)) {
            return;
        }

        if (!has(clicker, "use")) {
            if (debug) {
                plugin.getLogger().info("Le joueur " + clicker.getName() + " ne dispose pas de la permission d'utiliser playerinteract");
            }
            return;
        }

        World world = clicker.getWorld();
        if (!worlds.contains(world.getName())) {
            if (debug) {
                plugin.getLogger().info("Le monde " + world.getName() + " n'est pas en whitelist");
            }
            return;
        }

        if (isInBlacklistedRegion(clicker)) {
            if (debug) {
                plugin.getLogger().info("Le joueur " + clicker.getName() + " se trouve dans une région blacklistée");
            }
            return;
        }

        executeCommands(clicker, clickedPlayer);

        // Annule l'interaction (empêche, par exemple, l'ouverture d'un inventaire)
        event.setCancelled(true);
    }

    private boolean isInBlacklistedRegion(Player player) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet applicable = query.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));

            for (String blacklistedRegion : regions) {
                if (applicable.getRegions().stream().anyMatch(region -> region.getId().equalsIgnoreCase(blacklistedRegion))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur de lecture des régions WorldGuard (playerinteract)", e);
            return false;
        }
    }

    private void executeCommands(Player executor, Player target) {
        String targetName = target.getName();

        for (String cmd : playerCommands) {
            String command = cmd.replace("%player%", targetName);
            if (debug) {
                plugin.getLogger().info("Le joueur " + executor.getName() + " lance : " + command);
            }
            executor.performCommand(command);
        }

        for (String cmd : consoleCommands) {
            String command = cmd.replace("%player%", targetName);
            if (debug) {
                plugin.getLogger().info("La console lance : " + command);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }
}
