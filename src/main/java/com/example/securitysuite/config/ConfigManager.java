package com.example.securitysuite.config;

import com.example.securitysuite.SecurityPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads config.yml and secrets.yml. secrets.yml values always take
 * precedence over config.yml for the same dotted path. Nothing from
 * secrets.yml is ever logged - {@link #getSecret(String, String)} is the
 * only accessor for it and callers must not print its return value.
 */
public class ConfigManager {

    private final SecurityPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration secrets;
    private File secretsFile;

    public ConfigManager(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadSecrets();
    }

    private void loadSecrets() {
        secretsFile = new File(plugin.getDataFolder(), "secrets.yml");
        if (!secretsFile.exists()) {
            try (InputStream in = plugin.getResource("secrets.example.yml")) {
                if (in != null) {
                    java.nio.file.Files.copy(in, secretsFile.toPath());
                } else {
                    secretsFile.getParentFile().mkdirs();
                    secretsFile.createNewFile();
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create secrets.yml: " + e.getMessage());
            }
        }
        secrets = YamlConfiguration.loadConfiguration(secretsFile);
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.secrets = YamlConfiguration.loadConfiguration(secretsFile);
    }

    public FileConfiguration raw() {
        return config;
    }

    /**
     * Resolves a secret: secrets.yml wins, falls back to config.yml,
     * falls back to the supplied default. Never logs the resolved value.
     */
    public String getSecret(String path, String configFallbackPath) {
        String fromSecrets = secrets.getString(path);
        if (fromSecrets != null && !fromSecrets.isBlank()) {
            return fromSecrets;
        }
        String fromConfig = config.getString(configFallbackPath);
        return fromConfig == null ? "" : fromConfig;
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public java.util.List<String> getStringList(String path) {
        return config.getStringList(path);
    }
}
