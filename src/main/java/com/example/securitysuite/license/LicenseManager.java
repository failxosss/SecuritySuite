package com.example.securitysuite.license;

import com.example.securitysuite.SecurityPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Gates the whole plugin behind a license key set in config.yml
 * ({@code license.key}). The plugin ships with 1000 pre-generated keys;
 * only their SHA-256 hashes are compiled into {@link LicenseKeyHashes} -
 * the plaintext keys themselves never appear anywhere in this jar.
 *
 * <p><b>Honest limitation:</b> this is an offline, client-side check. It
 * stops someone from reading the valid keys out of the jar (decompiling
 * only ever recovers hashes, which are not practically reversible for
 * random 16-character keys). It does <i>not</i>, and cannot, stop someone
 * determined enough from patching the compiled bytecode to skip the check
 * entirely (e.g. NOPing out the disablePlugin call below) - no purely
 * offline license check in a distributed compiled jar can prevent that.
 * If you need real enforcement against a technical adversary (not just a
 * casual buyer trying to avoid paying), that requires phoning home to a
 * server you control at startup, which is a different, larger feature -
 * see the README's "License enforcement" section for the trade-off.
 */
public class LicenseManager {

    // Mixed into the hash so this jar's key hashes don't match plaintext keys
    // hashed without it. Not a secret in itself (it's compiled into the jar
    // right alongside the hashes) - its only job is making sure a hash here
    // could only have been produced by *this* generator, not a coincidence.
    private static final String PEPPER = "SecuritySuite-License-Pepper-v1";

    private final SecurityPlugin plugin;
    private boolean valid = false;
    private String activeKey = null;

    public LicenseManager(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Reads {@code license.key} from config.yml, hashes it, and checks it
     * against the embedded set. Call once during onEnable, before any
     * other subsystem is initialized.
     */
    public boolean validate() {
        String key = plugin.getConfigManager().getString("license.key", "");
        if (key == null) key = "";
        key = key.trim().toUpperCase();

        if (key.isEmpty()) {
            plugin.getLogger().severe("========================================================");
            plugin.getLogger().severe("SecuritySuite is DISABLED: no license key set.");
            plugin.getLogger().severe("Add your key under 'license: key: \"YOUR-KEY\"' in");
            plugin.getLogger().severe("plugins/SecuritySuite/config.yml, then restart.");
            plugin.getLogger().severe("========================================================");
            return false;
        }

        String hash = sha256(PEPPER + key);
        if (hash == null || !LicenseKeyHashes.VALID_HASHES.contains(hash)) {
            plugin.getLogger().severe("========================================================");
            plugin.getLogger().severe("SecuritySuite is DISABLED: the license key in config.yml");
            plugin.getLogger().severe("is not valid. Double-check for typos, or contact whoever");
            plugin.getLogger().severe("you purchased the plugin from.");
            plugin.getLogger().severe("========================================================");
            return false;
        }

        this.valid = true;
        this.activeKey = key;
        plugin.getLogger().info("SecuritySuite license key accepted.");
        return true;
    }

    public boolean isValid() {
        return valid;
    }

    /** Masked for display in commands/GUI - never logs or shows the full key. */
    public String getMaskedKey() {
        if (activeKey == null || activeKey.length() < 4) return "----";
        return "****-****-****-" + activeKey.substring(activeKey.length() - 4);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            plugin.getLogger().severe("SHA-256 unavailable on this JVM - cannot validate license.");
            return null;
        }
    }
}
