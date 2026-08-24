package com.example.securitysuite.discord;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import com.example.securitysuite.model.RiskAssessment;
import com.example.securitysuite.util.AsyncHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;

/**
 * Sends Discord webhook embeds for AntiVPN detections and AntiCheat
 * violations. The webhook URL is resolved via ConfigManager#getSecret so
 * secrets.yml can hold it instead of config.yml; it is never logged
 * (see the deliberate absence of any getLogger().info(url) call anywhere
 * in this class).
 */
public class DiscordManager {

    private final SecurityPlugin plugin;

    public DiscordManager(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    private String webhookUrl() {
        return plugin.getConfigManager().getSecret("discord.webhook-url", "discord.webhook-url");
    }

    public void sendAntiVpnEvent(Player player, RiskAssessment assessment, String action) {
        if (!plugin.getConfigManager().getBoolean("discord.enabled", false)) return;
        String url = webhookUrl();
        if (url == null || url.isBlank()) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "AntiVPN Detection");
        embed.addProperty("color", colorFor(assessment.getRating().name()));

        JsonArray fields = new JsonArray();
        addField(fields, "Player", player.getName(), true);
        addField(fields, "Country", nullSafe(assessment.getLookup().getCountry()), true);
        addField(fields, "ISP", nullSafe(assessment.getLookup().getIsp()), true);
        addField(fields, "VPN", String.valueOf(assessment.getLookup().isVpn()).toUpperCase(), true);
        addField(fields, "Proxy", String.valueOf(assessment.getLookup().isProxy()).toUpperCase(), true);
        addField(fields, "Hosting", String.valueOf(assessment.getLookup().isHosting()).toUpperCase(), true);
        addField(fields, "Risk", assessment.getScore() + " (" + assessment.getRating() + ")", true);
        addField(fields, "Action", action, true);
        embed.add("fields", fields);

        send(url, embed);
    }

    public void sendAntiCheatEvent(Player player, Check check, PlayerData data, CheckResult result) {
        if (!plugin.getConfigManager().getBoolean("discord.enabled", false)) return;
        String url = webhookUrl();
        if (url == null || url.isBlank()) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "AntiCheat Violation");
        embed.addProperty("color", 0xE67E22);

        JsonArray fields = new JsonArray();
        addField(fields, "Player", player.getName(), true);
        addField(fields, "Check", check.id(), true);
        addField(fields, "VL", String.format("%.1f", data.getViolation(check.id())), true);
        addField(fields, "Ping", plugin.getCompensationService().getPing(player) + "ms", true);
        addField(fields, "TPS", String.format("%.1f", plugin.getCompensationService().getTps()), true);
        addField(fields, "Confidence", Math.round(result.getConfidence() * 100) + "%", true);
        embed.add("fields", fields);

        send(url, embed);
    }

    private void send(String url, JsonObject embed) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", plugin.getConfigManager().getString("discord.username", "SecuritySuite"));
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        AsyncHttp.postJsonAsync(url, payload.toString(), 5000, plugin.getAsyncExecutor())
                .exceptionally(ex -> {
                    plugin.getLogger().fine("Discord webhook delivery failed: " + ex.getMessage());
                    return null;
                });
    }

    private void addField(JsonArray fields, String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value == null || value.isBlank() ? "-" : value);
        field.addProperty("inline", inline);
        fields.add(field);
    }

    private String nullSafe(String s) {
        return s == null ? "-" : s;
    }

    private int colorFor(String rating) {
        return switch (rating) {
            case "CRITICAL" -> 0xE74C3C;
            case "HIGH" -> 0xE67E22;
            case "MEDIUM" -> 0xF1C40F;
            default -> 0x2ECC71;
        };
    }
}
