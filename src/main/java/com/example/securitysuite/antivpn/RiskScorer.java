package com.example.securitysuite.antivpn;

import com.example.securitysuite.model.IpLookupResult;
import com.example.securitysuite.model.RiskAssessment;
import com.example.securitysuite.model.RiskAssessment.Rating;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure risk-scoring function with no Bukkit/plugin dependency, so it can
 * be unit tested directly (see RiskScorerTest) without a running server.
 * AntiVPNManager delegates to this class; it should never itself contain
 * scoring logic that duplicates what's here.
 */
public final class RiskScorer {

    public record Weights(int vpn, int proxy, int hosting, int tor, int datacenterAsn, int knownAbuseIp) {
        public static Weights defaults() {
            return new Weights(40, 35, 25, 70, 20, 50);
        }
    }

    public record Thresholds(int low, int medium, int high) {
        public static Thresholds defaults() {
            return new Thresholds(30, 60, 80);
        }
    }

    private RiskScorer() {}

    public static RiskAssessment score(IpLookupResult lookup, Weights weights, Thresholds thresholds) {
        if (lookup == null || lookup.isLookupFailed()) {
            return new RiskAssessment(lookup, 0, Rating.LOW, List.of("no-provider-data"));
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (lookup.isVpn()) { score += weights.vpn(); reasons.add("vpn"); }
        if (lookup.isProxy()) { score += weights.proxy(); reasons.add("proxy"); }
        if (lookup.isHosting()) { score += weights.hosting(); reasons.add("hosting"); }
        if (lookup.isTor()) { score += weights.tor(); reasons.add("tor"); }
        if (lookup.isKnownAbuse()) { score += weights.knownAbuseIp(); reasons.add("known-abuse"); }
        if (isDatacenterAsn(lookup.getAsn())) { score += weights.datacenterAsn(); reasons.add("datacenter-asn"); }

        score = Math.min(100, score);

        Rating rating;
        if (score >= thresholds.high()) rating = Rating.CRITICAL;
        else if (score >= thresholds.medium()) rating = Rating.HIGH;
        else if (score >= thresholds.low()) rating = Rating.MEDIUM;
        else rating = Rating.LOW;

        return new RiskAssessment(lookup, score, rating, reasons);
    }

    public static boolean isDatacenterAsn(String asn) {
        if (asn == null) return false;
        String lower = asn.toLowerCase(Locale.ROOT);
        return lower.contains("amazon") || lower.contains("aws") || lower.contains("google cloud")
                || lower.contains("microsoft") || lower.contains("azure") || lower.contains("digitalocean")
                || lower.contains("ovh") || lower.contains("hetzner") || lower.contains("linode")
                || lower.contains("vultr") || lower.contains("hosting");
    }
}
