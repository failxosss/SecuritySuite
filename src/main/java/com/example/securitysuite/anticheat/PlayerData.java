package com.example.securitysuite.anticheat;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling per-player state consumed by AntiCheat checks: movement/rotation
 * history, click timing, current violation levels per check, and a short
 * evidence buffer staff can inspect via /ac info. Bounded deques are used
 * everywhere so memory usage cannot grow unbounded per player.
 */
public class PlayerData {

    private static final int HISTORY_SIZE = 40;

    private final UUID uuid;

    private final Deque<Location> movementHistory = new ArrayDeque<>(HISTORY_SIZE);
    private final Deque<float[]> rotationHistory = new ArrayDeque<>(HISTORY_SIZE); // [yaw, pitch]
    private final Deque<Long> clickTimestamps = new ArrayDeque<>(HISTORY_SIZE);
    private final Deque<Long> moveTimestamps = new ArrayDeque<>(HISTORY_SIZE); // wall-clock ms, parallel to movementHistory - used by TimerA

    private final Map<String, Double> violationLevels = new ConcurrentHashMap<>();
    private final Map<String, Double> peakViolationLevels = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> evidence = new ConcurrentHashMap<>();

    // transient state flags used by movement checks to reduce false positives
    private volatile boolean onIce;
    private volatile boolean inWater;
    private volatile boolean onLadder;
    private volatile boolean inVehicle;
    private volatile boolean gliding;
    private volatile long lastTeleportTick;
    private volatile long lastKnockbackTick;
    private volatile long lastVelocityTick;
    private volatile int currentPingMs;
    private volatile long lastMoveProcessTick;

    // extra timing state for the newer checks (TimerA, MultiActionA, FastUseA, VelocityA) -
    // kept as simple volatile ticks/flags rather than growing the history deques further,
    // since these only ever need the single most recent value, not a rolling window.
    private volatile long lastBlockBreakTick = -1;
    private volatile long lastBlockPlaceTick = -1;
    private volatile long lastAttackTick = -1;
    private volatile boolean handRaised;
    private volatile long handRaiseStartTick = -1;

    private int evidenceBufferSize = 20;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() { return uuid; }

    public void setEvidenceBufferSize(int size) { this.evidenceBufferSize = size; }

    public void pushMovement(Location loc) {
        if (movementHistory.size() >= HISTORY_SIZE) movementHistory.pollFirst();
        movementHistory.addLast(loc.clone());
        if (moveTimestamps.size() >= HISTORY_SIZE) moveTimestamps.pollFirst();
        moveTimestamps.addLast(System.currentTimeMillis());
    }

    public List<Location> getMovementHistory() {
        return new ArrayList<>(movementHistory);
    }

    public List<Long> getMoveTimestamps() {
        return new ArrayList<>(moveTimestamps);
    }

    public void pushRotation(float yaw, float pitch) {
        if (rotationHistory.size() >= HISTORY_SIZE) rotationHistory.pollFirst();
        rotationHistory.addLast(new float[]{yaw, pitch});
    }

    public List<float[]> getRotationHistory() {
        return new ArrayList<>(rotationHistory);
    }

    public void pushClick() {
        long now = System.currentTimeMillis();
        if (clickTimestamps.size() >= HISTORY_SIZE) clickTimestamps.pollFirst();
        clickTimestamps.addLast(now);
    }

    public List<Long> getClickTimestamps() {
        return new ArrayList<>(clickTimestamps);
    }

    public double getViolation(String checkId) {
        return violationLevels.getOrDefault(checkId, 0.0);
    }

    public double getPeakViolation(String checkId) {
        return peakViolationLevels.getOrDefault(checkId, 0.0);
    }

    public void addViolation(String checkId, double amount, String reason) {
        double newVal = violationLevels.merge(checkId, amount, Double::sum);
        peakViolationLevels.merge(checkId, newVal, Math::max);
        pushEvidence(checkId, reason);
    }

    public void decay(String checkId, double amount) {
        violationLevels.computeIfPresent(checkId, (k, v) -> Math.max(0.0, v - amount));
    }

    public void resetViolation(String checkId) {
        violationLevels.remove(checkId);
        peakViolationLevels.remove(checkId);
        evidence.remove(checkId);
    }

    public void resetAll() {
        violationLevels.clear();
        peakViolationLevels.clear();
        evidence.clear();
    }

    public Map<String, Double> allViolations() {
        return new LinkedHashMap<>(violationLevels);
    }

    private void pushEvidence(String checkId, String reason) {
        Deque<String> buf = evidence.computeIfAbsent(checkId, k -> new ArrayDeque<>());
        if (buf.size() >= evidenceBufferSize) buf.pollFirst();
        buf.addLast("[" + System.currentTimeMillis() + "] " + reason);
    }

    public List<String> getEvidence(String checkId) {
        return new ArrayList<>(evidence.getOrDefault(checkId, new ArrayDeque<>()));
    }

    // ---- environment / state flags (updated by listeners, read by checks) ----

    public boolean isOnIce() { return onIce; }
    public void setOnIce(boolean v) { onIce = v; }

    public boolean isInWater() { return inWater; }
    public void setInWater(boolean v) { inWater = v; }

    public boolean isOnLadder() { return onLadder; }
    public void setOnLadder(boolean v) { onLadder = v; }

    public boolean isInVehicle() { return inVehicle; }
    public void setInVehicle(boolean v) { inVehicle = v; }

    public boolean isGliding() { return gliding; }
    public void setGliding(boolean v) { gliding = v; }

    public void markTeleport(long tick) { lastTeleportTick = tick; }
    public boolean recentlyTeleported(long currentTick, long graceTicks) {
        return currentTick - lastTeleportTick <= graceTicks;
    }

    public void markKnockback(long tick) { lastKnockbackTick = tick; }
    public boolean recentlyKnockback(long currentTick, long graceTicks) {
        return currentTick - lastKnockbackTick <= graceTicks;
    }

    public void markVelocity(long tick) { lastVelocityTick = tick; }
    public boolean recentlyVelocity(long currentTick, long graceTicks) {
        return currentTick - lastVelocityTick <= graceTicks;
    }

    public int getPingMs() { return currentPingMs; }
    public void setPingMs(int ms) { currentPingMs = ms; }

    public long getLastMoveProcessTick() { return lastMoveProcessTick; }
    public void setLastMoveProcessTick(long tick) { lastMoveProcessTick = tick; }

    // ---- action timestamps for MultiActionA ----

    public void markBlockBreak(long tick) { lastBlockBreakTick = tick; }
    public long getLastBlockBreakTick() { return lastBlockBreakTick; }

    public void markBlockPlace(long tick) { lastBlockPlaceTick = tick; }
    public long getLastBlockPlaceTick() { return lastBlockPlaceTick; }

    public void markAttack(long tick) { lastAttackTick = tick; }
    public long getLastAttackTick() { return lastAttackTick; }

    // ---- item-use tracking for FastUseA ----

    public boolean isHandRaised() { return handRaised; }

    /** Call every move/state tick; automatically records the tick a raise began. */
    public void setHandRaised(boolean raised, long currentTick) {
        if (raised && !handRaised) {
            handRaiseStartTick = currentTick;
        } else if (!raised) {
            handRaiseStartTick = -1;
        }
        handRaised = raised;
    }

    public long getHandRaiseStartTick() { return handRaiseStartTick; }
}
