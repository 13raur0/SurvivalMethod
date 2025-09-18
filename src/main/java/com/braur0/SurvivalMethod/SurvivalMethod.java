package com.braur0.SurvivalMethod;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class SurvivalMethod extends JavaPlugin implements Listener {

    private final Map<Player, Double> stamina = new HashMap<>();
    private final Map<Player, Long> lastExhaustionTime = new HashMap<>();
    private final Map<Player, Integer> thirst = new HashMap<>();

    private static final double MAX_STAMINA = 100.0;
    private static final double JUMP_UNLOCK_THRESHOLD = MAX_STAMINA * 0.2; // 20%
    private static final int MAX_THIRST = 300;
    private static final int THIRST_DECREMENT = 5;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        startStaminaRegenTask();
        startThirstTask();
    }

    // プレイヤー参加時の初期化
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        stamina.put(player, MAX_STAMINA);
        thirst.put(player, MAX_THIRST);
        player.setRemainingAir(MAX_THIRST);
        updateExpBar(player, MAX_STAMINA);
        player.setWalkSpeed(0.2f);
    }

    // --- スタミナ管理 ---
    private void startStaminaRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    double current = stamina.getOrDefault(player, MAX_STAMINA);
                    if (current >= MAX_STAMINA) continue;

                    long lastExhaustion = lastExhaustionTime.getOrDefault(player, now);
                    long idleMillis = now - lastExhaustion;

                    if (!player.isSprinting()) {
                        double regen = 1 + Math.min(idleMillis / 5000.0, 4);
                        current = Math.min(current + regen, MAX_STAMINA);
                        stamina.put(player, current);
                        updateExpBar(player, current);

                        if (current > JUMP_UNLOCK_THRESHOLD) {
                            removeJumpBlock(player);
                            player.setWalkSpeed(0.2f);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @EventHandler
    public void onSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();

        if (!event.isSprinting()) return;

        // スプリント中スタミナ減少タスク
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !player.isSprinting()) {
                    cancel();
                    return;
                }

                double current = stamina.getOrDefault(player, MAX_STAMINA);
                double hungerRatio = player.getFoodLevel() / 20.0;
                double sprintCost = 4 * (1 + (1 - hungerRatio));
                current = Math.max(current - sprintCost, 0);
                stamina.put(player, current);
                lastExhaustionTime.put(player, System.currentTimeMillis());
                updateExpBar(player, current);

                if (current <= 0) {
                    player.setWalkSpeed(0.1f);
                    applyJumpBlock(player);
                } else {
                    player.setWalkSpeed(0.2f);
                    removeJumpBlock(player);
                }
            }
        }.runTaskTimer(this, 0L, 20L);
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

    // --- 渇き管理 ---
    private void startThirstTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    int current = thirst.getOrDefault(player, MAX_THIRST);
                    if (current > 0) {
                        current = Math.max(current - THIRST_DECREMENT, 0);
                        thirst.put(player, current);
                        player.setRemainingAir(current);
                    } else {
                        player.damage(player.getMaxHealth() * 0.10);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 100L);
    }

    private void addThirst(Player player, int amount) {
        int current = thirst.getOrDefault(player, MAX_THIRST);
        int newValue = Math.min(current + amount, MAX_THIRST);
        thirst.put(player, newValue);
        player.setRemainingAir(newValue);
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material item = event.getItem().getType();

        switch (item) {
            case POTION -> {
                if (event.getItem().getItemMeta() instanceof PotionMeta meta) {
                    PotionData data = meta.getBasePotionData();
                    if (data.getType() == PotionType.WATER) {
                        addThirst(player, 60);
                    }
                }
            }
            case MUSHROOM_STEW, BEETROOT_SOUP, RABBIT_STEW, SUSPICIOUS_STEW -> addThirst(player, 20);
            default -> {
            }
        }
    }

    @EventHandler
    public void onAirChange(EntityAirChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        int current = thirst.getOrDefault(player, MAX_THIRST);
        if (event.getAmount() > current) {
            event.setCancelled(true);
        }
    }
}
