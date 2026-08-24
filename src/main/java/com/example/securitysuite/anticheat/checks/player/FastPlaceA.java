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
 * Tracks block-place timestamps per player (separate lightweight ring
 * buffer, since this is a much lower-frequency event than movement/clicks
 * and doesn't need to live in the main PlayerData history). Flags a
 * placement rate beyond what's achievable with vanilla client-side place
 * cooldown, factoring in ping (a burst of placements can arrive together
 * after a lag spike and is legitimate).
 */
public class FastPlaceA implements Check {

    private static final int WINDOW_MS = 1000;
    private static final int MAX_PLACES_PER_WINDOW = 9; // generous human ceiling incl. double-hand macros users sometimes trigger legitimately

    private final Map<UUID, Deque<Long>> placeTimestamps = new ConcurrentHashMap<>();

    @Override
    public String id() { return "FastPlaceA"; }

    @Override
    public String category() { return "player"; }

    public void recordPlace(Player player) {
        Deque<Long> deque = placeTimestamps.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        deque.addLast(now);
        while (!deque.isEmpty() && now - deque.peekFirst() > WINDOW_MS * 2L) {
            deque.pollFirst();
        }
    }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        Deque<Long> deque = placeTimestamps.get(player.getUniqueId());
        if (deque == null) return CheckResult.clean();

        long now = System.currentTimeMillis();
        long count = deque.stream().filter(t -> now - t <= WINDOW_MS).count();

        if (count <= MAX_PLACES_PER_WINDOW) return CheckResult.clean();

        double overshoot = count - MAX_PLACES_PER_WINDOW;
        double confidence = Math.min(0.85, overshoot / 10.0);
        return CheckResult.flag(confidence, Math.min(4.0, overshoot),
                "placed " + count + " blocks within " + WINDOW_MS + "ms (max plausible " + MAX_PLACES_PER_WINDOW + ")");
    }

    public void cleanup(UUID uuid) {
        placeTimestamps.remove(uuid);
    }
}
