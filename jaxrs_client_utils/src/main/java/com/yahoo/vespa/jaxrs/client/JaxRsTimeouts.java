package com.yahoo.vespa.jaxrs.client;

import java.time.Duration;

/**
 * @author hakonhall
 */
public interface JaxRsTimeouts {
    /**
     * The connect timeout, which must be at least 1ms.
     *
     * Throws com.google.common.util.concurrent.UncheckedTimeoutException on timeout.
     */
    Duration getConnectTimeoutOrThrow();

    /**
     * The read timeout, which must be at least 1ms.
     *
     * Throws com.google.common.util.concurrent.UncheckedTimeoutException on timeout.
     */
    Duration getReadTimeoutOrThrow();
}
