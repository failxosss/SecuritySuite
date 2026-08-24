package com.example.securitysuite.listener;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.PlayerData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.util.Vector;

public class CombatListener implements Listener {

    private final SecurityPlugin plugin;

    public CombatListener(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        // left-click swing -> used for CPS/autoclicker timing regardless of whether it lands a hit
        if (event.getAnimationType() != org.bukkit.event.player.PlayerAnimationType.ARM_SWING) return;
        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer().getUniqueId());
        data.pushClick();

        if (plugin.getConfigManager().getBoolean("anticheat.checks.combat", true)
                && plugin.getCheckManager().autoClickerA.isEnabled(plugin)) {
            var result = plugin.getCheckManager().autoClickerA.evaluate(event.getPlayer(), data);
            plugin.getCheckManager().report(event.getPlayer(), plugin.getCheckManager().autoClickerA, result);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!plugin.getConfigManager().getBoolean("anticheat.checks.combat", true)) return;

        PlayerData data = plugin.getPlayerDataManager().get(attacker.getUniqueId());
        var checkManager = plugin.getCheckManager();

        long tick = plugin.getCurrentTick();
        data.markAttack(tick);
        if (checkManager.multiActionA.isEnabled(plugin)) {
            var multiActionResult = checkManager.multiActionA.evaluate(attacker, data, tick);
            checkManager.report(attacker, checkManager.multiActionA, multiActionResult);
        }

        if (checkManager.snapAimA.isEnabled(plugin)) {
            var snapResult = checkManager.snapAimA.evaluate(attacker, data);
            checkManager.report(attacker, checkManager.snapAimA, snapResult);
        }

        double distance = attacker.getEyeLocation().distance(target.getLocation());
        if (checkManager.reachA.isEnabled(plugin)) {
            var result = checkManager.reachA.evaluate(attacker, data, distance, target);
            checkManager.report(attacker, checkManager.reachA, result);
        }

        if (checkManager.killAuraA.isEnabled(plugin)) {
            Vector toTarget = target.getEyeLocation().toVector().subtract(attacker.getEyeLocation().toVector());
            var result = checkManager.killAuraA.evaluate(attacker, data, toTarget);
            checkManager.report(attacker, checkManager.killAuraA, result);
        }

        if (checkManager.criticalsA.isEnabled(plugin)) {
            boolean claimedCritical = !attacker.isOnGround() && attacker.getFallDistance() > 0
                    && !attacker.isInsideVehicle();
            boolean eligible = eligibleForCritical(attacker, data);
            var result = checkManager.criticalsA.evaluate(attacker, data, claimedCritical, eligible);
            checkManager.report(attacker, checkManager.criticalsA, result);
        }

        if (checkManager.aimA.isEnabled(plugin)) {
            var result = checkManager.aimA.evaluate(attacker, data);
            checkManager.report(attacker, checkManager.aimA, result);
        }
    }

    private boolean eligibleForCritical(Player attacker, PlayerData data) {
        if (attacker.isOnGround()) return false;
        if (data.isInWater() || data.isOnLadder() || data.isInVehicle()) return false;
        if (attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)) return false;
        return attacker.getFallDistance() > 0;
    }
}
