package org.Cry_Legende.sHTweaks;

import org.Cry_Legende.sHTweaks.modules.AmbientSoundsModule;
import org.Cry_Legende.sHTweaks.modules.AmethystModule;
import org.Cry_Legende.sHTweaks.modules.AnvilsModule;
import org.Cry_Legende.sHTweaks.modules.ChestShopModule;
import org.Cry_Legende.sHTweaks.modules.CollecteursModule;
import org.Cry_Legende.sHTweaks.modules.CropsModule;
import org.Cry_Legende.sHTweaks.modules.DoorsModule;
import org.Cry_Legende.sHTweaks.modules.IslandBypassModule;
import org.Cry_Legende.sHTweaks.modules.ItemsModule;
import org.Cry_Legende.sHTweaks.modules.KittycannonModule;
import org.Cry_Legende.sHTweaks.modules.LavaWarnModule;
import org.Cry_Legende.sHTweaks.modules.PlayerInteractModule;
import org.Cry_Legende.sHTweaks.modules.RecountModule;
import org.Cry_Legende.sHTweaks.modules.RedstoneModule;
import org.Cry_Legende.sHTweaks.modules.SculkModule;
import org.Cry_Legende.sHTweaks.modules.SellWandConfirmModule;
import org.Cry_Legende.sHTweaks.modules.SignEditModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class SHTweaks extends JavaPlugin implements TabCompleter {

    /**
     * Module connu du plugin. Son nom sert de clé dans config.yml et messages.yml, et de segment
     * dans ses permissions (shtweaks.&lt;module&gt;.&lt;permission&gt;).
     *
     * @param factory         appelé seulement si les plugins requis sont là (ses classes ne sont pas chargées sinon)
     * @param requiredPlugins plugins sans lesquels le module reste coupé
     */
    private record Entry(String id, Supplier<Module> factory, String... requiredPlugins) {

        /** Premier plugin requis absent ou désactivé, ou null s'ils sont tous là. */
        String missingPlugin(PluginManager plugins) {
            for (String required : requiredPlugins) {
                if (!plugins.isPluginEnabled(required)) {
                    return required;
                }
            }
            return null;
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("items", ItemsModule::new),
            new Entry("anvils", AnvilsModule::new),
            new Entry("crops", CropsModule::new),
            new Entry("sculk", SculkModule::new),
            new Entry("amethyst", AmethystModule::new),
            new Entry("signedit", SignEditModule::new, "SuperiorSkyblock2"),
            new Entry("doors", DoorsModule::new),
            new Entry("islandbypass", IslandBypassModule::new, "SuperiorSkyblock2"),
            new Entry("redstone", RedstoneModule::new, "SuperiorSkyblock2"),
            new Entry("recount", RecountModule::new, "SuperiorSkyblock2"),
            new Entry("collecteurs", CollecteursModule::new, "AdvancedChests"),
            new Entry("chestshop", ChestShopModule::new, "ChestShop", "AdvancedChests"),
            new Entry("sellwandconfirm", SellWandConfirmModule::new, "WildTools"),
            new Entry("lavawarn", LavaWarnModule::new),
            new Entry("ambientsounds", AmbientSoundsModule::new),
            new Entry("playerinteract", PlayerInteractModule::new, "WorldGuard"),
            new Entry("kittycannon", KittycannonModule::new));

    /**
     * Modules qui étaient des plugins à part : sans section dans le config.yml du serveur, ils tournent avec les
     * réglages du config.yml du jar, comme le faisait le plugin.
     */
    private static final Set<String> ON_BY_DEFAULT = Set.of(
            "sellwandconfirm", "lavawarn", "ambientsounds", "playerinteract", "kittycannon");

    /** Sous-commandes de /shtweaks ; chacune a sa permission shtweaks.admin.&lt;sous-commande&gt;. */
    private static final List<String> SUBCOMMANDS = List.of("reload", "modules");

    private final Map<String, Module> active = new LinkedHashMap<>();
    // config.yml du serveur (avec les valeurs du jar pour les clés absentes) et messages : remplacés d'un bloc par reload
    private volatile YamlConfiguration config;
    private volatile Messages messages;
    private volatile boolean reloading;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        // Un fichier illisible n'arrête pas le plugin : les valeurs du jar servent jusqu'au prochain rechargement
        try {
            config = readConfig();
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("config.yml illisible, réglages du jar utilisés : " + firstLine(e));
            config = bundledConfig();
        }
        try {
            messages = Messages.load(this, messagesFile);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("messages.yml illisible, textes du jar utilisés : " + firstLine(e));
            messages = Messages.bundled(this);
        }
        PluginCommand command = getCommand("shtweaks");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        startModules();
        getLogger().info(active.size() + "/" + ENTRIES.size() + " module(s) actif(s) : " + String.join(", ", active.keySet()));
    }

    @Override
    public void onDisable() {
        stopModules();
    }

    public Messages messages() {
        return messages;
    }

    /** Vrai pendant /shtweaks reload : les modules savent qu'ils redémarrent sur un serveur qui tourne. */
    public boolean isReloading() {
        return reloading;
    }

    private YamlConfiguration bundledConfig() {
        var in = getResource("config.yml");
        return in == null ? new YamlConfiguration()
            : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** config.yml du serveur, lu sans rien avaler : une erreur de syntaxe est relancée. */
    private YamlConfiguration readConfig() throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(getDataFolder(), "config.yml"));
        yaml.setDefaults(bundledConfig());
        return yaml;
    }

    private static String firstLine(Exception e) {
        String text = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        int end = text.indexOf('\n');
        return (end < 0 ? text : text.substring(0, end)).replace("&", "").trim();
    }

    /** Section modules.&lt;id&gt; du serveur, ou celle du jar pour un module qui tourne sans section ; null sinon. */
    private ConfigurationSection sectionOf(String id) {
        String path = "modules." + id;
        if (config.contains(path, true)) {
            return config.getConfigurationSection(path);
        }
        return ON_BY_DEFAULT.contains(id) ? bundledConfig().getConfigurationSection(path) : null;
    }

    private void startModules() {
        for (Entry entry : ENTRIES) {
            ConfigurationSection section = sectionOf(entry.id());
            if (section == null || !section.getBoolean("enabled", ON_BY_DEFAULT.contains(entry.id()))) {
                continue;
            }
            String missing = entry.missingPlugin(getServer().getPluginManager());
            if (missing != null) {
                getLogger().warning("Module " + entry.id() + " coupé : " + missing + " absent.");
                continue;
            }
            try {
                Module module = entry.factory().get();
                module.enable(this, entry.id(), section);
                active.put(entry.id(), module);
            } catch (RuntimeException e) {
                getLogger().severe("Module " + entry.id() + " coupé, erreur au démarrage : " + e);
            }
        }
    }

    private void stopModules() {
        for (Map.Entry<String, Module> module : active.entrySet()) {
            try {
                module.getValue().disable();
            } catch (RuntimeException e) {
                getLogger().warning("Arrêt du module " + module.getKey() + " : " + e);
            }
        }
        active.clear();
    }

    /**
     * /shtweaks reload : config.yml et messages.yml sont lus tous les deux avant que quoi que ce soit change. Si l'un
     * est illisible, aucun module n'est arrêté et la configuration reste celle d'avant.
     */
    private void reload(CommandSender sender) {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        YamlConfiguration newConfig;
        try {
            newConfig = readConfig();
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().warning("config.yml illisible, configuration inchangée : " + firstLine(e));
            messages.send(sender, "command.reload-error", "file", "config.yml", "error", firstLine(e));
            return;
        }
        Messages newMessages;
        try {
            newMessages = Messages.load(this, messagesFile);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().warning("messages.yml illisible, configuration inchangée : " + firstLine(e));
            messages.send(sender, "command.reload-error", "file", "messages.yml", "error", firstLine(e));
            return;
        }
        stopModules();
        config = newConfig;
        messages = newMessages;
        reloading = true;
        try {
            startModules();
        } finally {
            reloading = false;
        }
        messages.send(sender, "command.reloaded", "active", String.valueOf(active.size()), "total", String.valueOf(ENTRIES.size()));
    }

    /** Sous-commandes que l'expéditeur a le droit d'utiliser. */
    private static List<String> allowed(CommandSender sender) {
        return SUBCOMMANDS.stream().filter(sub -> sender.hasPermission("shtweaks.admin." + sub)).toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<String> allowed = allowed(sender);
        if (allowed.isEmpty()) {
            messages.send(sender, "command.no-permission");
            return true;
        }
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (!allowed.contains(action)) {
            messages.send(sender, "command.usage-line", "subcommands", String.join("&7|&e", allowed));
            return true;
        }
        switch (action) {
            case "reload" -> reload(sender);
            case "modules" -> {
                messages.send(sender, "command.modules-header");
                for (Entry entry : ENTRIES) {
                    String missing = entry.missingPlugin(getServer().getPluginManager());
                    if (active.containsKey(entry.id())) {
                        messages.send(sender, "command.module-on", "module", entry.id());
                    } else if (missing != null) {
                        messages.send(sender, "command.module-missing", "module", entry.id(), "plugin", missing);
                    } else {
                        messages.send(sender, "command.module-off", "module", entry.id());
                    }
                }
            }
            default -> messages.send(sender, "command.usage-line", "subcommands", String.join("&7|&e", allowed));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        return allowed(sender).stream().filter(option -> option.startsWith(typed)).toList();
    }
}
