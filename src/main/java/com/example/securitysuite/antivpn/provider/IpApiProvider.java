package com.example.securitysuite.antivpn.provider;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.model.IpLookupResult;
import com.example.securitysuite.util.AsyncHttp;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * ip-api.com based provider. Free tier gives proxy/hosting flags plus
 * geolocation/ASN data, no API key required for the HTTP endpoint (rate
 * limited to 45 req/min per source IP). It does NOT provide a dedicated
 * Tor flag - see the note in {@link #lookup(String)}.
 */
public class IpApiProvider implements IpProvider {

    private final SecurityPlugin plugin;
    private final Executor executor;

    public IpApiProvider(SecurityPlugin plugin, Executor executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    @Override
    public String id() {
        return "ip-api";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfigManager().getBoolean("antivpn.providers.ip-api.enabled", true);
    }

    @Override
    public CompletableFuture<IpLookupResult> lookup(String ip) {
        String baseUrl = plugin.getConfigManager().getString("antivpn.providers.ip-api.base-url",
                "http://ip-api.com/json/%IP%?fields=status,message,country,countryCode,isp,org,as,proxy,hosting,query");
        long timeout = plugin.getConfigManager().getInt("antivpn.providers.ip-api.timeout-ms", 3000);
        String url = baseUrl.replace("%IP%", ip);

        return AsyncHttp.getAsync(url, timeout, executor)
                .thenApplyAsync(body -> parse(ip, body), executor)
                .exceptionally(ex -> {
                    plugin.getLogger().fine("ip-api lookup failed for " + maskIp(ip) + ": " + ex.getMessage());
                    return IpLookupResult.failed(ip, id());
                });
    }

    private IpLookupResult parse(String ip, String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("status") && !"success".equalsIgnoreCase(json.get("status").getAsString())) {
                return IpLookupResult.failed(ip, id());
            }
            IpLookupResult result = IpLookupResult.success(ip, id());
            result.setProxy(getBool(json, "proxy"));
            // ip-api's "hosting" flag also strongly correlates with generic VPN
            // exit nodes since most commercial VPN providers rent datacenter IPs.
            // We surface both signals; the risk scorer weighs them independently.
            result.setHosting(getBool(json, "hosting"));
            result.setVpn(getBool(json, "proxy") && getBool(json, "hosting"));
            result.setCountry(getString(json, "countryCode"));
            result.setIsp(getString(json, "isp"));
            result.setOrganization(getString(json, "org"));
            result.setAsn(getString(json, "as"));
            // NOTE: ip-api's free endpoint has no dedicated Tor exit-node flag.
            // We deliberately leave `tor` false here rather than guessing;
            // a real Tor check needs a maintained exit-node list (see
            // AntiVPNManager's fallback static-list note).
            return result;
        } catch (Exception e) {
            return IpLookupResult.failed(ip, id());
        }
    }

    private boolean getBool(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
    }

    private String getString(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private String maskIp(String ip) {
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) + ".x" : "***";
    }
}
