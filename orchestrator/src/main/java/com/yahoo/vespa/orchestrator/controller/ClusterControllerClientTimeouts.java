// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.time.TimeBudget;
import com.yahoo.vespa.jaxrs.client.JaxRsTimeouts;

import java.time.Duration;

/**
 * Calculates various timeouts associated with a REST call from the Orchestrator to the Cluster Controller.
 *
 * <p>Timeout handling of HTTP messaging is fundamentally flawed in various Java implementations.
 * We would like to specify a max time for the whole operation (connect, send request, and receive response).
 * Jersey JAX-RS implementation and the Apache HTTP client library provides a way to set the connect timeout C
 * and read timeout R. So if the operation takes NR reads, and the writes takes TW time,
 * the theoretical max time is: T = C + R * NR + TW. With both NR and TW unknown, there's no way to
 * set a proper C and R.</p>
 *
 * @author hakonhall
 */
public class ClusterControllerClientTimeouts implements JaxRsTimeouts {
    static final Duration CONNECT_TIMEOUT = Duration.ofMillis(100);
    // Time reserved to guarantee that even though the server application honors a server timeout S,
    // some time will pass before the server sees the timeout, and after it has returned.
    static final Duration DOWNSTREAM_OVERHEAD = Duration.ofMillis(300);
    // Minimum server-side timeout
    static final Duration MIN_SERVER_TIMEOUT = Duration.ofMillis(100);

    private final TimeBudget timeBudget;

    /**
     * Creates a timeouts instance.
     *
     * The {@link #timeBudget} SHOULD be the time budget for a single logical call to the Cluster Controller.
     * A logical call to CC may in fact call the CC several times, if the first ones are down and/or not
     * the master.
     *
     * @param timeBudget  The time budget for a single logical call to the the Cluster Controller.
     */
    public ClusterControllerClientTimeouts(TimeBudget timeBudget) {
        this.timeBudget = timeBudget;
    }

    @Override
    public Duration getConnectTimeoutOrThrow() {
        return CONNECT_TIMEOUT;
    }

    @Override
    public Duration getReadTimeoutOrThrow() {
        Duration timeLeft = timeBudget.timeLeft().get();

        // timeLeft = CONNECT_TIMEOUT + readTimeout
        Duration readTimeout = timeLeft.minus(CONNECT_TIMEOUT);

        if (readTimeout.toMillis() <= 0) {
            throw new UncheckedTimeoutException("Timed out after " + timeBudget.originalTimeout().get());
        }

        return readTimeout;
    }

    public Duration getServerTimeoutOrThrow() {
        // readTimeout = DOWNSTREAM_OVERHEAD + serverTimeout
        Duration serverTimeout = getReadTimeoutOrThrow().minus(DOWNSTREAM_OVERHEAD);

        if (serverTimeout.toMillis() < MIN_SERVER_TIMEOUT.toMillis()) {
            throw new UncheckedTimeoutException("Timed out after " + timeBudget.originalTimeout().get());
        }

        return serverTimeout;
    }

}
