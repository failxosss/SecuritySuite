package com.example.securitysuite;

import com.example.securitysuite.model.CheckResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckResultTest {

    @Test
    void cleanResultIsNotSuspicious() {
        CheckResult result = CheckResult.clean();
        assertFalse(result.isSuspicious());
        assertEquals(0.0, result.getViolation());
        assertEquals(0.0, result.getConfidence());
    }

    @Test
    void confidenceIsClampedToUnitRange() {
        CheckResult tooHigh = CheckResult.flag(5.0, 1.0, "overshoot");
        CheckResult tooLow = CheckResult.flag(-2.0, 1.0, "undershoot");

        assertEquals(1.0, tooHigh.getConfidence(), 0.0001);
        assertEquals(0.0, tooLow.getConfidence(), 0.0001);
    }

    @Test
    void violationCannotBeNegative() {
        CheckResult negative = CheckResult.flag(0.5, -3.0, "should clamp to zero");
        assertEquals(0.0, negative.getViolation(), 0.0001);
    }

    @Test
    void nullReasonBecomesEmptyString() {
        CheckResult result = new CheckResult(true, 0.5, 1.0, null);
        assertEquals("", result.getReason());
    }
}
