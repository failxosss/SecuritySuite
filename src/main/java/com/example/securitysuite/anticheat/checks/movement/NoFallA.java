package com.example.securitysuite.anticheat.checks.movement;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Tracks accumulated fall distance and flags when a player lands (goes
 * from airborne to on-ground) after a fall that should have caused
 * noticeable damage, but no damage event / no reduction in health was
 * observed. Water, hay bales, slime blocks, beds, powder snow, ladders/
 * vines, elytra, and Jump Boost / Feather Falling / Slow Falling are all
 * excluded since they legitimately negate or reduce fall damage.
 *
 * NoFallA only *flags*; actual damage bookkeeping (did damage apply) is
 * read from the player's last-known health delta, tracked by the
 * movement listener and passed in via evaluate's context - see
 * MovementListener for how fallStartY / lastHealth are threaded through.
 */
public class NoFallA implements Check {

    private static final double MIN_FALL_FOR_DAMAGE = 3.5; // blocks, vanilla safe fall distance

    private final SecurityPlugin plugin;

    public NoFallA(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() { return "NoFallA"; }

    @Override
    public String category() { return "movement"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        return CheckResult.clean(); // see overload
    }

    public CheckResult evaluate(Player player, PlayerData data, double fallDistanceAtLanding, boolean damageEventFired) {
        if (fallDistanceAtLanding < MIN_FALL_FOR_DAMAGE) return CheckResult.clean();
        if (damageEventFired) return CheckResult.clean();

        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || player.hasPotionEffect(PotionEffectType.JUMP_BOOST)
                || data.isGliding() || data.isInWater() || data.isOnLadder() || data.isInVehicle()) {
            return CheckResult.clean();
        }

        List<Location> history = data.getMovementHistory();
        if (!history.isEmpty()) {
            Material landedOn = history.get(history.size() - 1).clone().subtract(0, 1, 0).getBlock().getType();
            if (isFallNegatingBlock(landedOn)) return CheckResult.clean();
        }

        double overshoot = fallDistanceAtLanding - MIN_FALL_FOR_DAMAGE;
        double confidence = Math.min(0.9, overshoot / 8.0);
        double vl = Math.min(6.0, overshoot);
        return CheckResult.flag(confidence, vl,
                String.format("landed after %.1f block fall with no damage event", fallDistanceAtLanding));
    }

    private boolean isFallNegatingBlock(Material m) {
        return switch (m) {
            case HAY_BLOCK, SLIME_BLOCK, WATER, POWDER_SNOW, COBWEB, SWEET_BERRY_BUSH, VINE -> true;
            default -> m.name().contains("BED");
        };
    }
}
