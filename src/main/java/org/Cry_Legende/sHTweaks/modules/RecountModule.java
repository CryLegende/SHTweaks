package org.Cry_Legende.sHTweaks.modules;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Recalcule une fois chaque île pour remettre ses comptes de blocs d'accord avec ce qui est réellement posé.
 * <p>
 * Une île n'est recalculée que pendant qu'un de ses membres s'y trouve : ses chunks sont alors déjà chargés, comme
 * pour un /is level fait sur place. Recalculer une île sans personne dessus charge tous ses chunks d'un coup et fait
 * tourner ses fermes (essai du 2026-11-11 sur backopale : serveur figé). Une seule île à la fois, et aucune quand le
 * serveur rame. Les îles faites sont notées dans recount.yml : effacer ce fichier relance un passage complet.
 */
public final class RecountModule extends Module {

    /** Délai au-delà duquel un calcul sans réponse (erreur côté SuperiorSkyblock2) est abandonné. */
    private static final long CALC_TIMEOUT_TICKS = 60 * 20L;
    private static final long SAVE_INTERVAL_TICKS = 30 * 20L;

    private final Set<UUID> done = ConcurrentHashMap.newKeySet();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private File file;
    private double minTps;
    private boolean log;
    private volatile boolean dirty;
    private boolean busy;

    @Override
    protected void load(ConfigurationSection config) {
        long intervalTicks = Math.max(1, config.getInt("interval-seconds", 10)) * 20L;
        minTps = config.getDouble("min-tps", 18.0);
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
        busy = false;
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, intervalTicks, intervalTicks));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS));
    }

    @Override
    protected void unload() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        if (dirty) {
            dirty = false;
            writeFile(file, snapshot());
        }
    }

    /** Première île pas encore faite dont un membre connecté se trouve sur place. */
    private void check() {
        if (busy || plugin.getServer().getTPS()[0] < minTps) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Island island = SuperiorSkyblockAPI.getPlayer(player).getIsland();
            if (island == null || done.contains(island.getUniqueId()) || island.isBeingRecalculated()
                    || !island.isInsideRange(player.getLocation())) {
                continue;
            }
            recount(island);
            return;
        }
    }

    private void recount(Island island) {
        done.add(island.getUniqueId());
        dirty = true;
        busy = true;
        BigDecimal before = island.getIslandLevel();
        AtomicBoolean finished = new AtomicBoolean();
        BukkitTask[] watchdog = new BukkitTask[1];
        // SuperiorSkyblock2 n'appelle le rappel qu'en cas de réussite : sans réponse, l'île sera reprise plus tard.
        Consumer<Boolean> finish = success -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            if (watchdog[0] != null) {
                watchdog[0].cancel();
            }
            busy = false;
            if (!success) {
                done.remove(island.getUniqueId());
                return;
            }
            BigDecimal after = island.getIslandLevel();
            if (log && before.compareTo(after) != 0) {
                plugin.getLogger().info("Île de " + island.getOwner().getName() + " recalculée : niveau "
                        + before.toPlainString() + " -> " + after.toPlainString());
            }
        };
        watchdog[0] = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!finished.get()) {
                plugin.getLogger().warning("Île de " + island.getOwner().getName() + " : pas de résultat du recalcul après 60 s, reprise plus tard.");
            }
            finish.accept(false);
        }, CALC_TIMEOUT_TICKS);
        try {
            island.calcIslandWorth(null, () -> plugin.getServer().getScheduler().runTask(plugin, () -> finish.accept(true)));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Recalcul de l'île de " + island.getOwner().getName() + " impossible", e);
            finish.accept(false);
        }
    }

    private List<String> snapshot() {
        List<String> ids = new ArrayList<>(done.size());
        for (UUID id : done) {
            ids.add(id.toString());
        }
        return ids;
    }

    /** Écriture hors du thread principal, dans un fichier temporaire remplacé d'un coup. */
    private void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        List<String> ids = snapshot();
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
