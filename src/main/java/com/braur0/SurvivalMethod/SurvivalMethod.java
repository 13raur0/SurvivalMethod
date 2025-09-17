package com.braur0.SurvivalMethod;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.entity.EntityAirChangeEvent;

import java.util.HashMap;
import java.util.Map;

public class SurvivalMethod extends JavaPlugin implements Listener {

    private final Map<Player, Double> stamina = new HashMap<>();
    private final Map<Player, Long> lastExhaustionTime = new HashMap<>();
    private final double MAX_STAMINA = 100.0;
    private final double JUMP_UNLOCK_THRESHOLD = MAX_STAMINA * 0.2; // 20%
    private final Map<Player, Boolean> updatingAir = new HashMap<>();

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
                    // ✅ ここでMAXなら処理スキップ
                    if (current >= MAX_STAMINA) {
                        continue;
                    }
                    long lastExhaustion = lastExhaustionTime.getOrDefault(player, now);
                    long idleMillis = now - lastExhaustion;
                    if (!player.isSprinting()) {
                        // 経過時間に応じて回復量を増加
                        double regen = 1 + Math.min(idleMillis / 5000.0, 4); // 最大5倍速
                        current = Math.min(current + regen, MAX_STAMINA);
                        stamina.put(player, current);
                        updateExpBar(player, current);

                        // ✅ スタミナ20%以上でジャンプブロック解除
                        if (current > MAX_STAMINA * 0.2) {
                            removeJumpBlock(player);
                            player.setWalkSpeed(0.2f);
                        }
                    }
                }
            }

        }.runTaskTimer(this, 0L, 20L);

        // 酸素減少（5秒ごと）
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    int remaining = player.getRemainingAir();
                    if (remaining < 0) {
                        player.damage(1); // ハーフハート1つ分 = 1.0
                    }
                    player.setRemainingAir(remaining - 5);
                }
            }
        }.runTaskTimer(this, 0L, 100L); // 100tick = 5秒
    }

    private void updateExpBar(Player player, double value) {
        player.setExp((float)(value / MAX_STAMINA));
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

                    // スタミナが無くなったら歩行速度を落としジャンプ不能
                    if (current <= 0) {
                        player.setWalkSpeed(0.1f);
                        applyJumpBlock(player);
                    } else {
                        player.setWalkSpeed(0.2f);
                    }
                }
            }.runTaskTimer(this, 0L, 20L); // 10tickごとにスタミナ消費
        }
    }

    private void applyJumpBlock(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP,
                -1, // 事実上の永続
                200,               // Lv200 → ジャンプ不能
                false,
                false,
                false
        ));
    }

    private void removeJumpBlock(Player player) {
        player.removePotionEffect(PotionEffectType.JUMP);
    }

    @EventHandler
    public void onSprintToggle(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        double current = stamina.getOrDefault(player, MAX_STAMINA);

        if (event.isSprinting() && current <= 0) {
            event.setCancelled(true); // スタミナ0ならスプリント禁止
            player.setWalkSpeed(0.1f);
            applyJumpBlock(player);
        }
    }


    @EventHandler
    public void onAirChange(EntityAirChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (updatingAir.getOrDefault(player, false)) return; // 再入防止
        updatingAir.put(player, true);

        try {
            // 酸素が増える場合だけキャンセル
            if (event.getAmount() > player.getRemainingAir()) {
                event.setCancelled(true);
            }

        } finally {
            updatingAir.put(player, false);
        }
    }

}
