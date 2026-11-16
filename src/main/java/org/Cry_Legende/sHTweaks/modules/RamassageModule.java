package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fait comprendre au joueur que son objet a été ramassé par un coffre collecteur AdvancedChests : une petite particule
 * et un tintement aigu, à l'endroit où l'objet serait tombé (devant lui quand il l'a lâché), envoyés à lui seul. Rien n'est signalé pour ce qui
 * n'est pas son fait (cactus, mobs, entonnoirs, casse automatique d'un générateur...) : il faut qu'il ait lâché l'objet ou cassé le bloc lui-même.
 *
 * <p>AdvancedChests ramasse l'objet pendant son apparition (délai de ramassage à 0), le retire et annule l'événement :
 * l'objet n'existe jamais. Cet écouteur, à la priorité MONITOR, voit donc un {@link ItemSpawnEvent} annulé dont l'objet est
 * déjà retiré, dans un chunk où un collecteur est en marche. Un objet seulement annulé n'a pas été ramassé : un autre plugin
 * l'a envoyé ailleurs (EliteGenerator annule l'apparition des objets d'un générateur amélioré et les range dans
 * l'inventaire du joueur ou dans le coffre lié ; AdvancedChests ignore les apparitions déjà annulées).</p>
 *
 * <p>Pour savoir qui en est la cause, l'action du joueur est retenue sur le thread où elle a lieu, le temps de sa propre
 * suite d'événements : objet lâché ({@link PlayerDropItemEvent}, puis l'apparition de cet objet), bloc cassé
 * ({@link BlockBreakEvent}, puis {@link BlockDropItemEvent} qui donne les objets du bloc, puis leur apparition). Elle
 * prend fin à l'apparition du dernier de ses objets, ou dès que {@link BlockDropItemEvent} est annulé : ce qui apparaît
 * ensuite sur le même thread (la casse automatique d'un générateur, lancée par le planificateur juste après les paquets
 * des joueurs, dans le même tick) n'est plus à lui. Un objet qu'un plugin fait apparaître pendant cette suite (générateur
 * qui relâche les objets de son bloc, casse en zone, inventaire plein) compte, dans un rayon autour de l'action. Une
 * action dont la suite ne se termine pas ne survit pas au tick.</p>
 *
 * <p>Coût : une lecture de variable de thread par objet qui apparaît. Un signal par joueur au plus tous les
 * {@code cooldown-ms}, particule et son ensemble.</p>
 */
public final class RamassageModule extends Module {

    /** Action en cours d'un joueur sur un thread : réutilisée, jamais recréée. */
    private static final class Action {
        Player player;
        World world;
        double x;
        double y;
        double z;
        /** Endroit du signal quand il n'est pas celui de l'objet (objet lâché : devant le joueur). */
        boolean shifted;
        double signalX;
        double signalY;
        double signalZ;
        int tick;
        /** Objets de l'action qui doivent encore apparaître ; -1 tant qu'ils ne sont pas connus (bloc cassé). */
        int pending = -1;
        int[] ids = new int[8];

        void clear() {
            player = null;
            world = null;
            pending = -1;
        }

        void expect(int id) {
            if (pending < 0) {
                pending = 0;
            }
            if (pending == ids.length) {
                ids = Arrays.copyOf(ids, pending * 2);
            }
            ids[pending++] = id;
        }

        /** Retire cet objet de ceux qui doivent apparaître ; vrai s'il était le dernier. */
        boolean arrived(int id) {
            for (int i = 0; i < pending; i++) {
                if (ids[i] == id) {
                    ids[i] = ids[--pending];
                    return pending == 0;
                }
            }
            return false;
        }
    }

    private final ThreadLocal<Action> action = ThreadLocal.withInitial(Action::new);
    /** Heure (nanoTime) du dernier signal envoyé à chaque joueur. */
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    private CollectorLink link;
    private long cooldownNanos;
    private double radiusSquared;
    private Particle particle;
    private double rise;
    private double forward;
    private double below;
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
        forward = clamp(config.getDouble("particle.forward", 1.1), 0.0, 4.0);
        below = clamp(config.getDouble("particle.below-eyes", 0.4), 0.0, 1.6);
        force = config.getBoolean("particle.force", false);
        readParticle(config.getString("particle.type", "FLAME"));
        readSound(config.getString("sound.name", "BLOCK_NOTE_BLOCK_CHIME"));
        volume = (float) clamp(config.getDouble("sound.volume", 0.3), 0.0, 4.0);
        pitch = (float) clamp(config.getDouble("sound.pitch", 1.8), 0.5, 2.0);

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

    /** Bloc cassé : ses objets ne sont pas encore connus, ils le seront par {@link #onBlockDrop}. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        // sans objets, aucun BlockDropItemEvent ne viendrait clore l'action
        if (!event.isDropItems()) {
            return;
        }
        Block block = event.getBlock();
        remember(event.getPlayer(), block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }

    /** Objets du bloc que le joueur vient de casser : ils apparaissent juste après, dans cet ordre. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDrop(BlockDropItemEvent event) {
        // annulé : les objets n'apparaîtront pas (SHTPDrops les a mis dans l'inventaire), la suite de l'action s'arrête là
        if (event.isCancelled()) {
            action.get().clear();
            return;
        }
        Block block = event.getBlock();
        Action current = remember(event.getPlayer(), block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        for (Item item : event.getItems()) {
            // un objet déjà dans le monde (ajouté par un plugin) n'apparaîtra pas
            if (!item.isValid()) {
                current.expect(item.getEntityId());
            }
        }
        if (current.pending <= 0) {
            current.clear();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item drop = event.getItemDrop();
        Location at = drop.getLocation();
        Action current = remember(event.getPlayer(), at.getWorld(), at.getX(), at.getY(), at.getZ());
        current.expect(drop.getEntityId());
        // L'objet lâché apparaît à hauteur des yeux : la particule serait dans la tête du joueur, on la met devant lui
        Location eyes = event.getPlayer().getEyeLocation();
        double yaw = Math.toRadians(eyes.getYaw());
        current.shifted = true;
        current.signalX = eyes.getX() - Math.sin(yaw) * forward;
        current.signalY = eyes.getY() - below;
        current.signalZ = eyes.getZ() + Math.cos(yaw) * forward;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        last.remove(event.getPlayer().getUniqueId());
    }

    /**
     * AdvancedChests retire l'objet qu'un collecteur du chunk prend et annule son apparition (priorité HIGH) ; on passe
     * donc après lui et on veut justement les événements annulés.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSpawn(ItemSpawnEvent event) {
        Action current = action.get();
        Player player = current.player;
        if (player == null) {
            return;
        }
        if (current.tick != Bukkit.getCurrentTick()) {
            current.clear();
            return;
        }
        Item item = event.getEntity();
        boolean lastOfAction = current.arrived(item.getEntityId());
        if (taken(event, item, current, player)) {
            current.clear();
            return;
        }
        if (lastOfAction) {
            current.clear();
        }
    }

    /** L'objet a-t-il été pris par un collecteur, et ce joueur doit-il en être prévenu maintenant ? Envoie le signal si oui. */
    private boolean taken(ItemSpawnEvent event, Item item, Action current, Player player) {
        // annulé sans être retiré : un plugin l'a envoyé ailleurs (inventaire, coffre lié), pas un collecteur
        if (!event.isCancelled() || !item.isDead()) {
            return false;
        }
        Location at = event.getLocation();
        if (at.getWorld() != current.world || distanceSquared(at, current) > radiusSquared) {
            return false;
        }
        UUID id = player.getUniqueId();
        long now = System.nanoTime();
        Long before = last.get(id);
        if (before != null && now - before < cooldownNanos) {
            return false;
        }
        CollectorLink collectors = link;
        if (collectors == null || !collectors.hasActiveCollector(at)) {
            return false;
        }
        last.put(id, now);
        if (current.shifted) {
            signal(player, current.signalX, current.signalY, current.signalZ);
        } else {
            signal(player, at.getX(), at.getY(), at.getZ());
        }
        return true;
    }

    private Action remember(Player player, World world, double x, double y, double z) {
        Action current = action.get();
        current.clear();
        current.player = player;
        current.world = world;
        current.x = x;
        current.y = y;
        current.z = z;
        current.shifted = false;
        current.tick = Bukkit.getCurrentTick();
        return current;
    }

    private void signal(Player player, double x, double y, double z) {
        // Particule dont le vecteur (0, 1, 0) fixe la direction et « rise » la vitesse : une braise qui monte un peu
        player.spawnParticle(particle, x, y, z, 0, 0.0, 1.0, 0.0, rise, null, force);
        if (sound != null) {
            player.playSound(new Location(player.getWorld(), x, y, z), sound, SoundCategory.PLAYERS, volume, pitch);
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

    /** Son ; aucun son si le nom est vide, BLOCK_NOTE_BLOCK_CHIME si le nom est inconnu. */
    private void readSound(String name) {
        if (name == null || name.isBlank()) {
            sound = null;
            return;
        }
        sound = Sounds.sound(name);
        if (sound == null) {
            plugin.getLogger().warning(path("sound.name") + " : son inconnu « " + name + " », BLOCK_NOTE_BLOCK_CHIME utilisé.");
            sound = Sound.BLOCK_NOTE_BLOCK_CHIME;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
