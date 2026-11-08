package org.Cry_Legende.sHTweaks;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/**
 * Un réglage de gameplay : sa section de config.yml (modules.&lt;id&gt;) est lue à chaque activation,
 * ses écouteurs sont retirés quand il est coupé ou rechargé.
 */
public abstract class Module implements Listener {

    protected SHTweaks plugin;

    /** Lit la configuration du module ; appelé avant l'enregistrement des écouteurs. */
    protected abstract void load(ConfigurationSection config);

    /** Appelé quand le module est coupé (rechargement, arrêt du serveur). */
    protected void unload() {
    }

    final void enable(SHTweaks plugin, ConfigurationSection config) {
        this.plugin = plugin;
        load(config);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    final void disable() {
        HandlerList.unregisterAll(this);
        unload();
    }
}
