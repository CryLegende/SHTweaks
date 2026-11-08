package org.Cry_Legende.sHTweaks;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Messages du chat, lus dans messages.yml. Un message vide n'est pas envoyé.
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().build();

    private final YamlConfiguration file;

    Messages(File source) {
        this.file = YamlConfiguration.loadConfiguration(source);
    }

    /** Envoie le message « key », chaque paire de « replacements » remplaçant {nom} par sa valeur. */
    public void send(CommandSender to, String key, String... replacements) {
        String text = file.getString(key, "");
        if (text.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        to.sendMessage(LEGACY.deserialize(text));
    }
}
