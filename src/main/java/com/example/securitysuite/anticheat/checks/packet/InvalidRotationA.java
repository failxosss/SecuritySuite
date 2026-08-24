package com.example.securitysuite.anticheat.checks.packet;

import com.example.securitysuite.anticheat.Check;
import com.example.securitysuite.anticheat.PlayerData;
import com.example.securitysuite.model.CheckResult;
import org.bukkit.entity.Player;

/**
 * The protocol only allows pitch in [-90, 90]; anything outside that range
 * is not achievable by a vanilla client and indicates a modified client
 * sending raw/forged rotation packets (commonly paired with NoRotation/
 * Freecam-style cheats). Also flags NaN/Infinite values, which some
 * broken or malicious clients send and which can otherwise crash
 * downstream trig-based checks (AimA, KillAuraA).
 */
public class InvalidRotationA implements Check {

    @Override
    public String id() { return "InvalidRotationA"; }

    @Override
    public String category() { return "packet"; }

    @Override
    public CheckResult evaluate(Player player, PlayerData data) {
        return CheckResult.clean(); // see overload
    }

    public CheckResult evaluate(Player player, PlayerData data, float yaw, float pitch) {
        if (Float.isNaN(yaw) || Float.isNaN(pitch) || Float.isInfinite(yaw) || Float.isInfinite(pitch)) {
            return CheckResult.flag(1.0, 10.0, "NaN/Infinite rotation received");
        }
        if (pitch < -90.01f || pitch > 90.01f) {
            return CheckResult.flag(1.0, 8.0, "pitch " + pitch + " outside valid [-90,90] range");
        }
        return CheckResult.clean();
    }
}
