package com.braur0.SurvivalMethod;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;


public class ThirstSystem implements Listener {
    private final Plugin plugin;
    private final Map<UUID, Long> nextThirstDecreaseTime = new HashMap<>();
    private final Map<UUID, Long> nextDamageTime = new HashMap<>();
    private final Map<UUID, Boolean> isUpdatingAir = new HashMap<>();
    private final Map<UUID, Long> disconnectedPlayerThirstTimers = new HashMap<>();
    private final Map<UUID, Integer> divingPlayers = new HashMap<>();
    private static final int VANILLA_MAX_AIR = 300;

    // --- Config Values ---
    private final int DECREASE_AMOUNT;
    private final int MAX_AIR;
    private final int MIN_AIR;
    private final int DECREASE_INTERVAL_TICKS;
    private final int DAMAGE_INTERVAL_TICKS;
    private final double DAMAGE_AMOUNT;
    private final int POTION_RECOVER_AMOUNT;
    private final int STEW_RECOVER_AMOUNT;

    public ThirstSystem(Plugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        // Load thirst settings
        this.MAX_AIR = config.getInt("thirst.max-thirst", 285);
        this.MIN_AIR = config.getInt("thirst.min-thirst", -15);
        this.DECREASE_AMOUNT = config.getInt("thirst.decrease-amount", 30);
        this.DECREASE_INTERVAL_TICKS = config.getInt("thirst.decrease-interval-seconds", 30) * 20;
        this.DAMAGE_AMOUNT = config.getDouble("thirst.damage-amount", 2.0);
        this.DAMAGE_INTERVAL_TICKS = config.getInt("thirst.damage-interval-seconds", 3) * 20;
        this.POTION_RECOVER_AMOUNT = config.getInt("thirst.potion-recover-amount", 60);
        this.STEW_RECOVER_AMOUNT = config.getInt("thirst.stew-recover-amount", 30);

        startThirstAndDamageTask();
    }

    /**
     * Initializes thirst for a player on join or respawn.
     * If reset is true, thirst is set to max.
     * @param player The player to initialize.
     * @param reset  Whether to reset the player's thirst to max.
     */
    public void initializeForPlayer(Player player, boolean reset) {
        initializePlayer(player, reset);
        UUID playerUUID = player.getUniqueId();

        // If the player is rejoining, restore their thirst timer.
        if (disconnectedPlayerThirstTimers.containsKey(playerUUID)) {
            long remainingTime = disconnectedPlayerThirstTimers.remove(playerUUID);
            nextThirstDecreaseTime.put(playerUUID, System.currentTimeMillis() + remainingTime);
        } else {
            // Otherwise, schedule a new decrease.
            scheduleNextDecrease(player);
        }
        // Reset the damage timer
        nextDamageTime.remove(playerUUID);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up player data to prevent memory leaks.
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Save the remaining time until the next thirst decrease.
        if (nextThirstDecreaseTime.containsKey(playerUUID)) {
            long remainingTime = nextThirstDecreaseTime.get(playerUUID) - System.currentTimeMillis();
            if (remainingTime > 0) {
                disconnectedPlayerThirstTimers.put(playerUUID, remainingTime);
            }
        }

        nextThirstDecreaseTime.remove(playerUUID);
        isUpdatingAir.remove(playerUUID);
        nextDamageTime.remove(playerUUID);
        divingPlayers.remove(playerUUID);
    }

    @EventHandler
    public void onAirChange(EntityAirChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // If this plugin is in the process of changing the air level, ignore the event to prevent recursion.
        if (isUpdatingAir.getOrDefault(player.getUniqueId(), false)) {
            return;
        }
        // If the player is not diving, cancel the vanilla air change event.
        if (!divingPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material type = event.getItem().getType();

        // Water Bottle (recovers thirst)
        if (type == Material.POTION) {
            if (event.getItem().getItemMeta() instanceof PotionMeta) {
                PotionMeta meta = (PotionMeta) event.getItem().getItemMeta();
                if (meta.getBasePotionData().getType() == PotionType.WATER) {
                    addThirst(player, POTION_RECOVER_AMOUNT);
                }
            }
        }

        // Stews and Soups (recover thirst)
        else if (type == Material.MUSHROOM_STEW ||
                type == Material.RABBIT_STEW ||
                type == Material.BEETROOT_SOUP) {
            addThirst(player, STEW_RECOVER_AMOUNT);
        }
    }


    private void initializePlayer(Player player, boolean reset) {
        UUID playerUUID = player.getUniqueId();
        isUpdatingAir.put(playerUUID, true);
        try {
            player.setMaximumAir(MAX_AIR);
            if (reset) {
                player.setRemainingAir(MAX_AIR);
            } else {
                // Ensure the current air doesn't exceed the custom max, which can happen on rejoin.
                player.setRemainingAir(Math.min(player.getRemainingAir(), MAX_AIR));
            }
        } finally {
            isUpdatingAir.put(playerUUID, false);
        }
    }

    private void startThirstAndDamageTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID playerUUID = player.getUniqueId();
                    boolean isSubmerged = player.getEyeLocation().getBlock().getType() == Material.WATER;

                    // Moment the player enters the water
                    if (isSubmerged && !divingPlayers.containsKey(playerUUID)) {
                        switchToOxygenMode(player);
                        continue; // Skip further thirst processing for this tick
                    }

                    // Moment the player leaves the water
                    if (!isSubmerged && divingPlayers.containsKey(playerUUID)) {
                        switchToThirstMode(player);
                    }

                    // Only run thirst and damage logic when on land
                    if (!isSubmerged) {
                        // --- Thirst Decrease Logic ---
                        if (nextThirstDecreaseTime.containsKey(playerUUID)) {
                            long scheduledTime = nextThirstDecreaseTime.get(playerUUID);

                            if (now >= scheduledTime) {
                                int currentAir = player.getRemainingAir();
                                if (currentAir > MIN_AIR) {
                                    isUpdatingAir.put(playerUUID, true);
                                    try {
                                        int newAir = Math.max(MIN_AIR, currentAir - DECREASE_AMOUNT);
                                        player.setRemainingAir(newAir);
                                    } finally {
                                        isUpdatingAir.put(playerUUID, false);
                                    }
                                }
                                scheduleNextDecrease(player);
                            }
                        }

                        // --- Damage Logic ---
                        int currentAir = player.getRemainingAir();
                        if (currentAir <= 0) {
                            // Check if a damage timer is set
                            if (nextDamageTime.containsKey(playerUUID)) {
                                long scheduledDamageTime = nextDamageTime.get(playerUUID);
                                if (now >= scheduledDamageTime) {
                                    isUpdatingAir.put(playerUUID, true);
                                    try {
                                        player.damage(DAMAGE_AMOUNT);
                                    } finally {
                                        isUpdatingAir.put(playerUUID, false);
                                    }
                                    // Schedule the next damage tick
                                    scheduleNextDamage(player);
                                }
                            } else {
                                // First time the gauge is at zero, schedule the initial damage after a delay.
                                scheduleNextDamage(player);
                            }
                        } else {
                            nextDamageTime.remove(playerUUID);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second
    }

    private void addThirst(Player player, int amount) {
        // Do not recover thirst while diving
        if (divingPlayers.containsKey(player.getUniqueId())) return;

        int current = player.getRemainingAir();
        isUpdatingAir.put(player.getUniqueId(), true);
        try {
            player.setRemainingAir(Math.min(current + amount, MAX_AIR));
        } finally {
            isUpdatingAir.put(player.getUniqueId(), false);
        }
    }

    private void scheduleNextDecrease(Player player) {
        long decreaseIntervalMillis = (long) (DECREASE_INTERVAL_TICKS / 20.0 * 1000.0);
        nextThirstDecreaseTime.put(player.getUniqueId(), System.currentTimeMillis() + decreaseIntervalMillis);
    }

    private void scheduleNextDamage(Player player) {
        long damageIntervalMillis = (long) (DAMAGE_INTERVAL_TICKS / 20.0 * 1000.0);
        nextDamageTime.put(player.getUniqueId(), System.currentTimeMillis() + damageIntervalMillis);
    }

    private void switchToOxygenMode(Player player) {
        UUID playerUUID = player.getUniqueId();
        // Save the current thirst value
        divingPlayers.put(playerUUID, player.getRemainingAir());
        isUpdatingAir.put(playerUUID, true);
        try {
            // Revert to vanilla oxygen settings
            player.setMaximumAir(VANILLA_MAX_AIR);
            player.setRemainingAir(VANILLA_MAX_AIR);
        } finally {
            isUpdatingAir.put(playerUUID, false);
        }
    }

    private void switchToThirstMode(Player player) {
        UUID playerUUID = player.getUniqueId();
        // Retrieve the saved thirst value and remove the player from the diving list
        int lastThirst = divingPlayers.remove(playerUUID);
        isUpdatingAir.put(playerUUID, true);
        try {
            player.setMaximumAir(MAX_AIR);
            player.setRemainingAir(lastThirst);
        } finally {
            isUpdatingAir.put(playerUUID, false);
        }
    }
}
