package com.example.securitysuite.anticheat.checks.combat;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.CompensationService;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Flags melee hits landed from beyond the legitimate reach envelope.
 * Vanilla survival reach is ~3.0 blocks; creative is ~5.0. We use the
 * larger, more lenient bound plus ping-based extra tolerance (a hit that
 * was registered client-side up to `ping compensation` ms ago may have
 * been thrown from slightly further away than it now measures server-side).
 *
 * This check is invoked from CombatListener#onEntityDamageByEntity with
 * the distance already computed, via {@link #evaluate(Player, PlayerData, double)}.
 * The no-arg evaluate() required by the Check interface is unused here.
 */
public class ReachA implements Check {

    private static final double SURVIVAL_REACH = 3.0;
    private static final double CREATIVE_REACH = 5.0;
    private static final double PING_TOLERANCE_PER_100MS = 0.15; // blocks of extra tolerance
    private static final double MAX_PING_TOLERANCE = 0.6;

    private final SecurityPlugin plugin;
    private final CompensationService compensation;

    public ReachA(SecurityPlugin plugin, CompensationService compensation) {
        this.plugin = plugin;
        this.compensation = compensation;
    }

    @Override
    public String id() { return "ReachA"; }

    @Override
    public String category() { return "combat"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        return CheckResult.clean(); // see evaluate(player, data, distance)
    }

    public CheckResult evaluate(Player attacker, PlayerData data, double distance, LivingEntity target) {
        double baseReach = attacker.getGameMode().name().equals("CREATIVE") ? CREATIVE_REACH : SURVIVAL_REACH;

        int ping = compensation.getPing(attacker);
        double pingTolerance = Math.min(MAX_PING_TOLERANCE, (ping / 100.0) * PING_TOLERANCE_PER_100MS);

        double allowed = baseReach + pingTolerance;

        if (distance <= allowed) {
            return CheckResult.clean();
        }

        double overshoot = distance - allowed;
        // Confidence scales with how far past the allowed reach the hit was;
        // small overshoots (packet jitter) get low confidence and low VL.
        double confidence = Math.min(1.0, overshoot / 1.5);
        double vl = Math.min(10.0, overshoot * 4.0) * compensationDampening();

        String reason = String.format("hit at %.2f blocks (allowed %.2f, ping %dms)", distance, allowed, ping);
        return CheckResult.flag(confidence, vl, reason);
    }

    private double compensationDampening() {
        // under TPS pressure, reduce the VL contribution rather than skip entirely
        return 1.0 / compensation.tpsLeniencyMultiplier();
    }
}
