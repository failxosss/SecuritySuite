package com.example.securitysuite.anticheat;

import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;

/**
 * Base contract for every AntiCheat check. Checks are stateless *classes*
 * (one instance per check type, shared across players) - all per-player
 * state lives in {@link PlayerData}, which is passed in explicitly. This
 * keeps checks trivially thread-safe-ish and easy to unit test.
 */
public interface Check {

    /** Unique id, e.g. "ReachA", used in commands, alerts and config. */
    String id();

    /** Category used for config toggles and grouping: combat, movement, player, packet. */
    String category();

    /**
     * Runs the check's detection logic for this tick/event and returns a
     * verdict. Implementations must be fast (sub-millisecond target - see
     * performance.max-check-time-ms) and must never block.
     */
    CheckResult evaluate(Player player, PlayerData data);

    /** Whether this check is currently enabled (category flag AND per-check flag). */
    default boolean isEnabled(com.example.securitysuite.SecurityPlugin plugin) {
        var cfg = plugin.getConfigManager();
        boolean categoryEnabled = cfg.getBoolean("anticheat.checks." + category(), true);
        boolean explicitlyDisabled = cfg.getStringList("anticheat.disabled-checks").stream()
                .anyMatch(s -> s.equalsIgnoreCase(id()));
        return cfg.getBoolean("anticheat.enabled", true) && categoryEnabled && !explicitlyDisabled;
    }
}
