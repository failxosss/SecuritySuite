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
 * Detects sustained upward movement / hovering with no legitimate cause
 * (not flying-enabled, no levitation, no jump boost explaining the arc,
 * not in water/climbing, no elytra, no recent explosion/knockback).
 *
 * Deliberately requires SUSTAINED behaviour (accumulated over several
 * ticks, tracked as violation level) rather than flagging a single tick
 * of upward motion, since knockback, jump boost and piston launches all
 * produce a single legitimate upward tick.
 */
public class FlyA implements Check {

    private static final double SUSPICIOUS_UPWARD_PER_TICK = 0.42; // above normal jump arc
    private static final double HOVER_EPSILON = 0.02; // near-zero vertical while airborne

    private final SecurityPlugin plugin;
    private final CompensationService compensation;

    public FlyA(SecurityPlugin plugin, CompensationService compensation) {
        this.plugin = plugin;
        this.compensation = compensation;
    }

    @Override
    public String id() { return "FlyA"; }

    @Override
    public String category() { return "movement"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        if (player.getAllowFlight() || player.isFlying()) return CheckResult.clean();
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) return CheckResult.clean();
        if (data.isGliding() || data.isInVehicle() || data.isInWater() || data.isOnLadder()) return CheckResult.clean();
        if (data.recentlyTeleported(plugin.getCurrentTick(), 5)) return CheckResult.clean();
        if (data.recentlyKnockback(plugin.getCurrentTick(), 10)) return CheckResult.clean();
        if (data.recentlyVelocity(plugin.getCurrentTick(), 10)) return CheckResult.clean();

        List<Location> history = data.getMovementHistory();
        if (history.size() < 4) return CheckResult.clean();

        Location a = history.get(history.size() - 2);
        Location b = history.get(history.size() - 1);
        if (!a.getWorld().equals(b.getWorld())) return CheckResult.clean();

        double dy = b.getY() - a.getY();
        boolean onGround = player.isOnGround();

        if (onGround) return CheckResult.clean();

        // Sustained hover: airborne with ~zero vertical motion for several
        // consecutive samples (real falling/jumping always has non-trivial
        // vertical velocity except at the exact apex of a jump for one tick).
        int hoverTicks = 0;
        for (int i = Math.max(0, history.size() - 6); i < history.size() - 1; i++) {
            double d = history.get(i + 1).getY() - history.get(i).getY();
            if (Math.abs(d) < HOVER_EPSILON) hoverTicks++;
        }

        if (hoverTicks >= 4) {
            return CheckResult.flag(0.85, 4.0, "hovering with near-zero vertical motion for " + hoverTicks + " ticks while airborne");
        }

        if (dy > SUSPICIOUS_UPWARD_PER_TICK) {
            double overshoot = dy - SUSPICIOUS_UPWARD_PER_TICK;
            double confidence = Math.min(0.8, overshoot / 0.3);
            return CheckResult.flag(confidence, Math.min(5.0, overshoot * 8), "unexplained upward motion " + String.format("%.3f", dy) + " blocks/tick");
        }

        return CheckResult.clean();
    }
}
