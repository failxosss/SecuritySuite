package com.example.securitysuite.anticheat.checks.movement;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Vanilla caps upward climb speed on ladders/vines/scaffolding well below
 * normal walk speed. Only the *upward* direction is capped this tightly -
 * sliding down a ladder is fast and completely legitimate, so descending
 * motion is exempted entirely rather than trying to model it.
 */
public class FastLadderA implements Check {

    private static final double MAX_CLIMB_SPEED = 0.13; // vertical blocks/tick ceiling, with margin

    private final SecurityPlugin plugin;

    public FastLadderA(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() { return "FastLadderA"; }

    @Override
    public String category() { return "movement"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        if (!data.isOnLadder()) return CheckResult.clean();

        List<Location> history = data.getMovementHistory();
        if (history.size() < 2) return CheckResult.clean();

        Location from = history.get(history.size() - 2);
        Location to = history.get(history.size() - 1);
        if (!from.getWorld().equals(to.getWorld())) return CheckResult.clean();

        double dy = to.getY() - from.getY();
        if (dy <= 0) return CheckResult.clean();

        long tick = plugin.getCurrentTick();
        if (data.recentlyTeleported(tick, 3) || data.recentlyVelocity(tick, 6) || data.recentlyKnockback(tick, 6)) {
            return CheckResult.clean();
        }

        double allowed = MAX_CLIMB_SPEED * plugin.getCheckManager().getCompensationService().tpsLeniencyMultiplier();
        if (dy <= allowed) return CheckResult.clean();

        double overshoot = dy - allowed;
        double confidence = Math.min(0.8, overshoot / allowed);
        double vl = Math.min(5.0, overshoot * 15.0);

        return CheckResult.flag(confidence, vl,
                String.format("climbed %.3f blocks/tick on ladder/vine/scaffolding (max plausible %.3f)", dy, allowed));
    }
}
