package com.example.securitysuite.anticheat.checks.packet;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;

/**
 * A normal client's input handling is effectively single-threaded per
 * game action: breaking a block, placing a block, and attacking an entity
 * all funnel through the same input queue. Seeing all three land in the
 * same server tick is not achievable through ordinary client input and is
 * a strong signal of a packet-spamming utility (killaura/scaffold/nuker
 * combos firing raw packets outside the normal client loop) rather than
 * of fast legitimate play.
 */
public class MultiActionA implements Check {

    @Override
    public String id() { return "MultiActionA"; }

    @Override
    public String category() { return "packet"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        return CheckResult.clean(); // see the dedicated overload; this check needs the current tick clock
    }

    public CheckResult evaluate(Player player, PlayerData data, long currentTick) {
        int actionsThisTick = 0;
        if (data.getLastBlockBreakTick() == currentTick) actionsThisTick++;
        if (data.getLastBlockPlaceTick() == currentTick) actionsThisTick++;
        if (data.getLastAttackTick() == currentTick) actionsThisTick++;

        if (actionsThisTick < 3) return CheckResult.clean();

        return CheckResult.flag(0.7, 3.0,
                "broke a block, placed a block, and attacked in the same server tick - "
                        + "not reachable through normal single-input-queue client behavior");
    }
}
