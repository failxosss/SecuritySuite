package com.example.securitysuite.antivpn;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.model.RiskAssessment;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates a RiskAssessment into the configured actions (antivpn.actions)
 * and applies them. Multiple signals can each contribute an action; they
 * are de-duplicated and the most severe one wins for the actual
 * kick/ban outcome, while NOTIFY-only signals always additionally notify
 * staff regardless of what the "winning" action is.
 */
public class AntiVpnPunishmentDispatcher {

    private final SecurityPlugin plugin;

    private static final List<String> SEVERITY_ORDER = List.of("LOG", "NOTIFY", "COMMAND", "KICK", "TEMPBAN", "BAN");

    public AntiVpnPunishmentDispatcher(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Used from AsyncPlayerPreLoginEvent, where the connection is already
     * being terminated and no live Player object exists yet. Only handles
     * staff notification + DB recording; no in-game action is attempted
     * here (the caller already used event.disallow(...) for that).
     */
    public void notifyAndRecordPreLoginKick(String playerName, java.util.UUID uuid, RiskAssessment assessment, String action) {
        var lookup = assessment.getLookup();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", playerName);
        placeholders.put("risk", String.valueOf(assessment.getScore()));
        placeholders.put("rating", assessment.getRating().name());
        placeholders.put("reasons", String.join(", ", assessment.getReasons()));
        String message = plugin.getMessageManager().get("antivpn.staff-notify", placeholders);

        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("security.alerts"))
                        .forEach(p -> p.sendMessage(message)));
        plugin.getLogger().info(message.replaceAll("\u00A7[0-9a-fk-or]", ""));

        plugin.getDatabaseManager().recordDetection(uuid, lookup.getIp(),
                lookup.isVpn(), lookup.isProxy(), lookup.isHosting(), lookup.isTor(),
                assessment.getScore(), assessment.getRating().name(), action);
    }

    public void dispatch(Player player, RiskAssessment assessment) {
        if (assessment == null) return;

        var cfg = plugin.getConfigManager();
        List<String> triggeredActions = new ArrayList<>();

        if (assessment.getLookup().isVpn()) triggeredActions.add(cfg.getString("antivpn.actions.vpn", "KICK"));
        if (assessment.getLookup().isProxy()) triggeredActions.add(cfg.getString("antivpn.actions.proxy", "KICK"));
        if (assessment.getLookup().isTor()) triggeredActions.add(cfg.getString("antivpn.actions.tor", "BAN"));
        if (assessment.getLookup().isHosting()) triggeredActions.add(cfg.getString("antivpn.actions.datacenter", "NOTIFY"));

        if (triggeredActions.isEmpty()) {
            notifyStaff(player, assessment, "LOG");
            recordDetection(player, assessment, "LOG");
            return;
        }

        boolean anyNotify = triggeredActions.stream().anyMatch(a -> a.contains("NOTIFY"));
        String severest = mostSevere(triggeredActions);

        if (anyNotify || severest.equals("LOG")) {
            notifyStaff(player, assessment, severest);
        }

        applyAction(player, severest, assessment);
        recordDetection(player, assessment, severest);
    }

    private String mostSevere(List<String> actions) {
        String best = "LOG";
        int bestRank = 0;
        for (String combo : actions) {
            for (String part : combo.split(",")) {
                int rank = SEVERITY_ORDER.indexOf(part.trim().toUpperCase());
                if (rank > bestRank) {
                    bestRank = rank;
                    best = part.trim().toUpperCase();
                }
            }
        }
        return best;
    }

    private void applyAction(Player player, String action, RiskAssessment assessment) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("risk", String.valueOf(assessment.getScore()));
        placeholders.put("reasons", String.join(", ", assessment.getReasons()));

        switch (action) {
            case "LOG", "NOTIFY" -> { /* handled by notifyStaff */ }
            case "KICK" -> Bukkit.getScheduler().runTask(plugin, () -> {
                String msg = assessment.getLookup().isTor() ? plugin.getMessageManager().get("antivpn.kick-tor")
                        : assessment.getLookup().isVpn() ? plugin.getMessageManager().get("antivpn.kick-vpn")
                        : assessment.getLookup().isProxy() ? plugin.getMessageManager().get("antivpn.kick-proxy")
                        : assessment.getLookup().isHosting() ? plugin.getMessageManager().get("antivpn.kick-hosting")
                        : plugin.getMessageManager().get("antivpn.kick-generic", placeholders);
                player.kickPlayer(msg);
            });
            case "TEMPBAN" -> {
                int minutes = plugin.getConfigManager().getInt("antivpn.tempban-duration-minutes", 1440);
                Date expiry = new Date(System.currentTimeMillis() + minutes * 60_000L);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(),
                            "AntiVPN temp-ban (risk " + assessment.getScore() + ")", expiry, "SecuritySuite");
                    placeholders.put("expiry", expiry.toString());
                    player.kickPlayer(plugin.getMessageManager().get("antivpn.tempban-message", placeholders));
                });
            }
            case "BAN" -> Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(),
                        "AntiVPN ban (risk " + assessment.getScore() + ")", null, "SecuritySuite");
                player.kickPlayer(plugin.getMessageManager().get("antivpn.ban-message", placeholders));
            });
            case "COMMAND" -> {
                String template = plugin.getConfigManager().getString("antivpn.action-command", "");
                if (!template.isBlank()) {
                    String cmd = template.replace("%player%", player.getName()).replace("%uuid%", player.getUniqueId().toString());
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
                }
            }
            default -> plugin.getLogger().warning("Unknown AntiVPN action in config: " + action);
        }
    }

    private void notifyStaff(Player player, RiskAssessment assessment, String action) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("risk", String.valueOf(assessment.getScore()));
        placeholders.put("rating", assessment.getRating().name());
        placeholders.put("reasons", String.join(", ", assessment.getReasons()));

        String message = plugin.getMessageManager().get("antivpn.staff-notify", placeholders);
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("security.alerts"))
                .forEach(p -> p.sendMessage(message));
        plugin.getLogger().info(message.replaceAll("\u00A7[0-9a-fk-or]", ""));

        if (plugin.getConfigManager().getBoolean("discord.enabled", false)
                && plugin.getConfigManager().getBoolean("discord.send-antivpn-events", true)) {
            plugin.getDiscordManager().sendAntiVpnEvent(player, assessment, action);
        }
    }

    private void recordDetection(Player player, RiskAssessment assessment, String action) {
        var lookup = assessment.getLookup();
        plugin.getDatabaseManager().recordDetection(player.getUniqueId(), lookup.getIp(),
                lookup.isVpn(), lookup.isProxy(), lookup.isHosting(), lookup.isTor(),
                assessment.getScore(), assessment.getRating().name(), action);
    }
}
