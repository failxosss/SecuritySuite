package com.example.securitysuite.anticheat;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Reacts to individual check violations: sends staff alerts (respecting
 * per-staff verbose/alerts toggles), forwards to Discord if configured,
 * and hands off to PunishmentManager to decide whether accumulated VL
 * across checks now warrants an actual punishment.
 */
public class ViolationManager {

    private final SecurityPlugin plugin;

    public ViolationManager(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    public void onViolation(Player player, Check check, PlayerData data, CheckResult result) {
        if (plugin.getConfigManager().getBoolean("anticheat.alerts.enabled", true)) {
            broadcastAlert(player, check, data, result);
        }

        if (plugin.getConfigManager().getBoolean("discord.enabled", false)
                && plugin.getConfigManager().getBoolean("discord.send-anticheat-events", true)
                && data.getViolation(check.id()) >= plugin.getConfigManager().getInt("discord.anticheat-min-vl", 15)) {
            plugin.getDiscordManager().sendAntiCheatEvent(player, check, data, result);
        }

        plugin.getPunishmentManager().evaluate(player, check, data, result);
    }

    private void broadcastAlert(Player player, Check check, PlayerData data, CheckResult result) {
        double vl = data.getViolation(check.id());
        String rating = ratingFor(vl);
        String color = plugin.getConfigManager().getString("anticheat.alerts.colors." + rating.toLowerCase(), "&7");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("check", check.id());
        placeholders.put("vl", String.format("%.1f", vl));
        placeholders.put("ping", String.valueOf(plugin.getCompensationService().getPing(player)));
        placeholders.put("tps", String.format("%.1f", plugin.getCompensationService().getTps()));
        placeholders.put("confidence", String.valueOf(Math.round(result.getConfidence() * 100)));
        placeholders.put("color", com.example.securitysuite.config.MessageManager.color(color));

        String message = plugin.getMessageManager().getRaw("anticheat.alert", placeholders);

        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("security.alerts"))
                .forEach(p -> p.sendMessage(message));
        plugin.getLogger().info(stripColor(message));
    }

    private String ratingFor(double vl) {
        if (vl >= 60) return "CRITICAL";
        if (vl >= 40) return "HIGH";
        if (vl >= 20) return "MEDIUM";
        return "LOW";
    }

    private String stripColor(String s) {
        return s.replaceAll("\u00A7[0-9a-fk-or]", "");
    }
}
