package com.example.securitysuite.listener;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public class InventoryListener implements Listener {

    private final SecurityPlugin plugin;

    public InventoryListener(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() == InventoryType.CRAFTING || event.getInventory().getType() == InventoryType.PLAYER) return;
        plugin.getCheckManager().inventoryMoveA.setInventoryOpen(player.getUniqueId(), true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.getCheckManager().inventoryMoveA.setInventoryOpen(player.getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        plugin.getCheckManager().fastPlaceA.recordPlace(player);

        PlayerData placeData = plugin.getPlayerDataManager().get(player.getUniqueId());
        long placeTick = plugin.getCurrentTick();
        placeData.markBlockPlace(placeTick);
        reportMultiAction(player, placeData, placeTick);

        if (!plugin.getConfigManager().getBoolean("anticheat.checks.player", true)) return;
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (plugin.getCheckManager().fastPlaceA.isEnabled(plugin)) {
            var result = plugin.getCheckManager().fastPlaceA.evaluate(player, data);
            plugin.getCheckManager().report(player, plugin.getCheckManager().fastPlaceA, result);
        }

        if (plugin.getCheckManager().scaffoldA.isEnabled(plugin)) {
            var result = plugin.getCheckManager().scaffoldA.evaluate(player, data,
                    event.getBlockAgainst().getLocation(), player.getLocation().getPitch());
            plugin.getCheckManager().report(player, plugin.getCheckManager().scaffoldA, result);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        PlayerData breakData = plugin.getPlayerDataManager().get(player.getUniqueId());
        long breakTick = plugin.getCurrentTick();
        breakData.markBlockBreak(breakTick);
        reportMultiAction(player, breakData, breakTick);

        if (!plugin.getConfigManager().getBoolean("anticheat.checks.player", true)) return;
        if (!plugin.getCheckManager().fastBreakA.isEnabled(plugin)) return;

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        Long breakStart = breakStartTimes.remove(player.getUniqueId());
        long elapsed = breakStart == null ? Long.MAX_VALUE : System.currentTimeMillis() - breakStart;

        var result = plugin.getCheckManager().fastBreakA.evaluate(player, data, event.getBlock().getType(),
                player.getInventory().getItemInMainHand(), elapsed, player.getGameMode().name().equals("CREATIVE"));
        plugin.getCheckManager().report(player, plugin.getCheckManager().fastBreakA, result);
    }

    private void reportMultiAction(Player player, PlayerData data, long tick) {
        if (!plugin.getConfigManager().getBoolean("anticheat.checks.packet", true)) return;
        if (!plugin.getCheckManager().multiActionA.isEnabled(plugin)) return;
        var result = plugin.getCheckManager().multiActionA.evaluate(player, data, tick);
        plugin.getCheckManager().report(player, plugin.getCheckManager().multiActionA, result);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfigManager().getBoolean("anticheat.checks.player", true)) return;
        if (!plugin.getCheckManager().fastUseA.isEnabled(plugin)) return;

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        var result = plugin.getCheckManager().fastUseA.evaluate(player, data, event.getItem().getType());
        plugin.getCheckManager().report(player, plugin.getCheckManager().fastUseA, result);
    }

    // Tracked via BlockDamageEvent (start of break) so FastBreakA has a real elapsed time.
    private final java.util.Map<java.util.UUID, Long> breakStartTimes = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(org.bukkit.event.block.BlockDamageEvent event) {
        breakStartTimes.putIfAbsent(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }
}
