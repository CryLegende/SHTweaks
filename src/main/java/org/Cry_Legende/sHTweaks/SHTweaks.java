package org.Cry_Legende.sHTweaks;

import org.Cry_Legende.sHTweaks.modules.AmethystNerf;
import org.Cry_Legende.sHTweaks.modules.DoorReset;
import org.Cry_Legende.sHTweaks.modules.IndestructibleItems;
import org.Cry_Legende.sHTweaks.modules.InfiniteAnvils;
import org.Cry_Legende.sHTweaks.modules.SculkConfirm;
import org.Cry_Legende.sHTweaks.modules.SignEdit;
import org.Cry_Legende.sHTweaks.modules.StackedSheep;
import org.Cry_Legende.sHTweaks.modules.TwerkCrops;
import org.Cry_Legende.sHTweaks.modules.Villagers;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class SHTweaks extends JavaPlugin implements TabCompleter {

    /**
     * Module connu du plugin.
     *
     * @param requiredPlugin plugin sans lequel le module reste coupé, ou null
     * @param factory        créé seulement si le plugin requis est là (ses classes ne sont pas chargées sinon)
     */
    private record Entry(String id, String requiredPlugin, Supplier<Module> factory) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("indestructible-items", null, IndestructibleItems::new),
            new Entry("stacked-sheep", "WildStacker", StackedSheep::new),
            new Entry("infinite-anvils", null, InfiniteAnvils::new),
            new Entry("twerk-crops", null, TwerkCrops::new),
            new Entry("sculk-confirm", null, SculkConfirm::new),
            new Entry("amethyst-nerf", null, AmethystNerf::new),
            new Entry("villagers", null, Villagers::new),
            new Entry("sign-edit", "SuperiorSkyblock2", SignEdit::new),
            new Entry("door-reset", null, DoorReset::new));

    private final Map<String, Module> active = new LinkedHashMap<>();
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
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

    private void startModules() {
        reloadConfig();
        messages = new Messages(new File(getDataFolder(), "messages.yml"));
        for (Entry entry : ENTRIES) {
            ConfigurationSection section = getConfig().getConfigurationSection("modules." + entry.id());
            if (section == null || !section.getBoolean("enabled", false)) {
                continue;
            }
            if (entry.requiredPlugin() != null && !getServer().getPluginManager().isPluginEnabled(entry.requiredPlugin())) {
                getLogger().warning("Module " + entry.id() + " coupé : " + entry.requiredPlugin() + " absent.");
                continue;
            }
            try {
                Module module = entry.factory().get();
                module.enable(this, section);
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shtweaks.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "reload" -> {
                stopModules();
                startModules();
                messages.send(sender, "reloaded", "active", String.valueOf(active.size()), "total", String.valueOf(ENTRIES.size()));
            }
            case "modules" -> {
                messages.send(sender, "modules-header");
                for (Entry entry : ENTRIES) {
                    if (active.containsKey(entry.id())) {
                        messages.send(sender, "module-line-on", "module", entry.id());
                    } else if (entry.requiredPlugin() != null && !getServer().getPluginManager().isPluginEnabled(entry.requiredPlugin())) {
                        messages.send(sender, "module-line-missing", "module", entry.id(), "plugin", entry.requiredPlugin());
                    } else {
                        messages.send(sender, "module-line-off", "module", entry.id());
                    }
                }
            }
            default -> messages.send(sender, "admin-usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("shtweaks.admin")) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        return List.of("reload", "modules").stream().filter(option -> option.startsWith(typed)).toList();
    }
}
