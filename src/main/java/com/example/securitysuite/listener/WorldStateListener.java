package com.example.securitysuite.listener;

import com.example.securitysuite.SecurityPlugin;
import com.example.securitysuite.anticheat.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.Vector;

/**
 * Tracks server-initiated velocity changes (knockback from combat,
 * explosions, etc.) so movement checks can grant a short grace window
 * instead of misreading the resulting motion as speed/fly hacking.
 */
public class WorldStateListener implements Listener {

    private final SecurityPlugin plugin;

    public WorldStateListener(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        data.markKnockback(plugin.getCurrentTick());

        if (!plugin.getCheckManager().velocityA.isEnabled(plugin)) return;
        Vector applied = event.getKnockback();
        Location before = player.getLocation().clone();

        // Give the victim's movement a few ticks to actually play out before
        // judging compliance - checking on the same tick the knockback was
        // applied would always read as "didn't move yet" and false-flag everyone.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Location after = player.getLocation();
            var result = plugin.getCheckManager().velocityA.evaluate(player, data, applied, before, after);
            plugin.getCheckManager().report(player, plugin.getCheckManager().velocityA, result);
        }, 4L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.REGEN) return;
        if (!plugin.getConfigManager().getBoolean("anticheat.checks.player", true)) return;
        if (!plugin.getCheckManager().regenA.isEnabled(plugin)) return;

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        plugin.getCheckManager().regenA.recordNaturalRegen(player, event.getAmount());
        var result = plugin.getCheckManager().regenA.evaluate(player, data);
        plugin.getCheckManager().report(player, plugin.getCheckManager().regenA, result);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            data.markKnockback(plugin.getCurrentTick());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // warm up PlayerData so the very first movement events have a valid object to write into
        plugin.getPlayerDataManager().get(event.getPlayer().getUniqueId());
    }
}
