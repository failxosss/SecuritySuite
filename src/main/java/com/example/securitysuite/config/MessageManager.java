package com.example.securitysuite.config;

import com.example.securitysuite.SecurityPlugin;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class MessageManager {

    private final SecurityPlugin plugin;
    private FileConfiguration messages;
    private File file;

    public MessageManager(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);

        // Merge in any keys added in newer plugin versions without overwriting user edits.
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
                messages.options().copyDefaults(true);
                messages.save(file);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to merge messages.yml defaults: " + e.getMessage());
        }
    }

    public void reload() {
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String raw(String path) {
        String s = messages.getString(path);
        return s == null ? path : s;
    }

    public String get(String path) {
        return color(prefixIfNeeded(path, raw(path)));
    }

    public String get(String path, Map<String, String> placeholders) {
        String msg = raw(path);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("%" + e.getKey() + "%", e.getValue());
        }
        return color(prefixIfNeeded(path, msg));
    }

    /** For messages that shouldn't get the "[SecuritySuite]" prefix (e.g. alert lines that build their own). */
    public String getRaw(String path, Map<String, String> placeholders) {
        String msg = raw(path);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("%" + e.getKey() + "%", e.getValue());
        }
        return color(msg);
    }

    private String prefixIfNeeded(String path, String msg) {
        if (path.startsWith("anticheat.alert") || path.equals("anticheat.punishment-broadcast")) {
            return msg; // these already include their own [AntiCheat] tag
        }
        return raw("prefix") + msg;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
