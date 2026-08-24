package com.example.securitysuite.anticheat.checks.movement;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Vanilla will not let the client *start* sprinting while sneaking, blind,
 * with an item raised, or (in survival) below 6 hunger - modified clients
 * that force the sprint flag regardless are a common "speed via forced
 * sprint" trick. Note: vanilla only blocks the sprint *start* on low
 * hunger, it doesn't cancel a sprint already in progress if hunger drops
 * mid-sprint - so the hunger condition here is deliberately the weakest
 * signal of the four (lowest confidence) and is intended to be read
 * alongside the others, not alone. This asymmetry is a known, accepted
 * simplification rather than a bug.
 */
public class InvalidSprintA implements Check {

    private static final int LOW_HUNGER_THRESHOLD = 6;

    @Override
    public String id() { return "InvalidSprintA"; }

    @Override
    public String category() { return "movement"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        if (!player.isSprinting()) return CheckResult.clean();
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            return CheckResult.clean();
        }

        boolean sneaking = player.isSneaking();
        boolean blind = player.hasPotionEffect(PotionEffectType.BLINDNESS);
        boolean handRaised = player.isHandRaised();
        boolean lowHunger = player.getFoodLevel() <= LOW_HUNGER_THRESHOLD;

        if (!sneaking && !blind && !handRaised && !lowHunger) return CheckResult.clean();

        double confidence = sneaking || blind || handRaised ? 0.65 : 0.3;
        double vl = sneaking || blind || handRaised ? 2.5 : 1.0;

        String reason = "sprinting in a state vanilla does not allow sprint in ("
                + (sneaking ? "sneaking " : "")
                + (blind ? "blind " : "")
                + (handRaised ? "item-in-use " : "")
                + (lowHunger ? "low-hunger" : "") + ")";

        return CheckResult.flag(confidence, vl, reason.trim());
    }
}
