package org.Cry_Legende.sHTweaks.modules;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
 * Pancartes dans les mondes configurés : une pancarte vierge s'écrit librement ; une pancarte déjà écrite
 * ne se modifie qu'avec shtweaks.signedit.use, sur une île dont on est membre.
 * Ailleurs, seule shtweaks.signedit.bypass permet d'éditer ; elle lève aussi toutes les règles.
 */
public final class SignEditModule extends Module {

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
            send(player, refusal);
        }
    }

    /** null si l'édition est autorisée, sinon la raison du refus. */
    private String refusal(Player player, Block block) {
        if (has(player, "bypass")) {
            return null;
        }
        if (!worlds.contains(block.getWorld().getName())) {
            return SILENT;
        }
        Island island = SuperiorSkyblockAPI.getIslandAt(block.getLocation());
        if (island != null && !island.isMember(SuperiorSkyblockAPI.getPlayer(player))) {
            return "not-your-island";
        }
        if (isBlank(block)) {
            return null;
        }
        return has(player, "use") ? null : "vip-required";
    }

    /** Pancarte encore vierge, recto et verso. */
    private static boolean isBlank(Block block) {
        if (!(block.getState(false) instanceof Sign sign)) {
            return false;
        }
        for (Side side : Side.values()) {
            for (Component line : sign.getSide(side).lines()) {
                if (!PlainTextComponentSerializer.plainText().serialize(line).isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }
}
