package com.example.securitysuite.anticheat;

import com.example.securitysuite.SecurityPlugin;
import org.bukkit.entity.Player;

/**
 * Central place for all "is this player's suspicious-looking behaviour
 * actually just lag/ping/server load" calculations, so individual checks
 * don't each reinvent this logic (and so tuning it is a one-file change).
 */
public class CompensationService {

    private final SecurityPlugin plugin;
    private volatile double lastKnownTps = 20.0;
    private volatile long lastTickTime = System.currentTimeMillis();
    private volatile boolean currentlyInLagSpike = false;

    public CompensationService(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    /** Call once per server tick from a scheduler task. */
    public void onServerTick() {
        long now = System.currentTimeMillis();
        long delta = now - lastTickTime;
        lastTickTime = now;

        int spikeThreshold = plugin.getConfigManager().getInt("anticheat.compensation.lag-spike.spike-threshold-ms", 250);
        currentlyInLagSpike = plugin.getConfigManager().getBoolean("anticheat.compensation.lag-spike.enabled", true)
                && delta > spikeThreshold;

        // Simple rolling TPS estimate from tick deltas (50ms = perfect 20 TPS).
        double instantTps = Math.min(20.0, 1000.0 / Math.max(delta, 1));
        lastKnownTps = (lastKnownTps * 0.9) + (instantTps * 0.1);
    }

    public double getTps() {
        return lastKnownTps;
    }

    public boolean isLagSpike() {
        return currentlyInLagSpike;
    }

    /** Extra timing window (ms) that should be tolerated given a player's ping, for timing-sensitive checks. */
    public long pingCompensationMs(Player player) {
        if (!plugin.getConfigManager().getBoolean("anticheat.compensation.ping.enabled", true)) return 0;
        int ping = getPing(player);
        int grace = plugin.getConfigManager().getInt("anticheat.compensation.ping.grace-ms", 100);
        if (ping <= grace) return 0;
        double scalePer100 = plugin.getConfigManager().getDouble("anticheat.compensation.ping.scale-ms-per-100", 50);
        long cap = plugin.getConfigManager().getInt("anticheat.compensation.ping.max-compensation-ms", 400);
        long comp = (long) (((ping - grace) / 100.0) * scalePer100);
        return Math.min(comp, cap);
    }

    /** Multiplier (>=1.0) to apply to violation thresholds when TPS is degraded. Higher = more lenient. */
    public double tpsLeniencyMultiplier() {
        if (!plugin.getConfigManager().getBoolean("anticheat.compensation.tps.enabled", true)) return 1.0;
        double graceTps = plugin.getConfigManager().getDouble("anticheat.compensation.tps.grace-tps", 19.5);
        if (lastKnownTps >= graceTps) return 1.0;
        return plugin.getConfigManager().getDouble("anticheat.compensation.tps.leniency-multiplier", 1.75);
    }

    public int getPing(Player player) {
        try {
            return player.getPing();
        } catch (Throwable t) {
            return 0; // older API fallback
        }
    }
}
