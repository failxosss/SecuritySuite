package com.example.securitysuite.database;

import com.example.securitysuite.SecurityPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Owns the JDBC connection pool and all persistence. Every public method
 * returns a CompletableFuture and runs its SQL on the plugin's async
 * executor - nothing here ever touches the main server thread.
 */
public class DatabaseManager {

    private final SecurityPlugin plugin;
    private final Executor executor;
    private HikariDataSource dataSource;
    private String type;

    public DatabaseManager(SecurityPlugin plugin) {
        this.plugin = plugin;
        this.executor = plugin.getAsyncExecutor();
    }

    public void connect() {
        type = plugin.getConfigManager().getString("database.type", "SQLITE").toUpperCase();
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setMaximumPoolSize(plugin.getConfigManager().getInt("database.pool.maximum-pool-size", 6));
        hikariConfig.setMinimumIdle(plugin.getConfigManager().getInt("database.pool.minimum-idle", 1));
        hikariConfig.setConnectionTimeout(plugin.getConfigManager().getInt("database.pool.connection-timeout-ms", 5000));

        switch (type) {
            case "MYSQL", "MARIADB" -> {
                String host = plugin.getConfigManager().getString("database.mysql.host", "localhost");
                int port = plugin.getConfigManager().getInt("database.mysql.port", 3306);
                String db = plugin.getConfigManager().getString("database.mysql.database", "securitysuite");
                boolean ssl = plugin.getConfigManager().getBoolean("database.mysql.use-ssl", false);
                String user = plugin.getConfigManager().getString("database.mysql.username", "securitysuite");
                String pass = plugin.getConfigManager().getSecret("database.mysql.password", "database.mysql.password");

                hikariConfig.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + db + "?useSSL=" + ssl);
                hikariConfig.setUsername(user);
                hikariConfig.setPassword(pass);
                hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
            }
            default -> {
                File dbFile = new File(plugin.getDataFolder(), plugin.getConfigManager().getString("database.sqlite.file", "securitysuite.db"));
                hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                hikariConfig.setDriverClassName("org.sqlite.JDBC");
                hikariConfig.setMaximumPoolSize(1); // SQLite is single-writer; keep pool small
            }
        }

        dataSource = new HikariDataSource(hikariConfig);
        initSchema();
    }

    private void initSchema() {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(16) NOT NULL,
                    last_ip VARCHAR(128),
                    first_seen BIGINT NOT NULL,
                    last_seen BIGINT NOT NULL
                )
            """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS antivpn_detections (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid VARCHAR(36) NOT NULL,
                    ip_ref VARCHAR(128),
                    vpn INTEGER NOT NULL,
                    proxy INTEGER NOT NULL,
                    hosting INTEGER NOT NULL,
                    tor INTEGER NOT NULL,
                    risk_score INTEGER NOT NULL,
                    rating VARCHAR(16) NOT NULL,
                    action_taken VARCHAR(32),
                    created_at BIGINT NOT NULL
                )
            """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS violations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid VARCHAR(36) NOT NULL,
                    check_name VARCHAR(64) NOT NULL,
                    vl DOUBLE NOT NULL,
                    confidence DOUBLE NOT NULL,
                    reason VARCHAR(255),
                    created_at BIGINT NOT NULL
                )
            """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS punishments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid VARCHAR(36) NOT NULL,
                    source VARCHAR(16) NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    reason VARCHAR(255),
                    created_at BIGINT NOT NULL
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database schema: " + e.getMessage());
        }
    }

    public void close() {
        if (dataSource != null) dataSource.close();
    }

    // -------------------- Privacy helpers --------------------

    public String prepareIpForStorage(String ip) {
        boolean store = plugin.getConfigManager().getBoolean("privacy.store-ip", false);
        if (!store) return null;
        boolean hash = plugin.getConfigManager().getBoolean("privacy.hash-ip", true);
        if (!hash) return ip;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = plugin.getSalt() + ip;
            byte[] hashed = digest.digest(salted.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------- Async operations --------------------

    public CompletableFuture<Void> upsertPlayer(UUID uuid, String username, String ip) {
        return CompletableFuture.runAsync(() -> {
            String storedIp = prepareIpForStorage(ip);
            String sql = """
                INSERT INTO players (uuid, username, last_ip, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET username = excluded.username,
                    last_ip = excluded.last_ip, last_seen = excluded.last_seen
            """;
            long now = System.currentTimeMillis();
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, storedIp);
                ps.setLong(4, now);
                ps.setLong(5, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                // MySQL doesn't support SQLite's ON CONFLICT syntax identically in all versions;
                // fall back to a manual upsert if the combined statement fails.
                manualUpsertPlayer(uuid, username, storedIp, now);
            }
        }, executor);
    }

    private void manualUpsertPlayer(UUID uuid, String username, String storedIp, long now) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement upd = c.prepareStatement(
                    "UPDATE players SET username=?, last_ip=?, last_seen=? WHERE uuid=?")) {
                upd.setString(1, username);
                upd.setString(2, storedIp);
                upd.setLong(3, now);
                upd.setString(4, uuid.toString());
                int rows = upd.executeUpdate();
                if (rows == 0) {
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO players (uuid, username, last_ip, first_seen, last_seen) VALUES (?,?,?,?,?)")) {
                        ins.setString(1, uuid.toString());
                        ins.setString(2, username);
                        ins.setString(3, storedIp);
                        ins.setLong(4, now);
                        ins.setLong(5, now);
                        ins.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to upsert player record: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> recordDetection(UUID uuid, String ip, boolean vpn, boolean proxy,
                                                     boolean hosting, boolean tor, int riskScore,
                                                     String rating, String actionTaken) {
        return CompletableFuture.runAsync(() -> {
            String storedIp = prepareIpForStorage(ip);
            String sql = """
                INSERT INTO antivpn_detections (uuid, ip_ref, vpn, proxy, hosting, tor, risk_score, rating, action_taken, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
            """;
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, storedIp);
                ps.setInt(3, vpn ? 1 : 0);
                ps.setInt(4, proxy ? 1 : 0);
                ps.setInt(5, hosting ? 1 : 0);
                ps.setInt(6, tor ? 1 : 0);
                ps.setInt(7, riskScore);
                ps.setString(8, rating);
                ps.setString(9, actionTaken);
                ps.setLong(10, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to record AntiVPN detection: " + e.getMessage());
            }
        }, executor);
    }

    public CompletableFuture<Void> recordViolation(UUID uuid, String checkName, double vl, double confidence, String reason) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO violations (uuid, check_name, vl, confidence, reason, created_at) VALUES (?,?,?,?,?,?)";
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, checkName);
                ps.setDouble(3, vl);
                ps.setDouble(4, confidence);
                ps.setString(5, reason);
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to record violation: " + e.getMessage());
            }
        }, executor);
    }

    public CompletableFuture<Void> recordPunishment(UUID uuid, String source, String action, String reason) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO punishments (uuid, source, action, reason, created_at) VALUES (?,?,?,?,?)";
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, source);
                ps.setString(3, action);
                ps.setString(4, reason);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to record punishment: " + e.getMessage());
            }
        }, executor);
    }

    /** Deletes rows older than privacy.data-retention-days, if that value is > 0. Call from a repeating async task. */
    public CompletableFuture<Void> purgeOldData() {
        int days = plugin.getConfigManager().getInt("privacy.data-retention-days", 30);
        if (days <= 0) return CompletableFuture.completedFuture(null);
        long cutoff = System.currentTimeMillis() - (days * 86400_000L);
        return CompletableFuture.runAsync(() -> {
            String[] sqls = {
                    "DELETE FROM antivpn_detections WHERE created_at < ?",
                    "DELETE FROM violations WHERE created_at < ?",
                    "DELETE FROM punishments WHERE created_at < ?"
            };
            try (Connection c = dataSource.getConnection()) {
                for (String sql : sqls) {
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setLong(1, cutoff);
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to purge old data: " + e.getMessage());
            }
        }, executor);
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }
}
