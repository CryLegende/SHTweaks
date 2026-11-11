package org.Cry_Legende.sHTweaks.modules;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Recalcule une fois chaque île, quelques secondes après la connexion d'un de ses membres, pour remettre ses
 * comptes de blocs d'accord avec ce qui est réellement posé. SuperiorSkyblock2 ne recalcule une île que si un
 * membre est connecté, d'où l'attente d'une connexion. Les îles faites sont notées dans recount.yml : effacer
 * ce fichier relance un passage complet.
 */
public final class RecountModule extends Module {

    private final Set<UUID> done = ConcurrentHashMap.newKeySet();
    private File file;
    private long delayTicks;
    private boolean log;

    @Override
    protected void load(ConfigurationSection config) {
        delayTicks = Math.max(0, config.getInt("delay-seconds", 10)) * 20L;
        log = config.getBoolean("log", true);
        file = new File(plugin.getDataFolder(), "recount.yml");
        done.clear();
        for (String id : YamlConfiguration.loadConfiguration(file).getStringList("islands")) {
            try {
                done.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                // ligne abîmée : l'île sera recalculée une fois de plus
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> recount(player), delayTicks);
    }

    private void recount(Player player) {
        if (!player.isOnline()) {
            return;
        }
        Island island = SuperiorSkyblockAPI.getPlayer(player).getIsland();
        if (island == null || island.isBeingRecalculated() || !done.add(island.getUniqueId())) {
            return;
        }
        save();
        BigDecimal before = island.getIslandLevel();
        island.calcIslandWorth(null, () -> {
            if (log) {
                plugin.getLogger().info("Île de " + island.getOwner().getName() + " recalculée : niveau "
                        + before.toPlainString() + " -> " + island.getIslandLevel().toPlainString());
            }
        });
    }

    /** Écriture hors du thread principal, dans un fichier temporaire remplacé d'un coup. */
    private void save() {
        List<String> ids = new ArrayList<>(done.size());
        for (UUID id : done) {
            ids.add(id.toString());
        }
        File target = file;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeFile(target, ids));
    }

    private synchronized void writeFile(File target, List<String> ids) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("islands", ids);
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            yaml.save(temp);
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "recount.yml non écrit", e);
        }
    }
}
