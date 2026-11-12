package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/**
 * Montre la zone d'un coffre collecteur AdvancedChests : un joueur qui se tient dans un chunk où un collecteur est en
 * marche (ramassage du chunk activé) voit une ligne de particules discrète sur les bordures de ce chunk, à sa hauteur
 * et près de lui, même s'il est très loin du coffre. Aucune commande, aucune permission : tous les joueurs la voient.
 *
 * <p>L'état des coffres est lu en direct dans l'API d'AdvancedChests (voir {@link CollectorLink}), sans cache : le
 * plugin n'envoie aucun événement quand on active ou coupe le ramassage.</p>
 *
 * <p>Une seule tâche, sur le thread principal comme celle de SHInviWorldborder : le planificateur Bukkit passe avant la
 * phase où DivineMC fait tourner les mondes en parallèle, jamais en même temps. Elle tourne à chaque tick mais
 * n'examine, à chaque fois, qu'un joueur sur {@code interval-ticks} (selon son identifiant) : le travail est réparti sur
 * tous les ticks au lieu de former un pic tous les {@code interval-ticks} ticks. Les paquets partent vers le seul joueur
 * concerné ({@code Player#spawnParticle}).</p>
 */
public final class CollecteursModule extends Module {

    private static final double CHUNK = 16.0;
    private static final double EPSILON = 1e-9;

    /** Position réutilisée d'un joueur à l'autre. */
    private final Location scratch = new Location(null, 0, 0, 0);

    private CollectorLink link;
    private BukkitTask task;
    private int interval;
    private double radius;
    private double spacing;
    private double height;
    private Particle particle;
    private Object data;
    private boolean force;
    private long tick;

    // coût mesuré, écrit dans la console à l'arrêt du module
    private long ticks;
    private long totalNanos;
    private long maxNanos;
    private long particles;
    private long startedAt;

    @Override
    protected void load(ConfigurationSection config) {
        interval = (int) clamp(config.getInt("interval-ticks", 10), 1, 200);
        radius = clamp(config.getDouble("radius", 10.0), 2.0, 32.0);
        spacing = clamp(config.getDouble("spacing", 1.5), 0.25, 8.0);
        height = clamp(config.getDouble("height", 0.6), -2.0, 4.0);
        force = config.getBoolean("particle.force", false);
        readParticle(config);

        link = null;
        Plugin advancedChests = plugin.getServer().getPluginManager().getPlugin("AdvancedChests");
        try {
            link = CollectorLink.connect(advancedChests, plugin.getLogger());
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().severe("Module collecteurs coupé : API d'AdvancedChests différente de celle attendue ("
                    + advancedChests.getDescription().getVersion() + ") : " + e);
            return;
        }
        tick = 0;
        ticks = 0;
        totalNanos = 0;
        maxNanos = 0;
        particles = 0;
        startedAt = System.nanoTime();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::run, 20L, 1L);
    }

    @Override
    protected void unload() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        link = null;
        if (ticks > 0) {
            double seconds = Math.max(1e-3, (System.nanoTime() - startedAt) / 1e9);
            plugin.getLogger().info(String.format(Locale.ROOT,
                    "collecteurs : %d ticks, %.1f µs par tick en moyenne, %.0f µs au maximum, %.1f particules par seconde.",
                    ticks, totalNanos / 1000.0 / ticks, maxNanos / 1000.0, particles / seconds));
        }
    }

    private void run() {
        long started = System.nanoTime();
        int phase = (int) (tick++ % interval);
        int sent = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Math.floorMod(player.getUniqueId().hashCode(), interval) != phase
                    || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location at = player.getLocation(scratch);
            if (!link.hasActiveCollector(at)) {
                continue;
            }
            sent += draw(player, at.getX(), at.getY() + height, at.getZ());
        }
        long nanos = System.nanoTime() - started;
        ticks++;
        totalNanos += nanos;
        maxNanos = Math.max(maxNanos, nanos);
        particles += sent;
    }

    /**
     * Dessine les quatre bordures du chunk du joueur, limitées au rayon autour de lui.
     *
     * @return nombre de particules envoyées
     */
    private int draw(Player player, double x, double y, double z) {
        double minX = (((int) Math.floor(x)) >> 4) << 4;
        double minZ = (((int) Math.floor(z)) >> 4) << 4;
        return alongZ(player, minX, minZ, x, z, y)
                + alongZ(player, minX + CHUNK, minZ, x, z, y)
                + alongX(player, minZ, minX, x, z, y)
                + alongX(player, minZ + CHUNK, minX, x, z, y);
    }

    /** Bordure à x constant, qui s'étend en z de {@code startZ} à {@code startZ + 16}. */
    private int alongZ(Player player, double borderX, double startZ, double x, double z, double y) {
        double across = Math.abs(x - borderX);
        if (across >= radius) {
            return 0;
        }
        double half = Math.sqrt(radius * radius - across * across);
        double last = Math.min(startZ + CHUNK, z + half);
        int count = 0;
        // les particules suivent une grille fixe depuis le coin du chunk : elles ne glissent pas avec le joueur
        for (long i = first(startZ, z - half); startZ + i * spacing <= last + EPSILON; i++) {
            player.spawnParticle(particle, borderX, y, startZ + i * spacing, 1, 0.0, 0.0, 0.0, 0.0, data, force);
            count++;
        }
        return count;
    }

    /** Bordure à z constant, qui s'étend en x de {@code startX} à {@code startX + 16}. */
    private int alongX(Player player, double borderZ, double startX, double x, double z, double y) {
        double across = Math.abs(z - borderZ);
        if (across >= radius) {
            return 0;
        }
        double half = Math.sqrt(radius * radius - across * across);
        double last = Math.min(startX + CHUNK, x + half);
        int count = 0;
        for (long i = first(startX, x - half); startX + i * spacing <= last + EPSILON; i++) {
            player.spawnParticle(particle, startX + i * spacing, y, borderZ, 1, 0.0, 0.0, 0.0, 0.0, data, force);
            count++;
        }
        return count;
    }

    /** Indice de la première particule de la grille à partir de {@code from} (la grille commence au coin). */
    private long first(double start, double from) {
        return from <= start ? 0 : (long) Math.ceil((from - start) / spacing - EPSILON);
    }

    /** Particule et données adaptées à son type ; DUST si le nom est inconnu ou le type non géré. */
    private void readParticle(ConfigurationSection config) {
        Color color = color(config.getString("particle.color", "#55FFFF"), "particle.color", Color.AQUA);
        Color end = color(config.getString("particle.end-color", "#FFFFFF"), "particle.end-color", Color.WHITE);
        float size = (float) clamp(config.getDouble("particle.size", 0.8), 0.1, 4.0);
        String name = config.getString("particle.type", "DUST");
        try {
            particle = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(path("particle.type") + " : particule « " + name + " » inconnue, DUST utilisé.");
            particle = Particle.DUST;
        }
        Class<?> type = particle.getDataType();
        if (type == Void.class) {
            data = null;
        } else if (type == Particle.DustOptions.class) {
            data = new Particle.DustOptions(color, size);
        } else if (type == Particle.DustTransition.class) {
            data = new Particle.DustTransition(color, end, size);
        } else if (type == Color.class) {
            data = color;
        } else {
            plugin.getLogger().warning(path("particle.type") + " : " + particle.name() + " demande des données non gérées, DUST utilisé.");
            particle = Particle.DUST;
            data = new Particle.DustOptions(color, size);
        }
    }

    private Color color(String hex, String key, Color fallback) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.trim().replace("#", ""), 16));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(path(key) + " : couleur invalide « " + hex + " », valeur par défaut utilisée.");
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
