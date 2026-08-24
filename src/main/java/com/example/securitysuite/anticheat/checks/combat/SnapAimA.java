package com.example.securitysuite.anticheat.checks.combat;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Aim-assist / silent-aim style killaura variants often snap the crosshair
 * directly onto a target in a single tick right before attacking, rather
 * than turning smoothly over several ticks the way mouse input does. This
 * looks only at the single most recent rotation delta (not a sum over
 * several ticks) specifically so that a fast-but-genuine flick shot,
 * spread across its own several ticks of gradually increasing delta,
 * doesn't get punished the same as one true single-tick snap.
 */
public class SnapAimA implements Check {

    private static final double SNAP_THRESHOLD_DEGREES = 70.0;

    @Override
    public String id() { return "SnapAimA"; }

    @Override
    public String category() { return "combat"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        List<float[]> rotations = data.getRotationHistory();
        if (rotations.size() < 2) return CheckResult.clean();

        float[] prev = rotations.get(rotations.size() - 2);
        float[] curr = rotations.get(rotations.size() - 1);

        double yawDelta = angleDiff(prev[0], curr[0]);
        double pitchDelta = Math.abs(curr[1] - prev[1]);
        double combined = Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);

        if (combined <= SNAP_THRESHOLD_DEGREES) return CheckResult.clean();

        double overshoot = combined - SNAP_THRESHOLD_DEGREES;
        double confidence = Math.min(0.85, overshoot / 90.0);
        double vl = Math.min(6.0, overshoot / 20.0);

        return CheckResult.flag(confidence, vl,
                String.format("rotated %.1f\u00b0 in a single tick immediately before attacking (max plausible %.1f\u00b0)",
                        combined, SNAP_THRESHOLD_DEGREES));
    }

    private double angleDiff(float a, float b) {
        double diff = Math.abs(a - b) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }
}
