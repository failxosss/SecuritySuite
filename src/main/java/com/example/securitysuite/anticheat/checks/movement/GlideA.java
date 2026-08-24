package com.example.securitysuite.anticheat.checks.movement;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Flags a glide-style trajectory (shallow, sustained horizontal travel
 * combined with slow, controlled descent) occurring while the player is
 * NOT actually gliding with an elytra. Legitimate elytra flight is
 * excluded entirely via data.isGliding().
 */
public class GlideA implements Check {

    private static final double MIN_HORIZONTAL_FOR_GLIDE_SHAPE = 0.35;
    private static final double MAX_DESCENT_FOR_GLIDE_SHAPE = 0.15;
    private static final int REQUIRED_CONSECUTIVE_SAMPLES = 5;

    @Override
    public String id() { return "GlideA"; }

    @Override
    public String category() { return "movement"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        if (data.isGliding() || data.isInVehicle() || data.isInWater()) return CheckResult.clean();
        if (player.isOnGround()) return CheckResult.clean();

        List<Location> history = data.getMovementHistory();
        if (history.size() < REQUIRED_CONSECUTIVE_SAMPLES + 1) return CheckResult.clean();

        int glideLikeSamples = 0;
        for (int i = history.size() - REQUIRED_CONSECUTIVE_SAMPLES; i < history.size(); i++) {
            Location a = history.get(i - 1);
            Location b = history.get(i);
            if (!a.getWorld().equals(b.getWorld())) return CheckResult.clean();
            double horizontal = Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
            double descent = a.getY() - b.getY();
            if (horizontal >= MIN_HORIZONTAL_FOR_GLIDE_SHAPE && descent >= 0 && descent <= MAX_DESCENT_FOR_GLIDE_SHAPE) {
                glideLikeSamples++;
            }
        }

        if (glideLikeSamples >= REQUIRED_CONSECUTIVE_SAMPLES) {
            return CheckResult.flag(0.7, 4.0,
                    "elytra-style glide trajectory (" + glideLikeSamples + " samples) without elytra active");
        }

        return CheckResult.clean();
    }
}
