package com.example.securitysuite.model;

import java.util.List;

/**
 * Final AntiVPN verdict for a player/IP: the raw lookup, the computed
 * score, the letter rating and which scoring reasons contributed.
 */
public final class RiskAssessment {

    public enum Rating { LOW, MEDIUM, HIGH, CRITICAL }

    private final IpLookupResult lookup;
    private final int score;
    private final Rating rating;
    private final List<String> reasons;

    public RiskAssessment(IpLookupResult lookup, int score, Rating rating, List<String> reasons) {
        this.lookup = lookup;
        this.score = score;
        this.rating = rating;
        this.reasons = reasons;
    }

    public IpLookupResult getLookup() { return lookup; }
    public int getScore() { return score; }
    public Rating getRating() { return rating; }
    public List<String> getReasons() { return reasons; }
}
