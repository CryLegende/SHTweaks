package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fait comprendre au joueur que son objet a été ramassé par un coffre collecteur AdvancedChests : une petite particule
 * et un son de ramassage aigu, à l'endroit où l'objet serait tombé, envoyés à lui seul. Rien n'est signalé pour ce qui
 * n'est pas son fait (cactus, mobs, entonnoirs...) : il faut qu'il ait lâché l'objet ou cassé le bloc lui-même.
 *
 * <p>AdvancedChests ramasse l'objet pendant son apparition (délai de ramassage à 0) et annule l'événement : l'objet
 * n'existe jamais. Cet écouteur, à la priorité MONITOR, voit donc un {@link ItemSpawnEvent} annulé dans un chunk où un
 * collecteur est en marche. Pour savoir qui en est la cause, il retient la dernière action du joueur ({@link BlockBreakEvent}
 * ou {@link PlayerDropItemEvent}) : l'objet apparaît dans le même appel, quelques microsecondes plus tard, sur le même
 * thread. Cette action est donc propre à chaque thread (DivineMC fait tourner les mondes en parallèle) et reste valable
 * une fraction de tick, dans un rayon autour de l'action.</p>
 *
 * <p>Coût : le cas courant (objet qui apparaît sans être annulé) coûte un test de booléen. Un signal par joueur au plus
 * tous les {@code cooldown-ms}, particule et son ensemble.</p>
 */
public final class RamassageModule extends Module {

    /**
     * Durée de validité d'une action. L'objet apparaît dans le même appel que l'action ; cette borne n'écarte que ce qui
     * viendrait bien après (un objet lâché par une tâche de plugin, par exemple).
     */
    private static final long WINDOW_NANOS = 100_000_000L;

    /** Dernière action d'un joueur sur un thread : réutilisée, jamais recréée. */
    private static final class Action {
        Player player;
        World world;
        double x;
        double y;
        double z;
        long until;
    }

    private final ThreadLocal<Action> action = ThreadLocal.withInitial(Action::new);
    /** Heure (nanoTime) du dernier signal envoyé à chaque joueur. */
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    private CollectorLink link;
    private long cooldownNanos;
    private double radiusSquared;
    private Particle particle;
    private double rise;
    private boolean force;
    private Sound sound;
    private float volume;
    private float pitch;

    @Override
    protected void load(ConfigurationSection config) {
        cooldownNanos = (long) clamp(config.getDouble("cooldown-ms", 1000.0), 0.0, 60_000.0) * 1_000_000L;
        double radius = clamp(config.getDouble("radius", 8.0), 1.0, 64.0);
        radiusSquared = radius * radius;
        rise = clamp(config.getDouble("particle.rise", 0.02), 0.0, 0.5);
        force = config.getBoolean("particle.force", false);
        readParticle(config.getString("particle.type", "FLAME"));
        readSound(config.getString("sound.name", "ENTITY_ITEM_PICKUP"));
        volume = (float) clamp(config.getDouble("sound.volume", 0.4), 0.0, 4.0);
        pitch = (float) clamp(config.getDouble("sound.pitch", 1.9), 0.5, 2.0);

        Plugin advancedChests = plugin.getServer().getPluginManager().getPlugin("AdvancedChests");
        try {
            link = CollectorLink.connect(advancedChests, plugin.getLogger());
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("API d'AdvancedChests différente de celle attendue ("
                    + advancedChests.getDescription().getVersion() + ") : " + e, e);
        }
    }

    @Override
    protected void unload() {
        link = null;
        last.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        remember(event.getPlayer(), block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Location at = event.getItemDrop().getLocation();
        remember(event.getPlayer(), at.getWorld(), at.getX(), at.getY(), at.getZ());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        last.remove(event.getPlayer().getUniqueId());
    }

    /**
     * AdvancedChests annule l'apparition de l'objet quand un collecteur du chunk le prend (priorité HIGH) ; on passe
     * donc après lui et on veut justement les événements annulés.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        Action current = action.get();
        Player player = current.player;
        if (player == null) {
            return;
        }
        long now = System.nanoTime();
        if (now - current.until > 0) {
            current.player = null;
            return;
        }
        Location at = event.getLocation();
        if (at.getWorld() != current.world || distanceSquared(at, current) > radiusSquared) {
            return;
        }
        UUID id = player.getUniqueId();
        Long before = last.get(id);
        if (before != null && now - before < cooldownNanos) {
            return;
        }
        CollectorLink collectors = link;
        if (collectors == null || !collectors.hasActiveCollector(at)) {
            return;
        }
        last.put(id, now);
        current.player = null;
        signal(player, at);
    }

    private void remember(Player player, World world, double x, double y, double z) {
        Action current = action.get();
        current.player = player;
        current.world = world;
        current.x = x;
        current.y = y;
        current.z = z;
        current.until = System.nanoTime() + WINDOW_NANOS;
    }

    private void signal(Player player, Location at) {
        // Particule dont le vecteur (0, 1, 0) fixe la direction et « rise » la vitesse : une braise qui monte un peu
        player.spawnParticle(particle, at.getX(), at.getY(), at.getZ(), 0, 0.0, 1.0, 0.0, rise, null, force);
        if (sound != null) {
            player.playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    private static double distanceSquared(Location at, Action from) {
        double dx = at.getX() - from.x;
        double dy = at.getY() - from.y;
        double dz = at.getZ() - from.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Particule sans données ; FLAME si le nom est inconnu ou si le type demande des données. */
    private void readParticle(String name) {
        try {
            particle = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(path("particle.type") + " : particule « " + name + " » inconnue, FLAME utilisée.");
            particle = Particle.FLAME;
        }
        if (particle.getDataType() != Void.class) {
            plugin.getLogger().warning(path("particle.type") + " : " + particle.name()
                    + " demande des données, seules les particules sans données sont gérées, FLAME utilisée.");
            particle = Particle.FLAME;
        }
    }

    /** Son ; aucun son si le nom est vide, ENTITY_ITEM_PICKUP si le nom est inconnu. */
    private void readSound(String name) {
        if (name == null || name.isBlank()) {
            sound = null;
            return;
        }
        sound = Sounds.sound(name);
        if (sound == null) {
            plugin.getLogger().warning(path("sound.name") + " : son inconnu « " + name + " », ENTITY_ITEM_PICKUP utilisé.");
            sound = Sound.ENTITY_ITEM_PICKUP;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
