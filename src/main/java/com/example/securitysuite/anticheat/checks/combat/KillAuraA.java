package com.example.securitysuite.anticheat.checks.combat;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * KillAura rarely shows up as one clean signal - it shows up as several
 * weak signals occurring together: attacking without looking at the
 * target (angle between eye direction and target exceeds a human-plausible
 * FOV), multiple attacks per tick, and attacking through obstructions.
 * This check focuses on the "attacked without aiming at target" signal;
 * it is intentionally combined with AimA/ReachA/AutoClickerA upstream
 * (see CombatListener) rather than trying to be a complete detector alone -
 * a single-check killaura detector is exactly the kind of naive
 * threshold-based design this project deliberately avoids.
 */
public class KillAuraA implements Check {

    private static final double MAX_HUMAN_ATTACK_ANGLE_DEGREES = 60.0;

    @Override
    public String id() { return "KillAuraA"; }

    @Override
    public String category() { return "combat"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        return CheckResult.clean(); // see overload below
    }

    public CheckResult evaluate(Player attacker, PlayerData data, Vector toTarget) {
        Vector look = attacker.getEyeLocation().getDirection().normalize();
        Vector target = toTarget.clone().normalize();

        double dot = Math.max(-1.0, Math.min(1.0, look.dot(target)));
        double angleDegrees = Math.toDegrees(Math.acos(dot));

        if (angleDegrees <= MAX_HUMAN_ATTACK_ANGLE_DEGREES) {
            return CheckResult.clean();
        }

        double overshoot = angleDegrees - MAX_HUMAN_ATTACK_ANGLE_DEGREES;
        double confidence = Math.min(0.95, overshoot / 90.0);
        double vl = Math.min(8.0, overshoot / 15.0);

        return CheckResult.flag(confidence, vl,
                String.format("attacked at %.1f\u00b0 off crosshair (max plausible %.1f\u00b0)",
                        angleDegrees, MAX_HUMAN_ATTACK_ANGLE_DEGREES));
    }
}
