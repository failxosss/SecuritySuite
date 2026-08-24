package com.example.securitysuite.antivpn;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.antivpn.provider.CustomProvider;
import com.example.securitysuite.antivpn.provider.IpApiProvider;
import com.example.securitysuite.antivpn.provider.IpProvider;
import com.example.securitysuite.antivpn.provider.IpQualityProvider;
import com.example.securitysuite.model.IpLookupResult;
import com.example.securitysuite.model.RiskAssessment;
import com.example.securitysuite.model.RiskAssessment.Rating;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Central coordinator for AntiVPN/AntiProxy detection: tries configured
 * providers in order, applies whitelist rules, computes a weighted risk
 * score, caches the result, and reports back a RiskAssessment. It does NOT
 * itself kick/ban - see PunishmentDispatcher usage in the join listener,
 * which reads antivpn.actions from config.
 */
public class AntiVPNManager {

    private final SecurityPlugin plugin;
    private final List<IpProvider> providers = new ArrayList<>();
    private final Executor executor;

    /**
     * Known Tor exit-node awareness note: reliably detecting Tor requires a
     * regularly refreshed exit-node list (e.g. the official Tor bulk exit
     * list) or a provider that supplies it (IPQualityScore does). Without
     * such a source configured, `tor` will simply stay false rather than
     * being guessed - this is the explicit fallback for that feature.
     */

    public AntiVPNManager(SecurityPlugin plugin) {
        this.plugin = plugin;
        this.executor = plugin.getAsyncExecutor();
    }

    public void load() {
        providers.clear();
        providers.add(new IpApiProvider(plugin, executor));
        providers.add(new IpQualityProvider(plugin, executor));
        providers.add(new CustomProvider(plugin, executor));
    }

    private List<IpProvider> orderedEnabledProviders() {
        List<String> order = plugin.getConfigManager().getStringList("antivpn.providers.order");
        List<IpProvider> result = new ArrayList<>();
        for (String id : order) {
            providers.stream().filter(p -> p.id().equalsIgnoreCase(id) && p.isEnabled())
                    .findFirst().ifPresent(result::add);
        }
        // include any enabled provider not explicitly ordered, as a safety net
        for (IpProvider p : providers) {
            if (p.isEnabled() && !result.contains(p)) result.add(p);
        }
        return result;
    }

    /**
     * Full async pipeline: whitelist -> cache -> providers (in order,
     * first success wins) -> scoring -> cache write. Never blocks the
     * calling thread.
     */
    public CompletableFuture<RiskAssessment> assess(UUID uuid, String playerName, String ip) {
        if (!plugin.getConfigManager().getBoolean("antivpn.enabled", true)) {
            return CompletableFuture.completedFuture(null);
        }
        if (isWhitelisted(uuid, playerName, ip)) {
            return CompletableFuture.completedFuture(null);
        }

        RiskAssessment cached = plugin.getCacheManager().get(ip);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return lookupChain(ip, orderedEnabledProviders(), 0)
                .thenApplyAsync(lookup -> {
                    RiskAssessment assessment = score(lookup);
                    plugin.getCacheManager().put(ip, assessment);
                    return assessment;
                }, executor);
    }

    private CompletableFuture<IpLookupResult> lookupChain(String ip, List<IpProvider> providers, int index) {
        if (index >= providers.size()) {
            return CompletableFuture.completedFuture(IpLookupResult.failed(ip, "none"));
        }
        return providers.get(index).lookup(ip).thenComposeAsync(result -> {
            if (result != null && !result.isLookupFailed()) {
                return CompletableFuture.completedFuture(result);
            }
            return lookupChain(ip, providers, index + 1);
        }, executor);
    }

    public boolean isWhitelisted(UUID uuid, String playerName, String ip) {
        var cfg = plugin.getConfigManager();
        List<String> ips = cfg.getStringList("antivpn.whitelist.ips");
        List<String> players = cfg.getStringList("antivpn.whitelist.players");
        List<String> uuids = cfg.getStringList("antivpn.whitelist.uuids");

        if (ip != null && ips.contains(ip)) return true;
        if (playerName != null && players.stream().anyMatch(p -> p.equalsIgnoreCase(playerName))) return true;
        if (uuid != null && uuids.contains(uuid.toString())) return true;

        org.bukkit.entity.Player online = uuid != null ? plugin.getServer().getPlayer(uuid) : null;
        if (online != null) {
            String bypassPerm = cfg.getString("antivpn.whitelist.bypass-permission", "antivpn.bypass");
            if (online.hasPermission(bypassPerm) || online.hasPermission("security.bypass")) {
                return true;
            }
        }
        return false;
    }

    private RiskAssessment score(IpLookupResult lookup) {
        var cfg = plugin.getConfigManager();

        if (lookup.isLookupFailed()) {
            plugin.getLogger().fine("All AntiVPN providers failed or are unconfigured; treating as LOW risk (fail-open).");
        }

        RiskScorer.Weights weights = new RiskScorer.Weights(
                cfg.getInt("antivpn.scoring.vpn", 40),
                cfg.getInt("antivpn.scoring.proxy", 35),
                cfg.getInt("antivpn.scoring.hosting", 25),
                cfg.getInt("antivpn.scoring.tor", 70),
                cfg.getInt("antivpn.scoring.datacenter-asn", 20),
                cfg.getInt("antivpn.scoring.known-abuse-ip", 50));

        RiskScorer.Thresholds thresholds = new RiskScorer.Thresholds(
                cfg.getInt("antivpn.thresholds.low", 30),
                cfg.getInt("antivpn.thresholds.medium", 60),
                cfg.getInt("antivpn.thresholds.high", 80));

        return RiskScorer.score(lookup, weights, thresholds);
    }
}
