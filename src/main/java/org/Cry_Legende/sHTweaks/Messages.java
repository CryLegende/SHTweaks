package org.Cry_Legende.sHTweaks;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Messages du chat, lus dans messages.yml. Un message vide n'est pas envoyé. Une clé absente du fichier du serveur
 * reprend le texte livré dans le jar.
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().build();

    private final YamlConfiguration file;

    private Messages(YamlConfiguration file) {
        this.file = file;
    }

    /** Lit messages.yml. Un fichier illisible lève l'erreur : l'appelant garde ses textes actuels. */
    static Messages load(Plugin plugin, File source) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(source);
        yaml.setDefaults(bundledYaml(plugin));
        return new Messages(yaml);
    }

    /** Les textes livrés dans le jar, seuls. */
    static Messages bundled(Plugin plugin) {
        YamlConfiguration yaml = bundledYaml(plugin);
        yaml.setDefaults(bundledYaml(plugin));
        return new Messages(yaml);
    }

    private static YamlConfiguration bundledYaml(Plugin plugin) {
        var in = plugin.getResource("messages.yml");
        return in == null ? new YamlConfiguration()
            : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** Envoie le message « key », chaque paire de « replacements » remplaçant {nom} par sa valeur. */
    public void send(CommandSender to, String key, String... replacements) {
        // getString(key) et non getString(key, défaut) : seul le premier reprend le texte du jar
        String text = file.getString(key);
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        to.sendMessage(LEGACY.deserialize(text));
    }
}
