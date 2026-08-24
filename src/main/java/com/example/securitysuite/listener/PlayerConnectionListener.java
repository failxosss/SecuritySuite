package com.example.securitysuite.listener;

import com.example.securitysuite.SecurityPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Runs AntiVPN checks at pre-login time (already async by nature of
 * AsyncPlayerPreLoginEvent) so a flagged player can be disallowed before
 * they ever spawn into the world, and cleans up per-player AntiCheat
 * state on quit so PlayerDataManager doesn't leak.
 */
public class PlayerConnectionListener implements Listener {

    private final SecurityPlugin plugin;

    public PlayerConnectionListener(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!plugin.getConfigManager().getBoolean("antivpn.enabled", true)) return;
        if (!plugin.getConfigManager().getBoolean("antivpn.check-on-join", true)) return;

        String ip = event.getAddress().getHostAddress();
        long start = System.currentTimeMillis();

        try {
            var assessment = plugin.getAntiVPNManager()
                    .assess(event.getUniqueId(), event.getName(), ip)
                    .get(4, java.util.concurrent.TimeUnit.SECONDS); // pre-login already blocks the connection thread, not the main thread
            plugin.getPerformanceStats().recordApiLookup(System.currentTimeMillis() - start);

            if (assessment == null) return; // whitelisted or disabled

            String severest = resolveSeverestForPreLogin(assessment);
            if (severest.equals("KICK") || severest.equals("BAN") || severest.equals("TEMPBAN")) {
                String reasonMsg = assessment.getLookup().isTor() ? plugin.getMessageManager().get("antivpn.kick-tor")
                        : assessment.getLookup().isVpn() ? plugin.getMessageManager().get("antivpn.kick-vpn")
                        : assessment.getLookup().isProxy() ? plugin.getMessageManager().get("antivpn.kick-proxy")
                        : plugin.getMessageManager().get("antivpn.kick-hosting");
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, reasonMsg);
                // Player will never fire PlayerJoinEvent, so handle notify/record here directly.
                plugin.getAntiVpnPunishmentDispatcher().notifyAndRecordPreLoginKick(event.getName(), event.getUniqueId(), assessment, severest);
                return;
            }

            // Non-terminal actions (LOG/NOTIFY/COMMAND) are deferred to PlayerJoinEvent,
            // where a live Player object exists for permission-aware dispatch.
            pendingAssessments.put(event.getUniqueId(), assessment);
        } catch (Exception e) {
            plugin.getLogger().fine("AntiVPN pre-login check failed/timed out for " + event.getName() + ": " + e.getMessage());
            // fail-open: never block login due to an infrastructure failure
        }
    }

    private final java.util.Map<java.util.UUID, com.example.securitysuite.model.RiskAssessment> pendingAssessments = new java.util.concurrent.ConcurrentHashMap<>();

    private String resolveSeverestForPreLogin(com.example.securitysuite.model.RiskAssessment assessment) {
        var cfg = plugin.getConfigManager();
        java.util.List<String> actions = new java.util.ArrayList<>();
        if (assessment.getLookup().isVpn()) actions.add(cfg.getString("antivpn.actions.vpn", "KICK"));
        if (assessment.getLookup().isProxy()) actions.add(cfg.getString("antivpn.actions.proxy", "KICK"));
        if (assessment.getLookup().isTor()) actions.add(cfg.getString("antivpn.actions.tor", "BAN"));
        if (assessment.getLookup().isHosting()) actions.add(cfg.getString("antivpn.actions.datacenter", "NOTIFY"));
        java.util.List<String> order = java.util.List.of("LOG", "NOTIFY", "COMMAND", "KICK", "TEMPBAN", "BAN");
        String best = "LOG";
        int bestRank = 0;
        for (String combo : actions) {
            for (String part : combo.split(",")) {
                int rank = order.indexOf(part.trim().toUpperCase());
                if (rank > bestRank) { bestRank = rank; best = part.trim().toUpperCase(); }
            }
        }
        return best;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        plugin.getDatabaseManager().upsertPlayer(player.getUniqueId(), player.getName(), player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null);

        var assessment = pendingAssessments.remove(player.getUniqueId());
        if (assessment != null) {
            // player wasn't already disallowed (or the action was NOTIFY/LOG/COMMAND) - dispatch remaining side effects
            plugin.getAntiVpnPunishmentDispatcher().dispatch(player, assessment);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        plugin.getPlayerDataManager().remove(uuid);
        plugin.getPunishmentManager().cleanup(uuid);
        plugin.getCheckManager().fastPlaceA.cleanup(uuid);
        plugin.getCheckManager().inventoryMoveA.cleanup(uuid);
        plugin.getCheckManager().badPacketA.cleanup(uuid);
        plugin.getCheckManager().regenA.cleanup(uuid);
        pendingAssessments.remove(uuid);
    }
}
