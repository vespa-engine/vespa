package com.yahoo.vespa.jaxrs.client;

import java.time.Duration;

/**
 * @author hakonhall
 */
public interface JaxRsTimeouts {
    /**
     * The connect timeout.
     *
     * Throws com.google.common.util.concurrent.UncheckedTimeoutException on timeout.
     */
    Duration getConnectTimeout();

    /**
     * The read timeout.
     *
     * Throws com.google.common.util.concurrent.UncheckedTimeoutException on timeout.
     */
    Duration getReadTimeout();
}
