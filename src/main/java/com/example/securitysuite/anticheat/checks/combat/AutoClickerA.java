package com.example.securitysuite.anticheat.checks.combat;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Detects autoclicker/macro usage via two independent signals:
 *  1. Sustained CPS far above legitimate human capability.
 *  2. Suspiciously *low variance* between click intervals - real human
 *     clicking has natural jitter; many autoclickers fire on a near-fixed
 *     timer, which produces an unnaturally low standard deviation even at
 *     otherwise plausible CPS.
 * Both signals must agree (or CPS must be extreme) before flagging, which
 * is what keeps fast-but-human clickers from being punished.
 */
public class AutoClickerA implements Check {

    private static final int WINDOW_MS = 3000;
    private static final int EXTREME_CPS = 18;
    private static final int SUSPICIOUS_CPS = 12;
    private static final double LOW_VARIANCE_STDDEV_MS = 8.0; // ms

    @Override
    public String id() { return "AutoClickerA"; }

    @Override
    public String category() { return "combat"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        List<Long> clicks = data.getClickTimestamps();
        long now = System.currentTimeMillis();
        List<Long> recent = clicks.stream().filter(t -> now - t <= WINDOW_MS).toList();
        if (recent.size() < 8) return CheckResult.clean();

        double cps = recent.size() / (WINDOW_MS / 1000.0);

        double[] intervals = new double[recent.size() - 1];
        for (int i = 1; i < recent.size(); i++) {
            intervals[i - 1] = recent.get(i) - recent.get(i - 1);
        }
        double mean = average(intervals);
        double stddev = stddev(intervals, mean);

        if (cps >= EXTREME_CPS) {
            return CheckResult.flag(0.9, 6.0, String.format("extreme CPS %.1f (stddev %.1fms)", cps, stddev));
        }

        if (cps >= SUSPICIOUS_CPS && stddev < LOW_VARIANCE_STDDEV_MS) {
            double confidence = 0.5 + (SUSPICIOUS_CPS - stddev) / 50.0;
            return CheckResult.flag(Math.min(0.95, confidence), 3.0,
                    String.format("CPS %.1f with low timing variance (stddev %.1fms)", cps, stddev));
        }

        return CheckResult.clean();
    }

    private double average(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return values.length == 0 ? 0 : sum / values.length;
    }

    private double stddev(double[] values, double mean) {
        if (values.length == 0) return 0;
        double sumSq = 0;
        for (double v : values) sumSq += (v - mean) * (v - mean);
        return Math.sqrt(sumSq / values.length);
    }
}
