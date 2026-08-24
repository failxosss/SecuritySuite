package com.example.securitysuite.anticheat.checks.combat;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;

/**
 * A vanilla critical hit requires the attacker to be falling (negative Y
 * velocity), not on the ground, not on a ladder/vine, not in water, and
 * not blind/no-jump-boost, at the moment of the hit. "Criticals" cheats
 * force the critical hit particle/damage bonus outside those conditions.
 *
 * This check is called directly from CombatListener with the environment
 * flags already resolved (see evaluate(Player, PlayerData, boolean, boolean)),
 * since that data comes from the damage event context, not from PlayerData
 * history alone.
 */
public class CriticalsA implements Check {

    @Override
    public String id() { return "CriticalsA"; }

    @Override
    public String category() { return "combat"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        return CheckResult.clean(); // see overload below
    }

    /**
     * @param claimedCritical whether the client-reported hit included the critical flag/damage bonus
     * @param eligibleForCritical whether server-side state (falling, not on ground, not in liquid/ladder, no blindness) permits a critical
     */
    public CheckResult evaluate(Player player, PlayerData data, boolean claimedCritical, boolean eligibleForCritical) {
        if (!claimedCritical) return CheckResult.clean();
        if (eligibleForCritical) return CheckResult.clean();

        if (data.isOnLadder() || data.isInWater() || data.isInVehicle()) {
            // legitimate-mechanic exclusion zones; never flag here even if
            // the server's "eligible" computation was itself slightly stale
            return CheckResult.clean();
        }

        return CheckResult.flag(0.7, 3.0, "critical hit landed while not eligible (not falling / on ground)");
    }
}
