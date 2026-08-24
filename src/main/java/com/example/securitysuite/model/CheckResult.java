package com.example.securitysuite.model;

/**
 * Immutable result returned by every AntiCheat check invocation.
 * Checks never punish directly - they only report a suspicion level,
 * confidence and human-readable reason. The ViolationManager and
 * PunishmentManager decide what happens next.
 */
public final class CheckResult {

    public static final CheckResult CLEAN = new CheckResult(false, 0.0, 0.0, "clean");

    private final boolean suspicious;
    private final double confidence; // 0.0 - 1.0
    private final double violation;  // VL points to add if suspicious
    private final String reason;

    public CheckResult(boolean suspicious, double confidence, double violation, String reason) {
        this.suspicious = suspicious;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.violation = Math.max(0.0, violation);
        this.reason = reason == null ? "" : reason;
    }

    public static CheckResult flag(double confidence, double violation, String reason) {
        return new CheckResult(true, confidence, violation, reason);
    }

    public static CheckResult clean() {
        return CLEAN;
    }

    public boolean isSuspicious() {
        return suspicious;
    }

    public double getConfidence() {
        return confidence;
    }

    public double getViolation() {
        return violation;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "CheckResult{suspicious=" + suspicious + ", confidence=" + confidence
                + ", violation=" + violation + ", reason='" + reason + "'}";
    }
}
