package com.example.securitysuite.anticheat.checks.combat;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Detects unnatural aim behaviour: perfectly consistent snap angles,
 * GCD-friendly rotation deltas (e.g. every rotation being an exact
 * multiple of a fixed step - a classic sign of rotation-generation
 * cheats), or a rotation delta that is suspiciously constant across
 * several consecutive combat ticks in a row (real human aim has
 * continuously varying micro-adjustments).
 *
 * This is a *signal* check, not a standalone accusation - it is designed
 * to be combined with ReachA/AutoClickerA/CriticalsA by the caller (see
 * README "combining signals") before any punishment decision is made.
 */
public class AimA implements Check {

    private static final int MIN_SAMPLES = 6;
    private static final double IDENTICAL_DELTA_EPSILON = 0.02; // degrees

    @Override
    public String id() { return "AimA"; }

    @Override
    public String category() { return "combat"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        List<float[]> rotations = data.getRotationHistory();
        if (rotations.size() < MIN_SAMPLES) return CheckResult.clean();

        List<float[]> recent = rotations.subList(Math.max(0, rotations.size() - MIN_SAMPLES), rotations.size());

        double[] yawDeltas = new double[recent.size() - 1];
        for (int i = 1; i < recent.size(); i++) {
            double d = angleDiff(recent.get(i)[0], recent.get(i - 1)[0]);
            yawDeltas[i - 1] = d;
        }

        int identicalRun = longestIdenticalRun(yawDeltas);
        boolean anyMovement = false;
        for (double d : yawDeltas) if (Math.abs(d) > 0.5) { anyMovement = true; break; }

        if (anyMovement && identicalRun >= 4) {
            double confidence = Math.min(0.9, 0.3 + identicalRun * 0.1);
            return CheckResult.flag(confidence, 2.5,
                    "identical yaw deltas across " + identicalRun + " consecutive rotations (GCD/aim-assist pattern)");
        }

        return CheckResult.clean();
    }

    private double angleDiff(float a, float b) {
        double diff = (a - b) % 360.0;
        if (diff < -180) diff += 360;
        if (diff > 180) diff -= 360;
        return diff;
    }

    private int longestIdenticalRun(double[] deltas) {
        int longest = 1;
        int current = 1;
        for (int i = 1; i < deltas.length; i++) {
            if (Math.abs(deltas[i] - deltas[i - 1]) < IDENTICAL_DELTA_EPSILON) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return deltas.length == 0 ? 0 : longest;
    }
}
