package com.example.securitysuite;

import com.example.securitysuite.anticheat.CheckManager;
import com.example.securitysuite.anticheat.CompensationService;
import com.example.securitysuite.anticheat.PlayerDataManager;
import com.example.securitysuite.anticheat.PunishmentManager;
import com.example.securitysuite.anticheat.ViolationManager;
import com.example.securitysuite.antivpn.AntiVPNManager;
import com.example.securitysuite.antivpn.AntiVpnPunishmentDispatcher;
import com.example.securitysuite.command.AntiCheatCommand;
import com.example.securitysuite.command.AntiVpnCommand;
import com.example.securitysuite.command.SecurityCommand;
import com.example.securitysuite.config.ConfigManager;
import com.example.securitysuite.config.MessageManager;
import com.example.securitysuite.database.CacheManager;
import com.example.securitysuite.database.DatabaseManager;
import com.example.securitysuite.discord.DiscordManager;
import com.example.securitysuite.gui.SecurityGui;
import com.example.securitysuite.listener.CombatListener;
import com.example.securitysuite.listener.InventoryListener;
import com.example.securitysuite.listener.MovementListener;
import com.example.securitysuite.listener.PlayerConnectionListener;
import com.example.securitysuite.listener.WorldStateListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class SecurityPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private CacheManager cacheManager;
    private DatabaseManager databaseManager;
    private DiscordManager discordManager;
    private AntiVPNManager antiVPNManager;
    private AntiVpnPunishmentDispatcher antiVpnPunishmentDispatcher;
    private PlayerDataManager playerDataManager;
    private CheckManager checkManager;
    private ViolationManager violationManager;
    private PunishmentManager punishmentManager;
    private SecurityGui securityGui;

    private ExecutorService asyncExecutor;
    private final AtomicLong currentTick = new AtomicLong(0);
    private String salt;

    private final PerformanceStats performanceStats = new PerformanceStats();

    @Override
    public void onEnable() {
        this.asyncExecutor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "SecuritySuite-Async");
                    t.setDaemon(true);
                    return t;
                });

        this.configManager = new ConfigManager(this);
        configManager.load();

        this.salt = loadOrCreateSalt();

        this.messageManager = new MessageManager(this);
        messageManager.load();

        this.cacheManager = new CacheManager(this);
        cacheManager.load();

        this.databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        this.discordManager = new DiscordManager(this);

        this.antiVPNManager = new AntiVPNManager(this);
        antiVPNManager.load();
        this.antiVpnPunishmentDispatcher = new AntiVpnPunishmentDispatcher(this);

        this.playerDataManager = new PlayerDataManager(this);
        this.checkManager = new CheckManager(this);
        checkManager.load();
        this.violationManager = new ViolationManager(this);
        this.punishmentManager = new PunishmentManager(this);

        this.securityGui = new SecurityGui(this);

        registerListeners();
        registerCommands();
        scheduleTasks();

        getLogger().info("SecuritySuite enabled: AntiVPN=" + configManager.getBoolean("antivpn.enabled", true)
                + " AntiCheat=" + configManager.getBoolean("anticheat.enabled", true)
                + " Database=" + configManager.getString("database.type", "SQLITE"));
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
        if (asyncExecutor != null) asyncExecutor.shutdown();
        getLogger().info("SecuritySuite disabled.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldStateListener(this), this);
        getServer().getPluginManager().registerEvents(securityGui, this);
    }

    private void registerCommands() {
        getCommand("security").setExecutor(new SecurityCommand(this));
        getCommand("antivpn").setExecutor(new AntiVpnCommand(this));
        getCommand("anticheat").setExecutor(new AntiCheatCommand(this));
    }

    private void scheduleTasks() {
        // main-thread tick counter + compensation sampling - cheap, runs every tick
        getServer().getScheduler().runTaskTimer(this, () -> {
            currentTick.incrementAndGet();
            checkManager.getCompensationService().onServerTick();
        }, 1L, 1L);

        // violation decay
        int decayIntervalTicks = Math.max(1, configManager.getInt("anticheat.violation-decay.interval-seconds", 5) * 20);
        getServer().getScheduler().runTaskTimer(this, checkManager::tickDecay, decayIntervalTicks, decayIntervalTicks);

        // privacy data retention purge - once per hour, off-thread
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> databaseManager.purgeOldData(), 20L * 60, 20L * 60 * 60);
    }

    private String loadOrCreateSalt() {
        java.io.File saltFile = new java.io.File(getDataFolder(), ".salt");
        try {
            if (saltFile.exists()) {
                return java.nio.file.Files.readString(saltFile.toPath()).trim();
            }
            String generated = UUID.randomUUID().toString() + new SecureRandom().nextLong();
            saltFile.getParentFile().mkdirs();
            java.nio.file.Files.writeString(saltFile.toPath(), generated);
            return generated;
        } catch (Exception e) {
            getLogger().warning("Could not persist IP-hash salt; using a session-only salt (hashes will not match across restarts).");
            return UUID.randomUUID().toString();
        }
    }

    public long getCurrentTick() {
        return currentTick.get();
    }

    public String getSalt() {
        return salt;
    }

    public java.util.concurrent.Executor getAsyncExecutor() {
        return asyncExecutor;
    }

    public PerformanceStats getPerformanceStats() {
        return performanceStats;
    }

    public ConfigManager getConfigManager() { return configManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public CacheManager getCacheManager() { return cacheManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public DiscordManager getDiscordManager() { return discordManager; }
    public AntiVPNManager getAntiVPNManager() { return antiVPNManager; }
    public AntiVpnPunishmentDispatcher getAntiVpnPunishmentDispatcher() { return antiVpnPunishmentDispatcher; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public CheckManager getCheckManager() { return checkManager; }
    public ViolationManager getViolationManager() { return violationManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public CompensationService getCompensationService() { return checkManager.getCompensationService(); }
    public SecurityGui getSecurityGui() { return securityGui; }

    public void reloadAll() {
        configManager.reload();
        messageManager.reload();
        cacheManager.load();
        antiVPNManager.load();
        checkManager.load();
    }
}
