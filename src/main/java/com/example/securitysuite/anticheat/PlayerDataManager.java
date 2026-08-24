package com.example.securitysuite.anticheat;

import com.example.securitysuite.SecurityPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Map;

public class PlayerDataManager {

    private final SecurityPlugin plugin;
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    public PlayerDataManager(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerData get(UUID uuid) {
        return data.computeIfAbsent(uuid, id -> {
            PlayerData pd = new PlayerData(id);
            pd.setEvidenceBufferSize(plugin.getConfigManager().getInt("anticheat.evidence.buffer-size", 20));
            return pd;
        });
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }

    public Map<UUID, PlayerData> all() {
        return data;
    }
}
