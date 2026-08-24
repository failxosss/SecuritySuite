package com.example.securitysuite.anticheat.checks.player;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks health restored specifically via EntityRegainHealthEvent's
 * REGEN reason (natural saturation-driven regen, roughly 1 HP every 4s
 * when well fed) - not golden apples, potions, or other explicit sources,
 * which have their own reasons and their own legitimate, much larger
 * amounts. A client/exploit forcing extra natural-regen ticks (health/god
 * hacks that lean on this specific reason rather than raw setHealth,
 * which the server would reject) shows up as REGEN-reason healing well
 * beyond what saturation ticking can produce. The ceiling is set with a
 * deliberately generous margin above the vanilla ~0.25 HP/s rate.
 */
public class RegenA implements Check {

    private static final long WINDOW_MS = 5000;
    private static final double MAX_REGEN_PER_WINDOW = 4.0;

    private final Map<UUID, Deque<double[]>> regenLog = new ConcurrentHashMap<>(); // each entry: [timestampMs, amount]

    @Override
    public String id() { return "RegenA"; }

    @Override
    public String category() { return "player"; }

    public void recordNaturalRegen(Player player, double amount) {
        Deque<double[]> log = regenLog.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        log.addLast(new double[]{now, amount});
        while (!log.isEmpty() && now - log.peekFirst()[0] > WINDOW_MS * 2L) {
            log.pollFirst();
        }
    }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        Deque<double[]> log = regenLog.get(player.getUniqueId());
        if (log == null) return CheckResult.clean();

        long now = System.currentTimeMillis();
        double total = 0;
        for (double[] entry : log) {
            if (now - entry[0] <= WINDOW_MS) total += entry[1];
        }

        if (total <= MAX_REGEN_PER_WINDOW) return CheckResult.clean();

        double overshoot = total - MAX_REGEN_PER_WINDOW;
        double confidence = Math.min(0.7, overshoot / MAX_REGEN_PER_WINDOW);
        double vl = Math.min(4.0, overshoot);

        return CheckResult.flag(confidence, vl,
                String.format("regenerated %.1f HP via natural regen within %dms (max plausible %.1f) - possible regen/heal hack",
                        total, WINDOW_MS, MAX_REGEN_PER_WINDOW));
    }

    public void cleanup(UUID uuid) {
        regenLog.remove(uuid);
    }
}
