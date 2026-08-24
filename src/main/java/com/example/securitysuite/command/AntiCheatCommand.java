package com.example.securitysuite.command;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AntiCheatCommand implements CommandExecutor, TabCompleter {

    private final SecurityPlugin plugin;
    private final Set<UUID> alertsEnabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> verboseEnabled = ConcurrentHashMap.newKeySet();

    public AntiCheatCommand(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("security.anticheat")) {
            sender.sendMessage(plugin.getMessageManager().get("general.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "alerts" -> toggleAlerts(sender);
            case "verbose" -> toggleVerbose(sender);
            case "info" -> handleInfo(sender, args);
            case "violations" -> handleViolations(sender, args);
            case "reset" -> handleReset(sender, args);
            case "checks" -> handleChecks(sender);
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(plugin.getMessageManager().get("general.reloaded"));
            }
            case "help" -> sendHelp(sender);
            default -> sender.sendMessage(MessageManager.color("&cUnknown subcommand. Use /ac help."));
        }
        return true;
    }

    private void toggleAlerts(CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        if (!player.hasPermission("security.alerts")) {
            sender.sendMessage(plugin.getMessageManager().get("general.no-permission"));
            return;
        }
        if (alertsEnabled.remove(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().get("anticheat.alerts-disabled"));
        } else {
            alertsEnabled.add(player.getUniqueId());
            player.sendMessage(plugin.getMessageManager().get("anticheat.alerts-enabled"));
        }
    }

    public boolean hasAlertsEnabled(UUID uuid) {
        return alertsEnabled.contains(uuid);
    }

    private void toggleVerbose(CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        if (!player.hasPermission("security.verbose")) {
            sender.sendMessage(plugin.getMessageManager().get("general.no-permission"));
            return;
        }
        if (verboseEnabled.remove(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().get("anticheat.verbose-disabled"));
        } else {
            verboseEnabled.add(player.getUniqueId());
            player.sendMessage(plugin.getMessageManager().get("anticheat.verbose-enabled"));
        }
    }

    public boolean hasVerboseEnabled(UUID uuid) {
        return verboseEnabled.contains(uuid);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args);
        if (target == null) return;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        sender.sendMessage(plugin.getMessageManager().get("anticheat.player-info-header", Map.of("player", target.getName())));

        if (data.allViolations().isEmpty()) {
            sender.sendMessage(plugin.getMessageManager().get("anticheat.no-violations", Map.of("player", target.getName())));
            return;
        }

        for (var entry : data.allViolations().entrySet()) {
            sender.sendMessage(plugin.getMessageManager().get("anticheat.player-info-line", Map.of(
                    "check", entry.getKey(),
                    "vl", String.format("%.1f", entry.getValue()),
                    "peak", String.format("%.1f", data.getPeakViolation(entry.getKey())))));
        }
    }

    private void handleViolations(CommandSender sender, String[] args) {
        handleInfo(sender, args); // same view; kept as a distinct command per spec
    }

    private void handleReset(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args);
        if (target == null) return;
        plugin.getPlayerDataManager().get(target.getUniqueId()).resetAll();
        sender.sendMessage(plugin.getMessageManager().get("anticheat.violations-reset", Map.of("player", target.getName())));
    }

    private void handleChecks(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().get("anticheat.checks-list-header"));
        Map<String, List<Check>> byCategory = new java.util.LinkedHashMap<>();
        for (Check check : plugin.getCheckManager().all().values()) {
            byCategory.computeIfAbsent(check.category(), k -> new ArrayList<>()).add(check);
        }
        for (var entry : byCategory.entrySet()) {
            boolean categoryEnabled = plugin.getConfigManager().getBoolean("anticheat.checks." + entry.getKey(), true);
            sender.sendMessage(MessageManager.color("&e" + entry.getKey() + " &7(" + (categoryEnabled ? "&aenabled" : "&cdisabled") + "&7):"));
            for (Check check : entry.getValue()) {
                sender.sendMessage(MessageManager.color("  &f" + check.id() + " &7- " + (check.isEnabled(plugin) ? "&aON" : "&cOFF")));
            }
        }
    }

    private Player resolveTarget(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageManager.color("&cUsage: /ac " + args[0] + " <player>"));
            return null;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getMessageManager().get("general.player-not-found", Map.of("player", args[1])));
        }
        return target;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageManager.color("&8&m----&r &bAntiCheat &8&m----"));
        sender.sendMessage(MessageManager.color("&f/ac alerts &7- toggle receiving alerts"));
        sender.sendMessage(MessageManager.color("&f/ac verbose &7- toggle verbose output"));
        sender.sendMessage(MessageManager.color("&f/ac info <player> &7- view a player's violation levels"));
        sender.sendMessage(MessageManager.color("&f/ac violations <player> &7- same as info"));
        sender.sendMessage(MessageManager.color("&f/ac reset <player> &7- reset all violation levels"));
        sender.sendMessage(MessageManager.color("&f/ac checks &7- list all registered checks and their status"));
        sender.sendMessage(MessageManager.color("&f/ac reload &7- reload config/messages"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("alerts", "verbose", "info", "violations", "reset", "checks", "reload", "help"), args[0]);
        }
        if (args.length == 2 && Set.of("info", "violations", "reset").contains(args[0].toLowerCase())) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}
