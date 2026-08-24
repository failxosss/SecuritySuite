package com.example.securitysuite.anticheat.checks.player;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * "Scaffold" cheats place blocks beneath the player automatically while
 * walking backwards, typically producing two tells vanilla play rarely
 * does together: (1) the placed block is directly beneath/behind the
 * player's feet in the exact direction of travel, placed with inhuman
 * consistency, and (2) the player's look/pitch snaps sharply downward
 * right at the moment of placement then back up, because the cheat forces
 * a rotation toward the placement target. We only check signal (2) here
 * (rotation snap correlated with a placement beneath the player), since
 * signal (1) alone is legitimate manual bridging.
 */
public class ScaffoldA implements Check {

    @Override
    public String id() { return "ScaffoldA"; }

    @Override
    public String category() { return "player"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        return CheckResult.clean(); // see overload
    }

    public CheckResult evaluate(Player player, PlayerData data, Location placedAgainstFeetLocation, float pitchAtPlacement) {
        var rotations = data.getRotationHistory();
        if (rotations.size() < 3) return CheckResult.clean();

        float previousPitch = rotations.get(rotations.size() - 2)[1];
        double pitchDelta = Math.abs(pitchAtPlacement - previousPitch);

        Location feet = player.getLocation();
        double horizontalDistanceToPlacement = new Vector(feet.getX(), 0, feet.getZ())
                .distance(new Vector(placedAgainstFeetLocation.getX(), 0, placedAgainstFeetLocation.getZ()));

        boolean placedRightUnderFeet = horizontalDistanceToPlacement < 1.2;
        boolean sharpDownwardSnap = pitchAtPlacement > 60 && pitchDelta > 40;

        if (placedRightUnderFeet && sharpDownwardSnap) {
            return CheckResult.flag(0.55, 1.5,
                    String.format("block placed under feet with %.1f\u00b0 pitch snap to look down", pitchDelta));
        }

        return CheckResult.clean();
    }
}
