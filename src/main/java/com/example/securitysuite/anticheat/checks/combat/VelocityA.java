package com.example.securitysuite.anticheat.checks.combat;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Compares the horizontal knockback the server applied against how far the
 * player's position actually moved a few ticks later. Anti-knockback /
 * no-knockback hacks tell the server "I received it" (so the attacker
 * still sees their hit register normally) while the client silently
 * cancels or dampens the resulting motion. The compliance ratio is
 * intentionally lenient (35%) since terrain (stairs, water, a wall behind
 * the victim) can legitimately absorb a large share of applied knockback.
 */
public class VelocityA implements Check {

    private static final double MIN_COMPLIANCE_RATIO = 0.35;
    private static final double MIN_APPLIED_TO_JUDGE = 0.08; // ignore negligible knockback - nothing meaningful to measure

    @Override
    public String id() { return "VelocityA"; }

    @Override
    public String category() { return "combat"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        return CheckResult.clean(); // see the dedicated overload; this check needs the before/after locations
    }

    public CheckResult evaluate(Player player, PlayerData data, Vector appliedVelocity, Location beforeLoc, Location afterLoc) {
        if (appliedVelocity == null || beforeLoc == null || afterLoc == null) return CheckResult.clean();
        if (!beforeLoc.getWorld().equals(afterLoc.getWorld())) return CheckResult.clean();

        double predictedHorizontal = Math.sqrt(
                appliedVelocity.getX() * appliedVelocity.getX() + appliedVelocity.getZ() * appliedVelocity.getZ());
        if (predictedHorizontal < MIN_APPLIED_TO_JUDGE) return CheckResult.clean();

        double actualHorizontal = Math.sqrt(
                Math.pow(afterLoc.getX() - beforeLoc.getX(), 2) + Math.pow(afterLoc.getZ() - beforeLoc.getZ(), 2));
        double ratio = actualHorizontal / predictedHorizontal;

        if (ratio >= MIN_COMPLIANCE_RATIO) return CheckResult.clean();

        double deficit = MIN_COMPLIANCE_RATIO - ratio;
        double confidence = Math.min(0.8, deficit / MIN_COMPLIANCE_RATIO);
        double vl = Math.min(5.0, deficit * 8.0);

        return CheckResult.flag(confidence, vl,
                String.format("only followed through %.0f%% of applied knockback (min plausible %.0f%%) - possible anti-knockback",
                        ratio * 100, MIN_COMPLIANCE_RATIO * 100));
    }
}
