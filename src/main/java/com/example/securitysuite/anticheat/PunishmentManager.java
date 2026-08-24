package com.example.securitysuite.anticheat;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Date;

/**
 * Decides whether accumulated AntiCheat activity should trigger a real
 * punishment. Deliberately requires BOTH a violation-level threshold AND
 * contributions from multiple distinct checks within a rolling time
 * window (anticheat.punishments.min-distinct-checks /
 * time-window-seconds) before anything above ALERT/WARN fires - this is
 * the guard against a single noisy check or one lag spike banning someone,
 * as required by the spec.
 */
public class PunishmentManager {

    private final SecurityPlugin plugin;

    private final Map<UUID, Deque<ContributionEvent>> recentContributions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPunishmentAt = new ConcurrentHashMap<>();
    private final Set<UUID> testModePlayers = ConcurrentHashMap.newKeySet();

    private record ContributionEvent(String checkId, long timestamp) {}

    public PunishmentManager(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    public void setTestMode(UUID uuid, boolean enabled) {
        if (enabled) testModePlayers.add(uuid); else testModePlayers.remove(uuid);
    }

    public boolean isTestMode(UUID uuid) {
        return testModePlayers.contains(uuid);
    }

    public void evaluate(Player player, Check check, PlayerData data, CheckResult result) {
        if (!plugin.getConfigManager().getBoolean("anticheat.punishments.enabled", true)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        int windowSeconds = plugin.getConfigManager().getInt("anticheat.punishments.time-window-seconds", 30);

        Deque<ContributionEvent> deque = recentContributions.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        deque.addLast(new ContributionEvent(check.id(), now));
        while (!deque.isEmpty() && now - deque.peekFirst().timestamp() > windowSeconds * 1000L) {
            deque.pollFirst();
        }

        int minDistinctChecks = plugin.getConfigManager().getInt("anticheat.punishments.min-distinct-checks", 2);
        Set<String> distinctChecks = new HashSet<>();
        for (ContributionEvent e : deque) distinctChecks.add(e.checkId());

        double totalVl = data.allViolations().values().stream().mapToDouble(Double::doubleValue).sum();

        String action = resolveThresholdAction(totalVl);
        if (action == null) return;

        boolean isSevere = action.equals("KICK") || action.equals("TEMPBAN") || action.equals("BAN");
        if (isSevere && distinctChecks.size() < minDistinctChecks) {
            return;
        }

        long lastPunish = lastPunishmentAt.getOrDefault(uuid, 0L);
        if (now - lastPunish < 5000) return;

        applyPunishment(player, action, distinctChecks);
        lastPunishmentAt.put(uuid, now);
    }

    private String resolveThresholdAction(double totalVl) {
        var section = plugin.getConfigManager().raw().getConfigurationSection("anticheat.punishments.thresholds");
        if (section == null) return null;
        String best = null;
        int bestThreshold = -1;
        for (String key : section.getKeys(false)) {
            try {
                int threshold = Integer.parseInt(key);
                if (totalVl >= threshold && threshold > bestThreshold) {
                    bestThreshold = threshold;
                    best = section.getString(key + ".action");
                }
            } catch (NumberFormatException ignored) {}
        }
        return best;
    }

    private void applyPunishment(Player player, String action, Set<String> checks) {
        UUID uuid = player.getUniqueId();
        String checksJoined = String.join(", ", checks);

        if (isTestMode(uuid)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("action", action);
            placeholders.put("player", player.getName());
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("security.alerts"))
                    .forEach(p -> p.sendMessage(plugin.getMessageManager().getRaw("anticheat.test-mode-notice", placeholders)));
            return;
        }

        switch (action) {
            case "ALERT" -> { /* alert already sent by ViolationManager */ }
            case "WARN" -> player.sendMessage(com.example.securitysuite.config.MessageManager.color(
                    "&e[AntiCheat] &7You have been flagged for suspicious activity across: " + checksJoined));
            case "KICK" -> Bukkit.getScheduler().runTask(plugin, () ->
                    player.kickPlayer(com.example.securitysuite.config.MessageManager.color(
                            "&cKicked by AntiCheat (" + checksJoined + ")")));
            case "TEMPBAN" -> {
                int minutes = plugin.getConfigManager().getInt("anticheat.punishments.tempban-duration-minutes", 60);
                Date expiry = new Date(System.currentTimeMillis() + minutes * 60_000L);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(),
                            "AntiCheat temp-ban (" + checksJoined + ")", expiry, "SecuritySuite");
                    player.kickPlayer(com.example.securitysuite.config.MessageManager.color(
                            "&cTemporarily banned by AntiCheat until " + expiry));
                });
            }
            case "BAN" -> Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(),
                        "AntiCheat ban (" + checksJoined + ")", null, "SecuritySuite");
                player.kickPlayer(com.example.securitysuite.config.MessageManager.color(
                        "&cBanned by AntiCheat (" + checksJoined + ")"));
            });
            default -> plugin.getLogger().warning("Unknown AntiCheat punishment action in config: " + action);
        }

        Map<String, String> broadcastPlaceholders = new HashMap<>();
        broadcastPlaceholders.put("player", player.getName());
        broadcastPlaceholders.put("action", action);
        broadcastPlaceholders.put("checks", checksJoined);
        broadcastPlaceholders.put("color", "\u00A7c");
        String broadcast = plugin.getMessageManager().getRaw("anticheat.punishment-broadcast", broadcastPlaceholders);
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("security.alerts"))
                .forEach(p -> p.sendMessage(broadcast));

        plugin.getDatabaseManager().recordPunishment(uuid, "anticheat", action, checksJoined);
    }

    public void cleanup(UUID uuid) {
        recentContributions.remove(uuid);
        lastPunishmentAt.remove(uuid);
        testModePlayers.remove(uuid);
    }
}
