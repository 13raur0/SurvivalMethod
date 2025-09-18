package com.braur0.SurvivalMethod;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.Map;

public class SurvivalMethod extends JavaPlugin implements Listener {

    private final Map<Player, Double> stamina = new HashMap<>();
    private final Map<Player, Long> lastExhaustionTime = new HashMap<>();
    private final double MAX_STAMINA = 100.0;
    private final double JUMP_UNLOCK_THRESHOLD = MAX_STAMINA * 0.2; // 20%

    // 渇き管理
    private final Map<Player, Integer> thirst = new HashMap<>();
    private final int MAX_THIRST = 300;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        // 毎秒スタミナ自動回復
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
                        if (current > MAX_STAMINA * 0.2) {
                            removeJumpBlock(player);
                            player.setWalkSpeed(0.2f);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        // 渇き減少（5秒ごと）
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    int current = thirst.getOrDefault(player, MAX_THIRST);
                    if (current > 0) {
                        current = Math.max(current - 5, 0);
                        thirst.put(player, current);
                        player.setRemainingAir(current);
                    } else {
                        player.damage(player.getMaxHealth() * 0.10);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 100L);
    }

    // プレイヤー参加時に初期化
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        thirst.put(player, MAX_THIRST);
        player.setRemainingAir(MAX_THIRST);
    }

    private void updateExpBar(Player player, double value) {
        player.setExp((float)(value / MAX_STAMINA));
    }

    private void addThirst(Player player, int amount) {
        int current = thirst.getOrDefault(player, MAX_THIRST);
        int newValue = Math.min(current + amount, MAX_THIRST);
        thirst.put(player, newValue);

        // HUD即時更新
        player.setRemainingAir(newValue);
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material item = event.getItem().getType();

        switch (item) {
            case POTION:
                if (event.getItem().getItemMeta() instanceof PotionMeta meta) {
                    PotionData data = meta.getBasePotionData();
                    if (data.getType() == PotionType.WATER) {
                        addThirst(player, 60);
                    }
                }
                break;

            case MUSHROOM_STEW:
            case BEETROOT_SOUP:
            case RABBIT_STEW:
            case SUSPICIOUS_STEW:
                addThirst(player, 20);
                break;

            default:
                break;
        }
    }

    @EventHandler
    public void onSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (event.isSprinting()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !player.isSprinting()) {
                        this.cancel();
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
                    }
                }
            }.runTaskTimer(this, 0L, 20L);
        }
    }

    private void applyJumpBlock(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP,
                -1,
                200,
                false,
                false,
                false
        ));
    }

    private void removeJumpBlock(Player player) {
        player.removePotionEffect(PotionEffectType.JUMP);
    }

    @EventHandler
    public void onAirChange(EntityAirChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        int current = thirst.getOrDefault(player, MAX_THIRST);

        // Map の値より増える場合のみキャンセル（サーバー自動回復の無効化）
        if (event.getAmount() > current) {
            event.setCancelled(true);
        }
    }
}
