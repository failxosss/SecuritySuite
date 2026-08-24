package com.example.securitysuite;

import com.example.securitysuite.anticheat.PlayerData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlayerData has no Bukkit-server dependency for its violation/evidence
 * bookkeeping (only movement/rotation history touches Bukkit's Location
 * class, which is a plain data object and safe to exercise here), so it's
 * tested directly without MockBukkit.
 */
class PlayerDataTest {

    @Test
    void violationAccumulatesAndTracksPeak() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.addViolation("ReachA", 3.0, "test reason 1");
        data.addViolation("ReachA", 2.0, "test reason 2");

        assertEquals(5.0, data.getViolation("ReachA"), 0.0001);
        assertEquals(5.0, data.getPeakViolation("ReachA"), 0.0001);
    }

    @Test
    void decayReducesButNeverGoesNegative() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.addViolation("SpeedA", 1.0, "r");
        data.decay("SpeedA", 5.0); // decay more than current VL

        assertEquals(0.0, data.getViolation("SpeedA"), 0.0001);
    }

    @Test
    void peakSurvivesDecay() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.addViolation("FlyA", 10.0, "r");
        data.decay("FlyA", 4.0);

        assertEquals(6.0, data.getViolation("FlyA"), 0.0001);
        assertEquals(10.0, data.getPeakViolation("FlyA"), 0.0001);
    }

    @Test
    void resetViolationClearsSingleCheckOnly() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.addViolation("ReachA", 5.0, "r");
        data.addViolation("SpeedA", 3.0, "r");

        data.resetViolation("ReachA");

        assertEquals(0.0, data.getViolation("ReachA"), 0.0001);
        assertEquals(3.0, data.getViolation("SpeedA"), 0.0001);
    }

    @Test
    void resetAllClearsEverything() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.addViolation("ReachA", 5.0, "r");
        data.addViolation("SpeedA", 3.0, "r");

        data.resetAll();

        assertTrue(data.allViolations().isEmpty());
    }

    @Test
    void evidenceBufferIsBoundedBySize() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setEvidenceBufferSize(3);
        for (int i = 0; i < 10; i++) {
            data.addViolation("ReachA", 0.1, "evidence-" + i);
        }
        assertEquals(3, data.getEvidence("ReachA").size());
        // the buffer should contain the most recent entries, not the oldest
        assertTrue(data.getEvidence("ReachA").get(2).contains("evidence-9"));
    }

    @Test
    void knockbackGraceWindowRespectsTickDistance() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.markKnockback(100L);
        assertTrue(data.recentlyKnockback(105L, 10L));
        assertFalse(data.recentlyKnockback(120L, 10L));
    }
}
