package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Joue des sons ambiants à une position d'un monde, à intervalles réguliers ou aléatoires (modules.ambientsounds.sounds).
 * Au démarrage du serveur chaque son est joué tout de suite ; après un /shtweaks reload, le premier passage attend un
 * intervalle pour ne pas rejouer tous les sons d'un coup.
 */
public final class AmbientSoundsModule extends Module {

    /** Le monde est résolu à chaque lecture : il peut être chargé après le démarrage (PhantomWorlds). */
    private record AmbientSound(Sound sound, float pitch, float volume, String worldName, double x, double y, double z,
                                String replayEvery) {
        void play() {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.playSound(new Location(world, x, y, z), sound, volume, pitch);
            }
        }
    }

    // Une tâche en attente par son ; annulées au rechargement
    private final Map<String, BukkitTask> tasks = new ConcurrentHashMap<>();
    private volatile boolean running;

    @Override
    protected void load(ConfigurationSection config) {
        ConfigurationSection soundsSection = config.getConfigurationSection("sounds");
        if (soundsSection == null) {
            plugin.getLogger().warning(path("sounds") + " est introuvable.");
            return;
        }
        running = true;

        for (String key : soundsSection.getKeys(false)) {
            ConfigurationSection soundConfig = soundsSection.getConfigurationSection(key);
            if (soundConfig == null) continue;

            String soundName = soundConfig.getString("sound");
            String worldName = soundConfig.getString("world");
            String positionStr = soundConfig.getString("position");
            String replayEvery = soundConfig.getString("replayEvery");

            if (soundName == null || worldName == null || positionStr == null || replayEvery == null) {
                plugin.getLogger().warning(path("sounds." + key) + " ignoré : paramètres manquants.");
                continue;
            }

            Sound sound = Sounds.sound(soundName);
            if (sound == null) {
                plugin.getLogger().warning(path("sounds." + key) + " ignoré : son « " + soundName + " » inconnu.");
                continue;
            }

            try {
                String[] posParts = positionStr.split("/");
                double x = Double.parseDouble(posParts[0]);
                double y = Double.parseDouble(posParts[1]);
                double z = Double.parseDouble(posParts[2]);
                float pitch = (float) soundConfig.getDouble("pitch", 1.0);
                float volume = (float) soundConfig.getDouble("volume", 1.0);

                AmbientSound ambient = new AmbientSound(sound, pitch, volume, worldName, x, y, z, replayEvery);
                if (reloading()) {
                    scheduleNext(key, ambient);
                } else {
                    playAndScheduleNext(key, ambient);
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                plugin.getLogger().warning(path("sounds." + key) + " ignoré : position invalide (x/y/z attendu).");
            }
        }
    }

    @Override
    protected void unload() {
        running = false;
        tasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
    }

    private void playAndScheduleNext(String key, AmbientSound ambient) {
        if (!running) return;
        ambient.play();
        scheduleNext(key, ambient);
    }

    private void scheduleNext(String key, AmbientSound ambient) {
        if (!running) return;
        long delay = nextDelayInTicks(ambient.replayEvery());
        tasks.put(key, plugin.getServer().getScheduler().runTaskLater(plugin, () -> playAndScheduleNext(key, ambient), delay));
    }

    /** Lit replayEvery (un nombre de ticks, ou min-max) et rend le délai en ticks. */
    private long nextDelayInTicks(String replayEvery) {
        try {
            if (replayEvery.contains("-")) {
                String[] parts = replayEvery.split("-");
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                if (min > max) {
                    int temp = min;
                    min = max;
                    max = temp;
                }
                return ThreadLocalRandom.current().nextInt(min, max + 1);
            } else {
                return Long.parseLong(replayEvery.trim());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Format 'replayEvery' invalide : " + replayEvery
                + ". Utilisation d'un délai par défaut de 200 ticks.", e);
            return 200L; // 10 secondes
        }
    }
}
