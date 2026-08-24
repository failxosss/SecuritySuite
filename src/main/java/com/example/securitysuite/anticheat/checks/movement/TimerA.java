package com.example.securitysuite.anticheat.checks.movement;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Vanilla sends at most one movement packet per server tick (20/s). Timer
 * cheats speed up the client's internal clock so it *thinks* more ticks
 * have elapsed than the server allows, which manifests as a sustained
 * movement-packet rate above 20/s that SpeedA/FlyA alone won't catch if
 * the per-step distance stays within normal bounds - only the *rate* is
 * wrong, not any single step. We measure over a window (not packet-to-
 * packet) specifically to avoid flagging a single post-lag-spike burst,
 * which is legitimate and not timer abuse.
 */
public class TimerA implements Check {

    private static final long WINDOW_MS = 2000;
    private static final double MAX_MOVES_PER_SECOND = 22.0; // 20/s vanilla ceiling + jitter allowance
    private static final int MIN_SAMPLES = 12;

    @Override
    public String id() { return "TimerA"; }

    @Override
    public String category() { return "movement"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        List<Long> timestamps = data.getMoveTimestamps();
        if (timestamps.size() < MIN_SAMPLES) return CheckResult.clean();

        long now = timestamps.get(timestamps.size() - 1);
        long windowStart = now - WINDOW_MS;

        long count = 0;
        long earliestInWindow = now;
        for (long t : timestamps) {
            if (t >= windowStart) {
                count++;
                if (t < earliestInWindow) earliestInWindow = t;
            }
        }
        if (count < MIN_SAMPLES) return CheckResult.clean();

        double spanMs = now - earliestInWindow;
        if (spanMs <= 0) return CheckResult.clean(); // avoid divide-by-zero on a burst of same-millisecond packets

        double movesPerSecond = count / (spanMs / 1000.0);
        if (movesPerSecond <= MAX_MOVES_PER_SECOND) return CheckResult.clean();

        double overshoot = movesPerSecond - MAX_MOVES_PER_SECOND;
        double confidence = Math.min(0.85, overshoot / 10.0);
        double vl = Math.min(6.0, overshoot);

        return CheckResult.flag(confidence, vl,
                String.format("%.1f movement packets/sec over %.0fms (max plausible %.1f) - possible timer/tick manipulation",
                        movesPerSecond, spanMs, MAX_MOVES_PER_SECOND));
    }
}
