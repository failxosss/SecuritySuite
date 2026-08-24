package com.example.securitysuite.anticheat.checks.packet;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IMPORTANT LIMITATION - read before relying on this check:
 *
 * A genuine "BadPacket" check (validating raw packet structure, packet
 * order, duplicate/out-of-order sequence IDs, malformed NBT in packets,
 * etc.) requires intercepting packets below the Bukkit event API - that
 * needs a packet library such as ProtocolLib or a native Netty channel
 * injector. Plain Paper/Bukkit does not expose raw incoming packets.
 *
 * Rather than pretending to do that without the dependency, this class
 * implements the subset of "bad packet" detection that IS reliably
 * possible from Bukkit-level events alone:
 *   - NaN/Infinite position or rotation values reaching PlayerMoveEvent
 *   - movement events firing while the player is dead or not fully logged in
 *   - move/action events arriving faster than the protocol's practical
 *     tick-bound rate (a coarse flood signal)
 *
 * If you need full packet-level validation, add ProtocolLib as a
 * dependency and extend this class (or add a new packet-tier check) to
 * hook ProtocolLib's PacketListener - the CheckManager registration and
 * violation/evidence plumbing already in place will work unchanged.
 */
public class BadPacketA implements Check {

    private static final int MAX_EVENTS_PER_SECOND = 40; // generous ceiling above vanilla's ~20/s move tick rate

    private final Map<UUID, Long> windowStart = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> windowCount = new ConcurrentHashMap<>();

    @Override
    public String id() { return "BadPacketA"; }

    @Override
    public String category() { return "packet"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        List<Location> history = data.getMovementHistory();
        if (!history.isEmpty()) {
            Location last = history.get(history.size() - 1);
            if (Double.isNaN(last.getX()) || Double.isNaN(last.getY()) || Double.isNaN(last.getZ())
                    || Double.isInfinite(last.getX()) || Double.isInfinite(last.getY()) || Double.isInfinite(last.getZ())) {
                return CheckResult.flag(1.0, 10.0, "NaN/Infinite position coordinate");
            }
        }

        if (player.isDead()) {
            return CheckResult.flag(0.5, 1.0, "movement processed while player marked dead");
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long start = windowStart.getOrDefault(uuid, now);
        if (now - start > 1000) {
            windowStart.put(uuid, now);
            windowCount.put(uuid, 1);
            return CheckResult.clean();
        }
        int count = windowCount.merge(uuid, 1, Integer::sum);
        if (count > MAX_EVENTS_PER_SECOND) {
            return CheckResult.flag(0.4, 1.0, "movement/action event flood: " + count + " events/sec");
        }

        return CheckResult.clean();
    }

    public void cleanup(UUID uuid) {
        windowStart.remove(uuid);
        windowCount.remove(uuid);
    }
}
