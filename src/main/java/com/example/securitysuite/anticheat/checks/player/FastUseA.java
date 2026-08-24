package com.example.securitysuite.anticheat.checks.player;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Vanilla eating/drinking takes ~32 ticks from the item being raised to
 * being consumed. "InstaEat"-style hacks skip or shorten that animation
 * client-side while still sending the consume packet. We only judge this
 * from an observed hand-raise start (see PlayerData#getHandRaiseStartTick,
 * updated every move tick) - if we never saw the raise begin (e.g. it
 * started before this player's data was warmed up), we deliberately don't
 * guess, to avoid a false positive on a missed edge rather than a real
 * fast-use.
 */
public class FastUseA implements Check {

    private static final long MIN_USE_TICKS = 28; // vanilla ~32 ticks, with a safety margin for tick-boundary rounding

    private final SecurityPlugin plugin;

    public FastUseA(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() { return "FastUseA"; }

    @Override
    public String category() { return "player"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        return CheckResult.clean(); // see the dedicated overload; this check needs the consumed item context
    }

    public CheckResult evaluate(Player player, PlayerData data, Material consumedItem) {
        long start = data.getHandRaiseStartTick();
        if (start < 0) return CheckResult.clean();

        long elapsed = plugin.getCurrentTick() - start;
        if (elapsed >= MIN_USE_TICKS) return CheckResult.clean();

        double ratio = (MIN_USE_TICKS - elapsed) / (double) MIN_USE_TICKS;
        double confidence = Math.min(0.75, ratio);
        double vl = Math.min(5.0, ratio * 6.0);

        return CheckResult.flag(confidence, vl,
                "consumed " + consumedItem + " in " + elapsed + " ticks (vanilla minimum ~" + MIN_USE_TICKS + ")");
    }
}
