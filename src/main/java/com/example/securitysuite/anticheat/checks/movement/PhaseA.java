package com.example.securitysuite.anticheat.checks.movement;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Samples points along the straight-line path between the last two
 * positions and flags any that land inside a solid, non-passable,
 * non-liquid block - i.e. the player's reported position moved *through*
 * a wall rather than around it. This is a simplified point-sampling
 * raycast, not a full bounding-box sweep against the player's actual
 * hitbox, so thin diagonal clips at block edges can be missed; it is
 * intentionally biased toward precision over recall (fewer false
 * positives on legitimate edge-of-block movement) rather than trying to
 * catch every possible phase vector.
 */
public class PhaseA implements Check {

    private static final double MAX_SEGMENT_LENGTH = 1.5; // beyond this, treat as teleport/knockback, not a walk step
    private static final int SAMPLES = 4;

    private final SecurityPlugin plugin;

    public PhaseA(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() { return "PhaseA"; }

    @Override
    public String category() { return "movement"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        List<Location> history = data.getMovementHistory();
        if (history.size() < 2) return CheckResult.clean();

        Location from = history.get(history.size() - 2);
        Location to = history.get(history.size() - 1);
        if (!from.getWorld().equals(to.getWorld())) return CheckResult.clean();

        double distance = from.distance(to);
        if (distance < 0.05 || distance > MAX_SEGMENT_LENGTH) return CheckResult.clean();

        if (data.recentlyTeleported(plugin.getCurrentTick(), 3)) return CheckResult.clean();

        int solidHits = 0;
        for (int i = 1; i < SAMPLES; i++) {
            double t = (double) i / SAMPLES;
            Location point = from.clone().add(
                    (to.getX() - from.getX()) * t,
                    (to.getY() - from.getY()) * t,
                    (to.getZ() - from.getZ()) * t);
            if (isFullySolidNonPassable(point)) solidHits++;
        }

        if (solidHits == 0) return CheckResult.clean();

        double confidence = Math.min(0.9, 0.35 * solidHits);
        double vl = Math.min(8.0, 3.0 * solidHits);

        return CheckResult.flag(confidence, vl,
                "movement path intersected a solid block (" + solidHits + "/" + (SAMPLES - 1)
                        + " samples) - possible noclip/phase");
    }

    private boolean isFullySolidNonPassable(Location loc) {
        try {
            var block = loc.getBlock();
            return block.getType().isSolid() && !block.isPassable() && !block.isLiquid();
        } catch (Throwable t) {
            return false; // fail safe: an API/version mismatch on block state should never itself produce a flag
        }
    }
}
