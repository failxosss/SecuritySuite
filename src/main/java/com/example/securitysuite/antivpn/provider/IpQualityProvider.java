package com.example.securitysuite.antivpn.provider;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.model.IpLookupResult;
import com.example.securitysuite.util.AsyncHttp;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * IPQualityScore-based provider. Requires a paid/free-trial API key
 * (never hardcode it - it is read via ConfigManager#getSecret, which
 * prefers secrets.yml). Provides a genuine Tor flag and a fraud/abuse
 * score, so this is the recommended primary provider if you have a key.
 */
public class IpQualityProvider implements IpProvider {

    private final SecurityPlugin plugin;
    private final Executor executor;

    public IpQualityProvider(SecurityPlugin plugin, Executor executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    @Override
    public String id() {
        return "ipqualityscore";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfigManager().getBoolean("antivpn.providers.ipqualityscore.enabled", false);
    }

    @Override
    public CompletableFuture<IpLookupResult> lookup(String ip) {
        String key = plugin.getConfigManager().getSecret(
                "providers.ipqualityscore.api-key", "antivpn.providers.ipqualityscore.api-key");
        if (key == null || key.isBlank()) {
            plugin.getLogger().fine("IPQualityScore provider enabled but no API key configured (secrets.yml); skipping.");
            return CompletableFuture.completedFuture(IpLookupResult.failed(ip, id()));
        }

        String baseUrl = plugin.getConfigManager().getString(
                "antivpn.providers.ipqualityscore.base-url",
                "https://ipqualityscore.com/api/json/ip/%KEY%/%IP%");
        long timeout = plugin.getConfigManager().getInt("antivpn.providers.ipqualityscore.timeout-ms", 3000);
        String url = baseUrl.replace("%KEY%", key).replace("%IP%", ip);

        return AsyncHttp.getAsync(url, timeout, executor)
                .thenApplyAsync(body -> parse(ip, body), executor)
                .exceptionally(ex -> {
                    plugin.getLogger().fine("IPQualityScore lookup failed: " + ex.getMessage());
                    return IpLookupResult.failed(ip, id());
                });
    }

    private IpLookupResult parse(String ip, String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("success") && !json.get("success").getAsBoolean()) {
                return IpLookupResult.failed(ip, id());
            }
            IpLookupResult result = IpLookupResult.success(ip, id());
            result.setVpn(getBool(json, "vpn"));
            result.setProxy(getBool(json, "proxy"));
            result.setTor(getBool(json, "tor"));
            result.setHosting(getBool(json, "is_crawler") || getBool(json, "hosting"));
            result.setCountry(getString(json, "country_code"));
            result.setIsp(getString(json, "ISP"));
            result.setOrganization(getString(json, "organization"));
            result.setAsn(getString(json, "ASN"));
            if (json.has("fraud_score") && json.get("fraud_score").getAsInt() >= 85) {
                result.setKnownAbuse(true);
            }
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
}
