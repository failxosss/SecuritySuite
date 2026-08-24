package com.example.securitysuite.database;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.model.RiskAssessment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe, size-bounded, TTL-aware LRU cache mapping IP -> RiskAssessment.
 * Backed by a LinkedHashMap in access-order mode for O(1) LRU eviction,
 * guarded by a lock since AntiVPN lookups happen off the main thread from
 * multiple concurrent join events.
 */
public class CacheManager {

    private final SecurityPlugin plugin;
    private final ReentrantLock lock = new ReentrantLock();
    private LinkedHashMap<String, Entry> cache;
    private int maxSize;
    private long ttlMillis;
    private boolean enabled;

    private record Entry(RiskAssessment assessment, long expiresAt) {}

    public CacheManager(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.enabled = plugin.getConfigManager().getBoolean("antivpn.cache.enabled", true);
        this.maxSize = Math.max(16, plugin.getConfigManager().getInt("antivpn.cache.max-size", 20000));
        this.ttlMillis = plugin.getConfigManager().getInt("antivpn.cache.ttl-seconds", 86400) * 1000L;
        this.cache = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > maxSize;
            }
        };
    }

    public RiskAssessment get(String ip) {
        if (!enabled) return null;
        lock.lock();
        try {
            Entry e = cache.get(ip);
            if (e == null) return null;
            if (System.currentTimeMillis() > e.expiresAt()) {
                cache.remove(ip);
                return null;
            }
            return e.assessment();
        } finally {
            lock.unlock();
        }
    }

    public void put(String ip, RiskAssessment assessment) {
        if (!enabled) return;
        lock.lock();
        try {
            cache.put(ip, new Entry(assessment, System.currentTimeMillis() + ttlMillis));
        } finally {
            lock.unlock();
        }
    }

    /** @return number of entries removed */
    public int clear() {
        lock.lock();
        try {
            int size = cache.size();
            cache.clear();
            return size;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return cache.size();
        } finally {
            lock.unlock();
        }
    }
}
