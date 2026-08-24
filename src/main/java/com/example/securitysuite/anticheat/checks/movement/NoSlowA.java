package com.example.securitysuite.anticheat.checks.movement;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Vanilla slows horizontal movement to roughly 20% of walk speed while the
 * client has an item "in use" - eating, drinking, drawing a bow, blocking
 * with a shield. NoSlow hacks tell the server the item is in use (so it
 * still gets the eat/block/draw effect) while telling the client to keep
 * moving at full speed. We only compare against a generous multiple of the
 * slowed cap, not the cap itself, to absorb diagonal-movement rounding and
 * momentum bleed from a sprint-jump taken right before the item was raised.
 */
public class NoSlowA implements Check {

    private static final double SLOWED_WALK_CAP = 0.221 * 0.2;
    private static final double MARGIN_MULTIPLIER = 1.8;

    @Override
    public String id() { return "NoSlowA"; }

    @Override
    public String category() { return "movement"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        if (!player.isHandRaised() && !player.isBlocking()) return CheckResult.clean();

        List<Location> history = data.getMovementHistory();
        if (history.size() < 2) return CheckResult.clean();

        Location from = history.get(history.size() - 2);
        Location to = history.get(history.size() - 1);
        if (!from.getWorld().equals(to.getWorld())) return CheckResult.clean();

        if (data.isGliding() || data.isInVehicle() || data.isInWater()) return CheckResult.clean();

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        double allowed = SLOWED_WALK_CAP * MARGIN_MULTIPLIER;
        if (horizontal <= allowed) return CheckResult.clean();

        double overshoot = horizontal - allowed;
        double confidence = Math.min(0.85, overshoot / allowed);
        double vl = Math.min(5.0, overshoot * 10.0);

        return CheckResult.flag(confidence, vl,
                String.format("moved %.3f blocks/tick while eating/drinking/blocking (expected <= %.3f) - possible NoSlow",
                        horizontal, allowed));
    }
}
