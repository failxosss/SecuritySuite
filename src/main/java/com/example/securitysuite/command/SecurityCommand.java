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
import java.util.stream.Collectors;

public class SecurityCommand implements CommandExecutor, TabCompleter {

    private final SecurityPlugin plugin;

    public SecurityCommand(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("security.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("general.no-permission"));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendHelp(sender);
                return true;
            }
            plugin.getSecurityGui().openMain(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(plugin.getMessageManager().get("general.reloaded"));
            }
            case "stats" -> sendStats(sender);
            case "performance", "debug" -> sendPerformance(sender);
            case "test" -> handleTest(sender, args);
            case "help" -> sendHelp(sender);
            default -> sender.sendMessage(MessageManager.color("&cUnknown subcommand. Use /security help."));
        }
        return true;
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageManager.color("&cUsage: /security test <player>"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getMessageManager().get("general.player-not-found",
                    java.util.Map.of("player", args[1])));
            return;
        }
        boolean nowEnabled = !plugin.getPunishmentManager().isTestMode(target.getUniqueId());
        plugin.getPunishmentManager().setTestMode(target.getUniqueId(), nowEnabled);
        sender.sendMessage(MessageManager.color((nowEnabled ? "&aEnabled" : "&7Disabled")
                + " AntiCheat test mode for " + target.getName() + " (simulates actions without punishing)."));
    }

    private void sendStats(CommandSender sender) {
        sender.sendMessage(MessageManager.color("&8&m----&r &bSecuritySuite Stats &8&m----"));
        sender.sendMessage(MessageManager.color("&7Online players tracked: &f" + plugin.getPlayerDataManager().all().size()));
        sender.sendMessage(MessageManager.color("&7AntiVPN cache size: &f" + plugin.getCacheManager().size()));
        sender.sendMessage(MessageManager.color("&7Registered AntiCheat checks: &f" + plugin.getCheckManager().all().size()));
        sender.sendMessage(MessageManager.color("&7Current TPS estimate: &f"
                + String.format("%.2f", plugin.getCompensationService().getTps())));
    }

    private void sendPerformance(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);

        sender.sendMessage(MessageManager.color("&8&m----&r &9Performance &8&m----"));
        sender.sendMessage(MessageManager.color("&7Memory: &f" + usedMb + "MB / " + maxMb + "MB"));
        sender.sendMessage(MessageManager.color("&7Cache size: &f" + plugin.getCacheManager().size()));
        sender.sendMessage(MessageManager.color("&7Active checks: &f" + plugin.getCheckManager().all().size()));
        sender.sendMessage(MessageManager.color("&7Avg API lookup time: &f"
                + String.format("%.1fms", plugin.getPerformanceStats().averageApiLookupMs())));
        sender.sendMessage(MessageManager.color("&7Avg DB query time: &f"
                + String.format("%.1fms", plugin.getPerformanceStats().averageDbQueryMs())));

        plugin.getCheckManager().all().values().stream().limit(6).forEach(check ->
                sender.sendMessage(MessageManager.color(String.format("&7  %s: &f%.3fms avg",
                        check.id(), plugin.getPerformanceStats().averageCheckTimeMs(check.id())))));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageManager.color("&8&m----&r &bSecuritySuite &8&m----"));
        sender.sendMessage(MessageManager.color("&f/security &7- open the admin GUI"));
        sender.sendMessage(MessageManager.color("&f/security reload &7- reload config/messages/cache"));
        sender.sendMessage(MessageManager.color("&f/security stats &7- quick stats summary"));
        sender.sendMessage(MessageManager.color("&f/security performance &7- performance breakdown"));
        sender.sendMessage(MessageManager.color("&f/security test <player> &7- toggle AntiCheat test (no-punish) mode"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "stats", "performance", "debug", "test", "help"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}
