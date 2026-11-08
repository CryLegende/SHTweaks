package org.Cry_Legende.sHTweaks.modules;

import org.Cry_Legende.sHTweaks.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * Comportement des villageois : IA, commerce, reproduction, profession, golems...
 * Couper « no-ai », « silent » ou « invulnerable » ne rend rien aux villageois déjà modifiés.
 */
public final class Villagers extends Module {

    private boolean preventZombieCuring;
    private boolean noAi;
    private boolean disableTrading;
    private boolean disableBreeding;
    private boolean preventItemPickup;
    private boolean silent;
    private boolean preventProfessionChange;
    private boolean preventNewTrades;
    private boolean invulnerable;
    private boolean preventDoorOpening;
    private boolean preventGolemSpawning;

    @Override
    protected void load(ConfigurationSection config) {
        preventZombieCuring = config.getBoolean("prevent-zombie-curing", true);
        noAi = config.getBoolean("no-ai", true);
        disableTrading = config.getBoolean("disable-trading", true);
        disableBreeding = config.getBoolean("disable-breeding", true);
        preventItemPickup = config.getBoolean("prevent-item-pickup", true);
        silent = config.getBoolean("silent", false);
        preventProfessionChange = config.getBoolean("prevent-profession-change", true);
        preventNewTrades = config.getBoolean("prevent-new-trades", true);
        invulnerable = config.getBoolean("invulnerable", false);
        preventDoorOpening = config.getBoolean("prevent-door-opening", false);
        preventGolemSpawning = config.getBoolean("prevent-golem-spawning", true);
        if (noAi || silent || invulnerable) {
            for (World world : Bukkit.getWorlds()) {
                for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                    apply(villager);
                }
            }
        }
    }

    private void apply(Villager villager) {
        if (noAi) {
            villager.setAI(false);
        }
        if (silent) {
            villager.setSilent(true);
        }
        if (invulnerable) {
            villager.setInvulnerable(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (preventZombieCuring && event.getRightClicked() instanceof ZombieVillager zombie
                && zombie.hasPotionEffect(PotionEffectType.WEAKNESS)) {
            ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
            if (item.getType() == Material.GOLDEN_APPLE) {
                event.setCancelled(true);
            }
        }
        if (disableTrading && (event.getRightClicked() instanceof Villager || event.getRightClicked() instanceof WanderingTrader)) {
            event.setCancelled(true);
            event.getPlayer().playSound(event.getRightClicked().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            plugin.messages().send(event.getPlayer(), "villager-trade-disabled");
        }
    }

    @EventHandler
    public void onTransform(EntityTransformEvent event) {
        if (preventZombieCuring && event.getEntityType() == EntityType.ZOMBIE_VILLAGER
                && event.getTransformReason() == EntityTransformEvent.TransformReason.CURED) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            apply(villager);
        }
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (disableBreeding && (event.getMother() instanceof Villager || event.getFather() instanceof Villager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (preventItemPickup && event.getEntity() instanceof Villager) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCareerChange(VillagerCareerChangeEvent event) {
        if (preventProfessionChange) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (preventNewTrades) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (invulnerable && event.getEntity() instanceof Villager) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDoor(EntityInteractEvent event) {
        if (preventDoorOpening && event.getEntity() instanceof Villager) {
            String type = event.getBlock().getType().name();
            if (type.endsWith("_DOOR") && !type.contains("TRAPDOOR")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onGolem(CreatureSpawnEvent event) {
        if (preventGolemSpawning && event.getEntityType() == EntityType.IRON_GOLEM
                && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE) {
            event.setCancelled(true);
        }
    }
}
