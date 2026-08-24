package com.example.securitysuite.gui;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Simple, dependency-free inventory GUI (no external GUI library) exposing
 * the AntiSecurity dashboard: AntiVPN, AntiCheat, Player Lookup,
 * Violations, Recent Detections, Statistics, Configuration, Reload.
 * Permission-gated to security.admin at open time.
 */
public class SecurityGui implements Listener {

    private final SecurityPlugin plugin;
    private final Map<UUID, String> openMenus = new ConcurrentHashMap<>();

    public SecurityGui(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        if (!player.hasPermission("security.admin")) {
            player.sendMessage(plugin.getMessageManager().get("general.no-permission"));
            return;
        }
        String title = MessageManager.color(plugin.getConfigManager().getString("gui.title", "&8&lAntiSecurity"));
        int size = plugin.getConfigManager().getInt("gui.size", 27);
        Inventory inv = Bukkit.createInventory(null, size, title);

        inv.setItem(10, item(Material.SHIELD, "&bAntiVPN", "&7Provider status, cache, whitelist"));
        inv.setItem(12, item(Material.NETHER_STAR, "&cAntiCheat", "&7Checks, violations, alerts"));
        inv.setItem(14, item(Material.PLAYER_HEAD, "&ePlayer Lookup", "&7/antivpn info <player>", "&7/ac info <player>"));
        inv.setItem(16, item(Material.BOOK, "&6Recent Detections", "&7See console/staff alerts for a live feed"));
        inv.setItem(19, item(Material.PAPER, "&aStatistics", "&7/security stats"));
        inv.setItem(21, item(Material.COMPARATOR, "&dConfiguration", "&7Edit config.yml, then Reload"));
        inv.setItem(23, item(Material.REDSTONE_TORCH, "&cReload", "&7Reload config/messages/cache"));
        inv.setItem(25, item(Material.CLOCK, "&9Performance", "&7/security performance"));

        openMenus.put(player.getUniqueId(), "main");
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String menu = openMenus.get(player.getUniqueId());
        if (menu == null) return;

        String title = MessageManager.color(plugin.getConfigManager().getString("gui.title", "&8&lAntiSecurity"));
        if (!event.getView().getTitle().equals(title)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        switch (event.getSlot()) {
            case 10 -> player.sendMessage(MessageManager.color("&bUse &f/antivpn &bfor AntiVPN commands."));
            case 12 -> player.sendMessage(MessageManager.color("&cUse &f/anticheat &cfor AntiCheat commands."));
            case 14 -> player.sendMessage(MessageManager.color("&eUse &f/antivpn info <player> &eor &f/ac info <player>&e."));
            case 19 -> Bukkit.dispatchCommand(player, "security stats");
            case 23 -> {
                plugin.reloadAll();
                player.sendMessage(plugin.getMessageManager().get("general.reloaded"));
            }
            case 25 -> Bukkit.dispatchCommand(player, "security performance");
            default -> { /* no-op slots */ }
        }
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.color(name));
            List<String> colored = new ArrayList<>();
            for (String line : lore) colored.add(MessageManager.color(line));
            meta.setLore(colored);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
