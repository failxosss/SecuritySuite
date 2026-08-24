package com.example.securitysuite;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * Very lightweight rolling counters used by /security performance and
 * /security debug. Deliberately avoids any heavy instrumentation (no
 * per-call stack sampling) so that measuring performance doesn't itself
 * become a performance problem.
 */
public class PerformanceStats {

    private final Map<String, AtomicLong> checkTimeNanos = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> checkInvocations = new ConcurrentHashMap<>();
    private final AtomicLong apiLookupsTotalMs = new AtomicLong();
    private final AtomicLong apiLookupsCount = new AtomicLong();
    private final AtomicLong dbQueryTotalMs = new AtomicLong();
    private final AtomicLong dbQueryCount = new AtomicLong();

    public void recordCheckTime(String checkId, long nanos) {
        checkTimeNanos.computeIfAbsent(checkId, k -> new AtomicLong()).addAndGet(nanos);
        checkInvocations.computeIfAbsent(checkId, k -> new AtomicLong()).incrementAndGet();
    }

    public double averageCheckTimeMs(String checkId) {
        long total = checkTimeNanos.getOrDefault(checkId, new AtomicLong()).get();
        long count = checkInvocations.getOrDefault(checkId, new AtomicLong()).get();
        if (count == 0) return 0;
        return (total / (double) count) / 1_000_000.0;
    }

    public void recordApiLookup(long ms) {
        apiLookupsTotalMs.addAndGet(ms);
        apiLookupsCount.incrementAndGet();
    }

    public double averageApiLookupMs() {
        long count = apiLookupsCount.get();
        return count == 0 ? 0 : apiLookupsTotalMs.get() / (double) count;
    }

    public void recordDbQuery(long ms) {
        dbQueryTotalMs.addAndGet(ms);
        dbQueryCount.incrementAndGet();
    }

    public double averageDbQueryMs() {
        long count = dbQueryCount.get();
        return count == 0 ? 0 : dbQueryTotalMs.get() / (double) count;
    }

    public Map<String, Long> checkInvocationCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        checkInvocations.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
}
