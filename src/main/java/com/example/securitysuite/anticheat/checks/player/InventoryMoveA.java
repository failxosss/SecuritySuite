package com.example.securitysuite.anticheat.checks.player;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanilla clients cannot send movement packets while an inventory-type
 * screen (chest, crafting table, anvil, etc, NOT the player's own hotbar)
 * is open. "InventoryMove"/"Inventory" cheats exploit clients that keep
 * sending movement or combat actions while a container GUI is open server-
 * side. We track open-inventory state per player (set by the GUI/inventory
 * listener) and flag any movement sample recorded while a non-player
 * inventory is open.
 */
public class InventoryMoveA implements Check {

    private final Map<UUID, Boolean> inventoryOpen = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> flaggedTimestamps = new ConcurrentHashMap<>();

    @Override
    public String id() { return "InventoryMoveA"; }

    @Override
    public String category() { return "player"; }

    public void setInventoryOpen(UUID uuid, boolean open) {
        inventoryOpen.put(uuid, open);
    }

    public boolean isInventoryOpen(UUID uuid) {
        return inventoryOpen.getOrDefault(uuid, false);
    }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        if (!isInventoryOpen(player.getUniqueId())) return CheckResult.clean();

        // A single flag isn't enough (client/server GUI-close timing has a
        // few-tick race window that is entirely legitimate); require a short
        // burst of movement samples while the GUI is (still) marked open.
        Deque<Long> deque = flaggedTimestamps.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        deque.addLast(now);
        while (!deque.isEmpty() && now - deque.peekFirst() > 1000) deque.pollFirst();

        if (deque.size() < 3) return CheckResult.clean();

        return CheckResult.flag(0.6, 2.0, "movement recorded while a container inventory was open (" + deque.size() + " samples in 1s)");
    }

    public void cleanup(UUID uuid) {
        inventoryOpen.remove(uuid);
        flaggedTimestamps.remove(uuid);
    }
}
