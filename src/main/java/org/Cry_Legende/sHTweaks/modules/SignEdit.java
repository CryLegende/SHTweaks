package org.Cry_Legende.sHTweaks.modules;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.SignChangeEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Pancartes : une pancarte vide s'écrit librement ; une pancarte déjà écrite ne se modifie qu'avec
 * shsignedit.use, sur une île dont on est membre. shsignedit.bypass lève toutes les règles.
 */
public final class SignEdit extends Module {

    /** Refus sans message (monde hors règle) ; les autres refus sont des clés de messages.yml. */
    private static final String SILENT = "";

    private Set<String> worlds;

    @Override
    protected void load(ConfigurationSection config) {
        worlds = new HashSet<>(config.getStringList("worlds"));
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String refusal = refusal(event.getPlayer(), event.getBlock());
        if (refusal != null) {
            event.setCancelled(true);
            notify(event.getPlayer(), refusal);
        }
    }

    /** Refuse l'éditeur avant que le joueur tape, selon les mêmes règles. */
    @EventHandler
    public void onOpenSign(PlayerOpenSignEvent event) {
        // Éditeur ouvert par un plugin (saisie de texte) : pas une édition de pancarte par le joueur
        if (event.getCause() == PlayerOpenSignEvent.Cause.PLUGIN) {
            return;
        }
        String refusal = refusal(event.getPlayer(), event.getSign().getBlock());
        if (refusal != null) {
            event.setCancelled(true);
            notify(event.getPlayer(), refusal);
        }
    }

    private void notify(Player player, String refusal) {
        if (!refusal.isEmpty()) {
            plugin.messages().send(player, refusal);
        }
    }

    /** null si l'édition est autorisée, sinon la raison du refus. */
    private String refusal(Player player, Block block) {
        if (player.hasPermission("shsignedit.bypass")) {
            return null;
        }
        if (!worlds.contains(block.getWorld().getName())) {
            return SILENT;
        }
        Island island = SuperiorSkyblockAPI.getIslandAt(block.getLocation());
        if (island != null && !island.isMember(SuperiorSkyblockAPI.getPlayer(player))) {
            return "sign-not-your-island";
        }
        if (isEmpty(block)) {
            return null;
        }
        return player.hasPermission("shsignedit.use") ? null : "sign-vip-required";
    }

    /** Pancarte encore vierge, recto et verso. */
    private static boolean isEmpty(Block block) {
        if (!(block.getState() instanceof Sign sign)) {
            return false;
        }
        for (Side side : Side.values()) {
            for (String line : sign.getSide(side).getLines()) {
                if (!line.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }
}
