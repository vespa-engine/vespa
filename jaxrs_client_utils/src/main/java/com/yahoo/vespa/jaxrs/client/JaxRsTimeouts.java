package com.yahoo.vespa.jaxrs.client;

import java.time.Duration;

/**
 * @author hakon
 */
public interface JaxRsTimeouts {
    /**
     * Prepare for a single imminent JAX-RS call.
     *
     * The instance may typically set and validate the connect- and read- timeouts to be called soon,
     * or throw a timeout exception if there's insufficient time for a remote call.
     *
     * @throws RuntimeException on timeout
     */
    void prepareForImmediateJaxRsCall();

    /** The connect timeout. */
    Duration getConnectTimeout();

    /** The read timeout. */
    Duration getReadTimeout();
}
