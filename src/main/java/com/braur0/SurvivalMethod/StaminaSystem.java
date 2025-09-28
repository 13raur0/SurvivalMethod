package com.braur0.SurvivalMethod;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;
import java.util.Map;

public class StaminaSystem implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Double> stamina = new HashMap<>();
    private final Map<UUID, Long> lastExhaustionTime = new HashMap<>();

    // --- Config Values ---
    private final double MAX_STAMINA;
    private final double JUMP_UNLOCK_THRESHOLD;
    private final float DEFAULT_WALK_SPEED = 0.2f; // Default Minecraft walk speed
    private final float EXHAUSTED_WALK_SPEED;
    private final double BASE_REGEN;
    private final double MAX_IDLE_BONUS;
    private final double BASE_SPRINT_COST;

    public StaminaSystem(Plugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        // Load stamina settings
        this.MAX_STAMINA = config.getDouble("stamina.max-stamina", 100.0);
        double jumpUnlockPercent = config.getDouble("stamina.jump-unlock-threshold-percent", 20.0);
        this.JUMP_UNLOCK_THRESHOLD = MAX_STAMINA * (jumpUnlockPercent / 100.0);
        this.EXHAUSTED_WALK_SPEED = (float) config.getDouble("stamina.exhausted-walk-speed", 0.1);
        this.BASE_REGEN = config.getDouble("stamina.base-regen", 1.0);
        this.MAX_IDLE_BONUS = config.getDouble("stamina.max-idle-bonus", 4.0);
        this.BASE_SPRINT_COST = config.getDouble("stamina.base-sprint-cost", 4.0);

        startStaminaRegenTask();
        startStaminaConsumptionTask();
    }

    /**
     * Initializes stamina for a player.
     * If reset is true, stamina is set to max. Otherwise, it remains unchanged.
     * @param player The player to initialize.
     * @param reset  Whether to reset the player's stamina to max.
     */
    public void initializeForPlayer(Player player, boolean reset) {
        player.setWalkSpeed(DEFAULT_WALK_SPEED);
        removeJumpBlock(player);
        UUID playerUUID = player.getUniqueId();
        if (reset) {
            stamina.put(playerUUID, MAX_STAMINA);
        }
        updateExpBar(player, stamina.getOrDefault(playerUUID, MAX_STAMINA));
    }
    /**
     * Cleans up player data to prevent memory leaks.
     */
    private void cleanupPlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        stamina.remove(playerUUID);
        lastExhaustionTime.remove(playerUUID);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    private void startStaminaRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID playerUUID = player.getUniqueId();
                    double current = stamina.getOrDefault(playerUUID, MAX_STAMINA);
                    if (current >= MAX_STAMINA) continue;

                    long lastExhaustion = lastExhaustionTime.getOrDefault(playerUUID, now);
                    long idleMillis = now - lastExhaustion;

                    if (!player.isSprinting()) {
                        double regen = BASE_REGEN + Math.min(idleMillis / 5000.0, MAX_IDLE_BONUS);
                        current = Math.min(current + regen, MAX_STAMINA);
                        stamina.put(playerUUID, current);
                        updateExpBar(player, current);

                        if (current > JUMP_UNLOCK_THRESHOLD) {
                            removeJumpBlock(player);
                            player.setWalkSpeed(DEFAULT_WALK_SPEED);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startStaminaConsumptionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID playerUUID = player.getUniqueId();
                    if (player.isSprinting()) {
                        double current = stamina.getOrDefault(playerUUID, MAX_STAMINA);
                        if (current <= 0) continue;

                        double hungerRatio = player.getFoodLevel() / 20.0;
                        double sprintCost = BASE_SPRINT_COST * (1 + (1 - hungerRatio));
                        current = Math.max(current - sprintCost, 0);
                        stamina.put(playerUUID, current);
                        lastExhaustionTime.put(playerUUID, System.currentTimeMillis());
                        updateExpBar(player, current);

                        if (current <= 0) {
                            player.setWalkSpeed(EXHAUSTED_WALK_SPEED);
                            applyJumpBlock(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void applyJumpBlock(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, -1, 200, false, false, false));
    }

    private void removeJumpBlock(Player player) {
        player.removePotionEffect(PotionEffectType.JUMP);
    }

    private void updateExpBar(Player player, double value) {
        player.setExp((float) (value / MAX_STAMINA));
    }
}