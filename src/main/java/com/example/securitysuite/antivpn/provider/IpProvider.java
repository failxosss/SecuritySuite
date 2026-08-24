package com.example.securitysuite.antivpn.provider;

import com.example.securitysuite.model.IpLookupResult;

import java.util.concurrent.CompletableFuture;

/**
 * A source of IP intelligence. Implementations MUST perform their network
 * I/O off the main server thread and must never throw - all failure modes
 * are represented via {@link IpLookupResult#failed(String, String)} so the
 * AntiVPNManager can fall through to the next configured provider.
 */
public interface IpProvider {

    /** Unique lowercase id matching the key used in config.yml providers.order */
    String id();

    boolean isEnabled();

    /**
     * Performs the lookup asynchronously. Implementations must apply their
     * own timeout (see config `timeout-ms`) and complete the future with a
     * failed result rather than leaving it hanging or throwing.
     */
    CompletableFuture<IpLookupResult> lookup(String ip);
}
