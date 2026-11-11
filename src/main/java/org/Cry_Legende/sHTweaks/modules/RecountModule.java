package org.Cry_Legende.sHTweaks.modules;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Recalcule une fois chaque île pour remettre ses comptes de blocs d'accord avec ce qui est réellement posé.
 * <p>
 * Une île est recalculée quelques secondes après la connexion d'un de ses membres. Avec {@code offline}, les autres
 * le sont aussi, une à une, de la plus haute à la plus basse : SuperiorSkyblock2 refuse de recalculer une île sans
 * membre connecté, elle est donc marquée active le temps du calcul, puis rendue inactive avec sa date de dernière
 * activité d'origine. Les îles faites sont notées dans recount.yml :
 * effacer ce fichier relance un passage complet.
 */
public final class RecountModule extends Module {

    /** Délai au-delà duquel un calcul sans réponse (erreur côté SuperiorSkyblock2) est abandonné. */
    private static final long CALC_TIMEOUT_TICKS = 60 * 20L;
    private static final long SAVE_INTERVAL_TICKS = 30 * 20L;
    private static final int PROGRESS_EVERY = 250;

    private final Set<UUID> done = ConcurrentHashMap.newKeySet();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private File file;
    private long delayTicks;
    private boolean log;
    private boolean offline;
    private long offlineStartTicks;
    private long offlinePauseTicks;
    private volatile boolean dirty;
    private volatile boolean running;

    private Deque<UUID> queue;
    private int swept;
    private int recounted;
    private int changed;
    private BigDecimal levelsBefore = BigDecimal.ZERO;
    private BigDecimal levelsAfter = BigDecimal.ZERO;

    @Override
    protected void load(ConfigurationSection config) {
        delayTicks = Math.max(0, config.getInt("delay-seconds", 10)) * 20L;
        log = config.getBoolean("log", true);
        offline = config.getBoolean("offline", false);
        offlineStartTicks = Math.max(1, config.getInt("offline-start-minutes", 10)) * 60L * 20L;
        offlinePauseTicks = Math.max(1, config.getInt("offline-pause-ticks", 20));
        file = new File(plugin.getDataFolder(), "recount.yml");
        done.clear();
        for (String id : YamlConfiguration.loadConfiguration(file).getStringList("islands")) {
            try {
                done.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                // ligne abîmée : l'île sera recalculée une fois de plus
            }
        }
        running = true;
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS));
        if (offline) {
            tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, this::startSweep, offlineStartTicks));
        }
    }

    @Override
    protected void unload() {
        running = false;
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        if (dirty) {
            dirty = false;
            writeFile(file, snapshot());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!running || !player.isOnline()) {
                return;
            }
            Island island = SuperiorSkyblockAPI.getPlayer(player).getIsland();
            if (island != null && !island.isBeingRecalculated() && !done.contains(island.getUniqueId())) {
                recount(island, () -> {
                });
            }
        }, delayTicks);
    }

    /** File d'attente des îles pas encore faites, de la plus haute à la plus basse. */
    private void startSweep() {
        List<Island> islands = new ArrayList<>(SuperiorSkyblockAPI.getGrid().getIslands());
        islands.sort(Comparator.comparing(Island::getIslandLevel).reversed());
        queue = new ArrayDeque<>();
        for (Island island : islands) {
            if (!done.contains(island.getUniqueId())) {
                queue.add(island.getUniqueId());
            }
        }
        plugin.getLogger().info("Recalcul des îles : " + queue.size() + " île(s) à faire, une toutes les "
                + offlinePauseTicks + " ticks au plus.");
        next();
    }

    private void next() {
        if (!running) {
            return;
        }
        while (!queue.isEmpty()) {
            Island island = SuperiorSkyblockAPI.getGrid().getIslandByUUID(queue.poll());
            if (island == null || done.contains(island.getUniqueId())) {
                continue;
            }
            if (island.isBeingRecalculated()) {
                queue.add(island.getUniqueId());
                break;
            }
            recount(island, () -> {
                swept++;
                if (log && swept % PROGRESS_EVERY == 0) {
                    plugin.getLogger().info("Recalcul des îles : " + swept + " faite(s), " + queue.size() + " restante(s).");
                }
                tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, this::next, offlinePauseTicks));
            });
            return;
        }
        if (queue.isEmpty()) {
            plugin.getLogger().info("Recalcul des îles terminé : " + recounted + " île(s) recalculée(s) depuis le démarrage, "
                    + changed + " niveau(x) changé(s), total " + levelsBefore.toPlainString() + " -> " + levelsAfter.toPlainString() + ".");
            saveIfDirty();
        } else {
            tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, this::next, offlinePauseTicks));
        }
    }

    /**
     * Recalcule l'île puis lance {@code then} sur le thread principal. Une île sans membre connecté est marquée active
     * le temps du calcul ; elle redevient inactive à la fin si personne ne s'est connecté entre-temps.
     */
    private void recount(Island island, Runnable then) {
        if (!done.add(island.getUniqueId())) {
            then.run();
            return;
        }
        dirty = true;
        BigDecimal before = island.getIslandLevel();
        boolean forced = !island.isCurrentlyActive();
        // Le recalcul date l'île de maintenant : une île inactive garde sa vraie date de dernière activité.
        long lastTimeUpdate = island.getLastTimeUpdate();
        if (forced) {
            island.setCurrentlyActive(true);
        }
        AtomicBoolean finished = new AtomicBoolean();
        BukkitTask[] watchdog = new BukkitTask[1];
        Runnable finish = () -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            if (watchdog[0] != null) {
                watchdog[0].cancel();
            }
            if (forced && noMemberOnline(island)) {
                island.setCurrentlyActive(false);
                island.setLastTimeUpdate(lastTimeUpdate);
            }
            BigDecimal after = island.getIslandLevel();
            recounted++;
            levelsBefore = levelsBefore.add(before);
            levelsAfter = levelsAfter.add(after);
            if (before.compareTo(after) != 0) {
                changed++;
                if (log) {
                    plugin.getLogger().info("Île de " + island.getOwner().getName() + " recalculée : niveau "
                            + before.toPlainString() + " -> " + after.toPlainString());
                }
            }
            then.run();
        };
        watchdog[0] = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!finished.get()) {
                plugin.getLogger().warning("Île de " + island.getOwner().getName() + " : pas de résultat du recalcul après 60 s, île suivante.");
            }
            finish.run();
        }, CALC_TIMEOUT_TICKS);
        try {
            island.calcIslandWorth(null, () -> plugin.getServer().getScheduler().runTask(plugin, finish));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Recalcul de l'île de " + island.getOwner().getName() + " impossible", e);
            finish.run();
        }
    }

    private static boolean noMemberOnline(Island island) {
        for (SuperiorPlayer member : island.getIslandMembers(true)) {
            if (member.isOnline()) {
                return false;
            }
        }
        return true;
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
