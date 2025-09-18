package com.braur0.SurvivalMethod;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class SurvivalMethod extends JavaPlugin implements Listener {

    private StaminaSystem staminaSystem;
    private ThirstSystem thirstSystem;

    private boolean resetOnJoin;
    private boolean resetOnRespawn;

    @Override
    public void onEnable() {
        // Load or create config.yml
        saveDefaultConfig();

        Bukkit.getPluginManager().registerEvents(this, this);

        // Load core settings
        this.resetOnJoin = getConfig().getBoolean("core.reset-on-join", true);
        this.resetOnRespawn = getConfig().getBoolean("core.reset-on-respawn", true);

        // Stamina system
        if (getConfig().getBoolean("stamina.enabled", true)) {
            this.staminaSystem = new StaminaSystem(this, getConfig());
            getServer().getPluginManager().registerEvents(staminaSystem, this);
            getLogger().info("Stamina system has been enabled.");
        }

        // Thirst system
        if (getConfig().getBoolean("thirst.enabled", true)) {
            this.thirstSystem = new ThirstSystem(this, getConfig());
            getServer().getPluginManager().registerEvents(thirstSystem, this);
            getLogger().info("Thirst system has been enabled.");
        }
    }

    // Handles player initialization on join.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (resetOnJoin) {
            Player player = event.getPlayer();
            player.setFoodLevel(20); // Set hunger to max
            if (staminaSystem != null) {
                staminaSystem.initializeForPlayer(player);
            }
            if (thirstSystem != null) {
                thirstSystem.initializeForPlayer(player);
            }
        }
    }

    // Handles player initialization on respawn.
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (resetOnRespawn) {
            Player player = event.getPlayer();
            // Delay by 1 tick to ensure player state is fully reset before processing.
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.setFoodLevel(20); // Set hunger to max
                    if (staminaSystem != null) {
                        staminaSystem.initializeForPlayer(player);
                    }
                    if (thirstSystem != null) {
                        thirstSystem.initializeForPlayer(player);
                    }
                }
            }.runTask(this);
        }
    }
}
