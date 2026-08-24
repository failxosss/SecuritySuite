package com.example.securitysuite.antivpn.provider;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.model.IpLookupResult;
import com.example.securitysuite.util.AsyncHttp;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Generic provider for any third-party JSON IP-intelligence API, driven
 * entirely by config.yml's antivpn.providers.custom.field-map. This lets
 * server owners plug in an in-house or niche provider without touching code,
 * as long as the API returns a flat JSON object with boolean/string fields.
 *
 * If your API's response shape is nested or otherwise incompatible with a
 * flat field-map, this provider cannot honor it reliably - implement a
 * dedicated IpProvider class instead rather than fighting the mapper.
 */
public class CustomProvider implements IpProvider {

    private final SecurityPlugin plugin;
    private final Executor executor;

    public CustomProvider(SecurityPlugin plugin, Executor executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    @Override
    public String id() {
        return "custom";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfigManager().getBoolean("antivpn.providers.custom.enabled", false);
    }

    @Override
    public CompletableFuture<IpLookupResult> lookup(String ip) {
        String baseUrl = plugin.getConfigManager().getString("antivpn.providers.custom.base-url", "");
        if (baseUrl.isBlank()) {
            return CompletableFuture.completedFuture(IpLookupResult.failed(ip, id()));
        }
        String key = plugin.getConfigManager().getSecret(
                "providers.custom.api-key", "antivpn.providers.custom.api-key");
        long timeout = plugin.getConfigManager().getInt("antivpn.providers.custom.timeout-ms", 3000);
        String url = baseUrl.replace("%IP%", ip).replace("%KEY%", key == null ? "" : key);

        return AsyncHttp.getAsync(url, timeout, executor)
                .thenApplyAsync(body -> parse(ip, body), executor)
                .exceptionally(ex -> {
                    plugin.getLogger().fine("Custom provider lookup failed: " + ex.getMessage());
                    return IpLookupResult.failed(ip, id());
                });
    }

    private IpLookupResult parse(String ip, String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            IpLookupResult result = IpLookupResult.success(ip, id());
            result.setVpn(boolField(json, "vpn"));
            result.setProxy(boolField(json, "proxy"));
            result.setHosting(boolField(json, "hosting"));
            result.setTor(boolField(json, "tor"));
            result.setCountry(stringField(json, "country"));
            result.setAsn(stringField(json, "asn"));
            result.setIsp(stringField(json, "isp"));
            result.setOrganization(stringField(json, "org"));
            return result;
        } catch (Exception e) {
            return IpLookupResult.failed(ip, id());
        }
    }

    private String mappedKey(String logicalName) {
        String mapped = plugin.getConfigManager().getString(
                "antivpn.providers.custom.field-map." + logicalName, logicalName);
        return mapped == null || mapped.isBlank() ? logicalName : mapped;
    }

    private boolean boolField(JsonObject o, String logicalName) {
        String key = mappedKey(logicalName);
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
    }

    private String stringField(JsonObject o, String logicalName) {
        String key = mappedKey(logicalName);
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}
