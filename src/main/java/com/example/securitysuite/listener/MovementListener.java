package com.example.securitysuite.listener;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MovementListener implements Listener {

    private final SecurityPlugin plugin;
    private final Map<UUID, Double> fallStart = new ConcurrentHashMap<>();

    public MovementListener(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (!plugin.getConfigManager().getBoolean("anticheat.enabled", true)) return;

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        updateEnvironmentFlags(player, data);

        if (event.getTo() != null) {
            data.pushMovement(event.getTo());
            data.pushRotation(event.getTo().getYaw(), event.getTo().getPitch());
        }
        data.setPingMs(plugin.getCompensationService().getPing(player));

        // fall-distance tracking for NoFallA
        if (player.isOnGround()) {
            Double start = fallStart.remove(player.getUniqueId());
            if (start != null) {
                double fallDistance = start - player.getLocation().getY();
                if (fallDistance > 0) {
                    runNoFall(player, data, fallDistance);
                }
            }
        } else {
            fallStart.putIfAbsent(player.getUniqueId(), player.getLocation().getY());
        }

        if (plugin.getConfigManager().getBoolean("anticheat.checks.movement", true)) {
            runAndReport(player, data, plugin.getCheckManager().speedA);
            runAndReport(player, data, plugin.getCheckManager().flyA);
            runAndReport(player, data, plugin.getCheckManager().jesusA);
            runAndReport(player, data, plugin.getCheckManager().stepA);
            runAndReport(player, data, plugin.getCheckManager().glideA);
            runAndReport(player, data, plugin.getCheckManager().timerA);
            runAndReport(player, data, plugin.getCheckManager().noSlowA);
            runAndReport(player, data, plugin.getCheckManager().phaseA);
            runAndReport(player, data, plugin.getCheckManager().fastLadderA);
            runAndReport(player, data, plugin.getCheckManager().invalidSprintA);
        }
        if (plugin.getConfigManager().getBoolean("anticheat.checks.packet", true)) {
            runAndReport(player, data, plugin.getCheckManager().badPacketA);
            var rotResult = plugin.getCheckManager().invalidRotationA.evaluate(player, data,
                    event.getTo() != null ? event.getTo().getYaw() : 0,
                    event.getTo() != null ? event.getTo().getPitch() : 0);
            plugin.getCheckManager().report(player, plugin.getCheckManager().invalidRotationA, rotResult);
        }
    }

    private void runNoFall(Player player, PlayerData data, double fallDistance) {
        // A slight delay lets the EntityDamageEvent (fall damage) fire first
        // in the same tick sequence before we decide "no damage occurred".
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            boolean damageFired = recentFallDamage.remove(player.getUniqueId()) != null;
            var result = plugin.getCheckManager().noFallA.evaluate(player, data, fallDistance, damageFired);
            plugin.getCheckManager().report(player, plugin.getCheckManager().noFallA, result);
        }, 2L);
    }

    private final Map<UUID, Long> recentFallDamage = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            recentFallDamage.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer().getUniqueId());
        data.markTeleport(plugin.getCurrentTick());
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent event) {
        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer().getUniqueId());
        data.markVelocity(plugin.getCurrentTick());
    }

    private void updateEnvironmentFlags(Player player, PlayerData data) {
        Material feet = player.getLocation().getBlock().getType();
        Material below = player.getLocation().clone().subtract(0, 0.2, 0).getBlock().getType();

        data.setOnIce(feet == Material.ICE || feet == Material.PACKED_ICE || feet == Material.BLUE_ICE
                || below == Material.ICE || below == Material.PACKED_ICE || below == Material.BLUE_ICE
                || feet == Material.FROSTED_ICE || below == Material.FROSTED_ICE);
        data.setInWater(player.getLocation().getBlock().isLiquid());
        data.setOnLadder(feet == Material.LADDER || feet == Material.VINE || feet == Material.SCAFFOLDING);
        data.setInVehicle(player.isInsideVehicle());
        data.setGliding(player.isGliding());
        data.setHandRaised(player.isHandRaised(), plugin.getCurrentTick());
    }

    private void runAndReport(Player player, PlayerData data, Check check) {
        if (!check.isEnabled(plugin)) return;
        long start = System.nanoTime();
        CheckResult result = check.evaluate(player, data);
        plugin.getPerformanceStats().recordCheckTime(check.id(), System.nanoTime() - start);
        plugin.getCheckManager().report(player, check, result);
    }
}
