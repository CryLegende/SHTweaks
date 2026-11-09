package org.Cry_Legende.sHTweaks.modules;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Active le mode bypass de SuperiorSkyblock2 (/is admin bypass) à la première connexion qui suit le démarrage,
 * pour les joueurs qui ont sa permission. SuperiorSkyblock2 ne garde ce mode qu'en mémoire : il repart coupé
 * à chaque redémarrage. Un joueur qui le coupe le retrouve coupé à ses connexions suivantes.
 */
public final class IslandBypassModule extends Module {

    private static final String PERMISSION = "superior.admin.bypass";

    /** Joueurs dont le mode a déjà été réglé depuis l'activation du module. */
    private final Set<UUID> handled = new HashSet<>();

    @Override
    protected void load(ConfigurationSection config) {
        // Rechargement : les joueurs déjà connectés gardent le mode qu'ils ont choisi
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            handled.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(PERMISSION) || !handled.add(player.getUniqueId())) {
            return;
        }
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);
        if (!superiorPlayer.hasBypassModeEnabled()) {
            superiorPlayer.setBypassMode(true);
            send(player, "enabled");
        }
    }
}
