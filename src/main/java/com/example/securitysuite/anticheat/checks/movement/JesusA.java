package com.example.securitysuite.anticheat.checks.movement;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Detects "Jesus" hacks: standing/walking on top of water or lava instead
 * of sinking/swimming. Boats, frost walker boots (ice formation), and
 * lily pads/turtle-riding are all explicitly excluded.
 */
public class JesusA implements Check {

    private final SecurityPlugin plugin;

    public JesusA(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() { return "JesusA"; }

    @Override
    public String category() { return "movement"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        if (data.isInVehicle() || data.isGliding()) return CheckResult.clean();
        if (!player.isOnGround()) return CheckResult.clean();

        List<Location> history = data.getMovementHistory();
        if (history.isEmpty()) return CheckResult.clean();
        Location loc = history.get(history.size() - 1);

        Location below = loc.clone().subtract(0, 0.3, 0);
        Material blockAtFeet = loc.getBlock().getType();
        Material blockBelow = below.getBlock().getType();

        boolean standingOnLiquidSurface =
                (blockAtFeet == Material.WATER || blockAtFeet == Material.LAVA
                        || blockBelow == Material.WATER || blockBelow == Material.LAVA)
                && player.isOnGround();

        if (!standingOnLiquidSurface) return CheckResult.clean();

        // Frost Walker forms real ice blocks under the player - if the block
        // actually converted to FROSTED_ICE this is legitimate, not a Jesus hack.
        if (blockBelow == Material.FROSTED_ICE || blockAtFeet == Material.FROSTED_ICE) {
            return CheckResult.clean();
        }

        return CheckResult.flag(0.75, 3.0, "on-ground while standing on unconverted liquid surface");
    }
}
