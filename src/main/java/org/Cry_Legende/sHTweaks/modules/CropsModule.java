package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * S'accroupir près des cultures listées leur fait prendre un stade, avec une chance par culture.
 * Canne à sucre et cactus : l'âge monte jusqu'au maximum, le bloc suivant pousse au tick aléatoire d'après.
 */
public final class CropsModule extends Module {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private int radius;
    private int chance;
    private long cooldownMs;
    private Set<Material> crops;
    private Set<String> excludedWorlds;

    @Override
    protected void load(ConfigurationSection config) {
        radius = Math.max(0, config.getInt("radius", 2));
        chance = Math.max(0, Math.min(100, config.getInt("chance", 35)));
        cooldownMs = Math.max(0, config.getLong("cooldown-ms", 500));
        crops = Materials.parse(config.getStringList("crops"), plugin.getLogger(), path("crops"));
        excludedWorlds = new HashSet<>(config.getStringList("excluded-worlds"));
    }

    @Override
    protected void unload() {
        cooldowns.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    // Écoute aussi les accroupissements annulés (SHSit s'en sert pour relever le joueur)
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking() || player.getGameMode() == GameMode.SPECTATOR
                || excludedWorlds.contains(player.getWorld().getName())) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            return;
        }
        Location origin = player.getLocation();
        boolean grown = false;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = origin.clone().add(dx, dy, dz).getBlock();
                    if (!crops.contains(block.getType()) || !(block.getBlockData() instanceof Ageable ageable)
                            || ageable.getAge() >= ageable.getMaximumAge()
                            || ThreadLocalRandom.current().nextInt(100) >= chance) {
                        continue;
                    }
                    ageable.setAge(ageable.getAge() + 1);
                    block.setBlockData(ageable);
                    block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.2, 0.5), 5, 0.2, 0.2, 0.2, 0.0);
                    grown = true;
                }
            }
        }
        if (grown) {
            cooldowns.put(player.getUniqueId(), now);
        }
    }
}
