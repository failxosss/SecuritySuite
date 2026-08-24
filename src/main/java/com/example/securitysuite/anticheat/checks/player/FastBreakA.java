package com.example.securitysuite.anticheat.checks.player;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/**
 * Compares the observed time between "start breaking" and "block broken"
 * against a conservative estimate of the minimum vanilla break time for
 * that block/tool/enchantment/effect/gamemode combination. Because
 * perfectly replicating vanilla's break-speed formula (tool tier, hardness,
 * efficiency level, haste, mining fatigue, water/no-jump penalties, aqua
 * affinity) is involved, this check applies a deliberately conservative
 * multiplier (see MIN_TIME_SAFETY_FACTOR) so it only flags breaks that are
 * clearly, not marginally, too fast - reducing false positives at the cost
 * of catching only egregious FastBreak usage. This trade-off is intentional
 * and documented in the README.
 */
public class FastBreakA implements Check {

    private static final double MIN_TIME_SAFETY_FACTOR = 0.55; // allow down to 55% of our estimate before flagging

    @Override
    public String id() { return "FastBreakA"; }

    @Override
    public String category() { return "player"; }

    public CheckResult evaluate(Player player, PlayerData data, Material block, ItemStack tool, long breakTimeMs, boolean creativeInstantMine) {
        if (creativeInstantMine || player.getGameMode().name().equals("CREATIVE")) return CheckResult.clean();

        double estimatedMinMs = estimateMinimumBreakTimeMs(player, block, tool);
        double floor = estimatedMinMs * MIN_TIME_SAFETY_FACTOR;

        if (breakTimeMs >= floor) return CheckResult.clean();

        double ratio = floor <= 0 ? 0 : (floor - breakTimeMs) / floor;
        double confidence = Math.min(0.8, ratio);
        double vl = Math.min(5.0, ratio * 6.0);

        return CheckResult.flag(confidence, vl,
                String.format("broke %s in %dms (expected floor ~%.0fms)", block, breakTimeMs, floor));
    }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        return CheckResult.clean(); // see the dedicated overload; this check needs block-break event context
    }

    /**
     * Deliberately approximate: uses Bukkit's exposed hardness where
     * available and a coarse tool-tier multiplier table rather than
     * reimplementing Minecraft's full internal break-speed formula (which
     * also depends on internal digging-speed tables not exposed by the
     * Paper API). This is a documented simplification, not a silent one.
     */
    private double estimateMinimumBreakTimeMs(Player player, Material block, ItemStack tool) {
        double hardness = safeHardness(block);
        if (hardness <= 0) return 50; // unbreakable-ish/instant blocks

        double toolMultiplier = toolSpeedMultiplier(tool, block);
        double hasteMultiplier = player.hasPotionEffect(PotionEffectType.FAST_DIGGING) ? 1.3 : 1.0;
        double fatigueMultiplier = player.hasPotionEffect(PotionEffectType.SLOW_DIGGING) ? 0.3 : 1.0;

        double effectiveSpeed = toolMultiplier * hasteMultiplier * fatigueMultiplier;
        double seconds = (hardness * 1.5) / Math.max(0.05, effectiveSpeed);
        return seconds * 1000.0;
    }

    private double safeHardness(Material block) {
        try {
            return block.getHardness();
        } catch (Throwable t) {
            return 1.0;
        }
    }

    private double toolSpeedMultiplier(ItemStack tool, Material block) {
        if (tool == null) return 1.0;
        String name = tool.getType().name();
        double base = 1.0;
        if (name.contains("WOOD")) base = 2.0;
        else if (name.contains("STONE")) base = 4.0;
        else if (name.contains("IRON")) base = 6.0;
        else if (name.contains("DIAMOND")) base = 8.0;
        else if (name.contains("NETHERITE")) base = 9.0;
        else if (name.contains("GOLD")) base = 12.0;

        int efficiency = tool.getEnchantments().entrySet().stream()
                .filter(e -> e.getKey().getKey().getKey().equalsIgnoreCase("efficiency"))
                .map(Map.Entry::getValue).findFirst().orElse(0);
        if (efficiency > 0) {
            base += (efficiency * efficiency) + 1;
        }
        return base;
    }
}
