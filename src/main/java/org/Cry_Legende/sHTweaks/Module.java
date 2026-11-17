package org.Cry_Legende.sHTweaks;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permissible;

/**
 * Un réglage de gameplay : sa section de config.yml (modules.&lt;id&gt;) est lue à chaque activation,
 * ses écouteurs sont retirés quand il est coupé ou rechargé.
 */
public abstract class Module implements Listener {

    protected SHTweaks plugin;
    private String id;

    /** Lit la configuration du module ; appelé avant l'enregistrement des écouteurs. */
    protected abstract void load(ConfigurationSection config);

    /** Appelé quand le module est coupé (rechargement, arrêt du serveur). */
    protected void unload() {
    }

    /** Permission du module : shtweaks.&lt;module&gt;.&lt;nom&gt;. */
    protected final boolean has(Permissible who, String permission) {
        return who.hasPermission("shtweaks." + id + "." + permission);
    }

    /** Message du module : clé &lt;module&gt;.&lt;nom&gt; de messages.yml. */
    protected final void send(CommandSender to, String key, String... replacements) {
        plugin.messages().send(to, id + "." + key, replacements);
    }

    /** Vrai quand le module démarre à cause de /shtweaks reload, sur un serveur qui tourne. */
    protected final boolean reloading() {
        return plugin.isReloading();
    }

    /** Clé de configuration complète, pour les avertissements. */
    protected final String path(String key) {
        return "modules." + id + "." + key;
    }

    final void enable(SHTweaks plugin, String id, ConfigurationSection config) {
        this.plugin = plugin;
        this.id = id;
        load(config);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    final void disable() {
        HandlerList.unregisterAll(this);
        unload();
    }
}
