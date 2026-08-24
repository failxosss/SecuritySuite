package com.example.securitysuite.command;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AntiVpnCommand implements CommandExecutor, TabCompleter {

    private final SecurityPlugin plugin;

    public AntiVpnCommand(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("security.antivpn")) {
            sender.sendMessage(plugin.getMessageManager().get("general.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check", "info" -> handleCheck(sender, args);
            case "clearcache" -> {
                int removed = plugin.getCacheManager().clear();
                sender.sendMessage(plugin.getMessageManager().get("antivpn.cache-cleared", Map.of("count", String.valueOf(removed))));
            }
            case "help" -> sendHelp(sender);
            default -> sender.sendMessage(MessageManager.color("&cUnknown subcommand. Use /antivpn help."));
        }
        return true;
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageManager.color("&cUsage: /antivpn check <player>"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getMessageManager().get("general.player-not-found", Map.of("player", args[1])));
            return;
        }
        if (target.getAddress() == null) {
            sender.sendMessage(MessageManager.color("&cCould not resolve that player's address."));
            return;
        }
        String ip = target.getAddress().getAddress().getHostAddress();
        sender.sendMessage(MessageManager.color("&7Looking up " + target.getName() + "..."));

        plugin.getAntiVPNManager().assess(target.getUniqueId(), target.getName(), ip).thenAccept(assessment -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (assessment == null) {
                    sender.sendMessage(plugin.getMessageManager().get("antivpn.whitelisted", Map.of()));
                    return;
                }
                var lookup = assessment.getLookup();
                Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("player", target.getName());
                placeholders.put("vpn", String.valueOf(lookup.isVpn()));
                placeholders.put("proxy", String.valueOf(lookup.isProxy()));
                placeholders.put("hosting", String.valueOf(lookup.isHosting()));
                placeholders.put("tor", String.valueOf(lookup.isTor()));
                placeholders.put("risk", assessment.getScore() + "");
                placeholders.put("rating", assessment.getRating().name());
                sender.sendMessage(plugin.getMessageManager().get("antivpn.check-result", placeholders));
            });
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageManager.color("&8&m----&r &bAntiVPN &8&m----"));
        sender.sendMessage(MessageManager.color("&f/antivpn check <player> &7- run a fresh AntiVPN lookup"));
        sender.sendMessage(MessageManager.color("&f/antivpn info <player> &7- alias of check"));
        sender.sendMessage(MessageManager.color("&f/antivpn clearcache &7- clear the IP lookup cache"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("check", "info", "clearcache", "help"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("info"))) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}
