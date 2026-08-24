package com.example.securitysuite.anticheat.checks.movement;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Vanilla auto-step lets a player walk up a single half-block-ish ledge
 * (technically up to 0.6 for most entities) without jumping. "Step"/
 * "NoWeb" style cheats let the player instantly ascend a full block or
 * more without a jump input. We flag a same-tick vertical gain beyond the
 * vanilla auto-step allowance when the player did not jump (no upward
 * velocity impulse recorded) and is not climbing/swimming/using scaffolding.
 */
public class StepA implements Check {

    private static final double VANILLA_AUTOSTEP = 0.6001;

    private final SecurityPlugin plugin;

    public StepA(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() { return "StepA"; }

    @Override
    public String category() { return "movement"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        if (data.isOnLadder() || data.isInWater() || data.isGliding() || data.isInVehicle()) return CheckResult.clean();
        if (data.recentlyTeleported(plugin.getCurrentTick(), 5)) return CheckResult.clean();
        if (data.recentlyVelocity(plugin.getCurrentTick(), 10)) return CheckResult.clean();
        if (!player.isOnGround()) return CheckResult.clean();

        List<Location> history = data.getMovementHistory();
        if (history.size() < 2) return CheckResult.clean();

        Location from = history.get(history.size() - 2);
        Location to = history.get(history.size() - 1);
        if (!from.getWorld().equals(to.getWorld())) return CheckResult.clean();

        double dy = to.getY() - from.getY();
        if (dy <= VANILLA_AUTOSTEP) return CheckResult.clean();

        // A jump has a characteristic initial upward velocity of ~0.42 blocks/tick;
        // anything between autostep and a real jump arc, landing ON GROUND on the
        // very next sample, is the suspicious "step" signature (a real jump would
        // still be airborne one tick later, not already grounded again).
        if (dy > VANILLA_AUTOSTEP && dy < 1.3) {
            double overshoot = dy - VANILLA_AUTOSTEP;
            double confidence = Math.min(0.8, overshoot / 0.5);
            return CheckResult.flag(confidence, Math.min(4.0, overshoot * 6),
                    String.format("instant %.3f block step-up while grounded without jump arc", dy));
        }

        return CheckResult.clean();
    }
}
