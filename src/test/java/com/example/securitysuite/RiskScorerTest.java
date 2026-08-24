package com.example.securitysuite;

import com.example.securitysuite.antivpn.RiskScorer;
import com.example.securitysuite.model.IpLookupResult;
import com.example.securitysuite.model.RiskAssessment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskScorerTest {

    private final RiskScorer.Weights weights = RiskScorer.Weights.defaults();
    private final RiskScorer.Thresholds thresholds = RiskScorer.Thresholds.defaults();

    @Test
    void cleanIpIsLowRisk() {
        IpLookupResult lookup = IpLookupResult.success("1.2.3.4", "test");
        RiskAssessment result = RiskScorer.score(lookup, weights, thresholds);
        assertEquals(0, result.getScore());
        assertEquals(RiskAssessment.Rating.LOW, result.getRating());
    }

    @Test
    void vpnAloneIsMedium() {
        IpLookupResult lookup = IpLookupResult.success("1.2.3.4", "test").setVpn(true);
        RiskAssessment result = RiskScorer.score(lookup, weights, thresholds);
        assertEquals(40, result.getScore());
        assertEquals(RiskAssessment.Rating.MEDIUM, result.getRating());
    }

    @Test
    void torAloneIsHigh() {
        IpLookupResult lookup = IpLookupResult.success("1.2.3.4", "test").setTor(true);
        RiskAssessment result = RiskScorer.score(lookup, weights, thresholds);
        assertEquals(70, result.getScore());
        assertEquals(RiskAssessment.Rating.HIGH, result.getRating());
    }

    @Test
    void combinedSignalsAreCriticalAndCappedAt100() {
        IpLookupResult lookup = IpLookupResult.success("1.2.3.4", "test")
                .setVpn(true).setProxy(true).setTor(true).setHosting(true).setKnownAbuse(true);
        RiskAssessment result = RiskScorer.score(lookup, weights, thresholds);
        assertEquals(100, result.getScore()); // 40+35+25+70+50 = 220, capped
        assertEquals(RiskAssessment.Rating.CRITICAL, result.getRating());
    }

    @Test
    void failedLookupFailsOpenAsLow() {
        IpLookupResult lookup = IpLookupResult.failed("1.2.3.4", "test");
        RiskAssessment result = RiskScorer.score(lookup, weights, thresholds);
        assertEquals(0, result.getScore());
        assertEquals(RiskAssessment.Rating.LOW, result.getRating());
        assertTrue(result.getReasons().contains("no-provider-data"));
    }

    @Test
    void datacenterAsnHeuristicMatchesKnownProviders() {
        assertTrue(RiskScorer.isDatacenterAsn("AS16509 Amazon.com, Inc."));
        assertTrue(RiskScorer.isDatacenterAsn("Hetzner Online GmbH"));
        assertFalse(RiskScorer.isDatacenterAsn("Deutsche Telekom AG"));
        assertFalse(RiskScorer.isDatacenterAsn(null));
    }

    @Test
    void thresholdBoundariesAreInclusiveOfLowerBound() {
        RiskScorer.Weights customWeights = new RiskScorer.Weights(30, 0, 0, 0, 0, 0);
        IpLookupResult lookup = IpLookupResult.success("1.2.3.4", "test").setVpn(true);
        RiskAssessment result = RiskScorer.score(lookup, customWeights, thresholds);
        assertEquals(30, result.getScore());
        assertEquals(RiskAssessment.Rating.MEDIUM, result.getRating()); // >= low(30) -> MEDIUM
    }
}
