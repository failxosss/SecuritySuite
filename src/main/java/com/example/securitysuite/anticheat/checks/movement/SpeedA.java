package com.example.securitysuite.anticheat.checks.movement;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.CompensationService;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Flags horizontal movement speed beyond what vanilla mechanics allow for
 * the player's current state. Rather than a single hardcoded cap, the
 * allowed speed is built up from the player's actual state so that sprint,
 * Speed potions, ice, and soul sand/honey are all accounted for BEFORE we
 * ever compare against a threshold - see buildAllowedSpeed().
 */
public class SpeedA implements Check {

    private static final double BASE_WALK = 0.221; // blocks/tick, approx vanilla walk speed ceiling
    private static final double SPRINT_MULTIPLIER = 1.3;
    private static final double ICE_MULTIPLIER = 1.9;
    private static final double SOUL_SPEED_MULTIPLIER = 1.3;

    private final SecurityPlugin plugin;
    private final CompensationService compensation;

    public SpeedA(SecurityPlugin plugin, CompensationService compensation) {
        this.plugin = plugin;
        this.compensation = compensation;
    }

    @Override
    public String id() { return "SpeedA"; }

    @Override
    public String category() { return "movement"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        List<Location> history = data.getMovementHistory();
        if (history.size() < 2) return CheckResult.clean();

        Location from = history.get(history.size() - 2);
        Location to = history.get(history.size() - 1);
        if (!from.getWorld().equals(to.getWorld())) return CheckResult.clean();

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Exclusion zones where horizontal speed is legitimately very high
        // and this check should not attempt to model it at all.
        if (data.isGliding() || data.isInVehicle()
                || data.recentlyTeleported(currentTick(), 3)
                || data.recentlyKnockback(currentTick(), 6)
                || data.recentlyVelocity(currentTick(), 6)) {
            return CheckResult.clean();
        }

        double allowed = buildAllowedSpeed(player, data);

        // TPS-based leniency: under server lag, movement packets can bunch up.
        allowed *= compensation.tpsLeniencyMultiplier();

        if (horizontalDistance <= allowed) {
            return CheckResult.clean();
        }

        double overshoot = horizontalDistance - allowed;
        double confidence = Math.min(0.9, overshoot / (allowed * 0.75));
        double vl = Math.min(6.0, overshoot * 10.0);

        return CheckResult.flag(confidence, vl,
                String.format("moved %.3f blocks/tick (allowed %.3f)", horizontalDistance, allowed));
    }

    private double buildAllowedSpeed(Player player, PlayerData data) {
        double speed = BASE_WALK;
        if (player.isSprinting()) speed *= SPRINT_MULTIPLIER;
        if (data.isOnIce()) speed *= ICE_MULTIPLIER;

        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            speed *= (1.0 + 0.2 * amplifier);
        }

        try {
            if (player.getInventory().getBoots() != null
                    && player.getInventory().getBoots().getEnchantments().keySet().stream()
                        .anyMatch(e -> e.getKey().getKey().equalsIgnoreCase("soul_speed"))
                    && data.isOnIce() == false) {
                speed *= SOUL_SPEED_MULTIPLIER;
            }
        } catch (Throwable ignored) {
            // enchantment lookup can vary across Paper API minor versions; fail safe (no bonus applied)
        }

        return speed;
    }

    private long currentTick() {
        return plugin.getCurrentTick();
    }
}
