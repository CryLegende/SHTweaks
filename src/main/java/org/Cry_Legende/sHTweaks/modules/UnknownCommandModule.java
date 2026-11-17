package org.Cry_Legende.sHTweaks.modules;

import net.kyori.adventure.text.Component;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.command.UnknownCommandEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Deux réglages pour les commandes que Paper refuse à l'analyse :
 * <ul>
 *   <li>les espaces à la fin d'une commande de joueur sont retirés avant l'analyse : « /shop » suivi d'un espace
 *       donnait « Incorrect argument for command » alors que « /shop » marche (l'analyseur Brigadier lit l'espace
 *       comme le début d'un argument qui n'existe pas) ;</li>
 *   <li>le texte que Paper envoie pour une commande inconnue ou mal formée (celui de Mojang, en anglais, avec le
 *       « &lt;--[HERE] ») est remplacé par le message SkyHand (messages.yml, unknowncommand.message). Un message vide
 *       laisse le texte de Paper ; un spigot.yml dont unknown-command est vide garde son silence.</li>
 * </ul>
 */
public final class UnknownCommandModule extends Module {

    @Override
    protected void load(ConfigurationSection config) {
        // Rien à lire : le texte vient de messages.yml, au moment de la commande
    }

    /** Premier de la file : les autres plugins voient déjà la commande sans l'espace final. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String trimmed = message.stripTrailing();
        // Plus d'un caractère : « / » seul reste tel quel, Paper le refuse comme avant
        if (trimmed.length() > 1 && trimmed.length() != message.length()) {
            event.setMessage(trimmed);
        }
    }

    /** Priorité basse : un plugin qui règle lui-même le message (ou le coupe) passe après et garde la main. */
    @EventHandler(priority = EventPriority.LOW)
    public void onUnknown(UnknownCommandEvent event) {
        // null : spigot.yml (messages.unknown-command) est vide, c'est voulu
        if (event.message() == null) {
            return;
        }
        Component text = message("message");
        if (text != null) {
            event.message(text);
        }
    }
}
