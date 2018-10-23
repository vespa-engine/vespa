package com.yahoo.vespa.jaxrs.client;

import java.time.Duration;

/**
 * Legacy defaults for timeouts.
 *
 * Clients should instead define their own JaxRsTimeouts tailored to their use-case.
 *
 * @author hakon
 */
// Immutable
public class LegacyJaxRsTimeouts implements JaxRsTimeouts {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Override
    public Duration getConnectTimeout() {
        return CONNECT_TIMEOUT;
    }

    @Override
    public Duration getReadTimeout() {
        return READ_TIMEOUT;
    }
}
