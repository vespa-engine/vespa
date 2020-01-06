// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import java.time.Duration;

/**
 * Legacy defaults for timeouts.
 *
 * Clients should instead define their own JaxRsTimeouts tailored to their use-case.
 *
 * @author hakonhall
 */
// Immutable
public class LegacyJaxRsTimeouts implements JaxRsTimeouts {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Override
    public Duration getConnectTimeoutOrThrow() {
        return CONNECT_TIMEOUT;
    }

    @Override
    public Duration getReadTimeoutOrThrow() {
        return READ_TIMEOUT;
    }
}
