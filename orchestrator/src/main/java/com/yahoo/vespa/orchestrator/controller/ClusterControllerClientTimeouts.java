// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.time.TimeBudget;

import java.time.Duration;
import java.util.Optional;

/**
 * Calculates various timeouts associated with a REST call from the Orchestrator to the Cluster Controller.
 *
 * <p>Timeout handling of HTTP messaging is fundamentally flawed in various Java implementations.
 * We would like to specify a max time for the whole operation (connect, send request, and receive response).
 * The Apache HTTP client library provides a way to set the connect timeout C
 * and read timeout R. So if the operation takes NR reads, and the writes takes TW time,
 * the theoretical max time is: T = C + R * NR + TW. With both NR and TW unknown, there's no way to
 * set a proper C and R.</p>
 *
 * @author hakonhall
 */
public class ClusterControllerClientTimeouts {
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
     * @param timeBudget  The time budget for a single logical call to the Cluster Controller.
     */
    public ClusterControllerClientTimeouts(TimeBudget timeBudget) {
        this.timeBudget = timeBudget;
    }

    public Duration getServerTimeoutOrThrow() {
        // readTimeout = DOWNSTREAM_OVERHEAD + serverTimeout
        TimeBudget serverBudget = readBudget().withReserved(DOWNSTREAM_OVERHEAD);
        if (serverBudget.timeLeft().get().compareTo(MIN_SERVER_TIMEOUT) < 0)
            throw new UncheckedTimeoutException("Timed out after " + timeBudget.originalTimeout().get());

        return serverBudget.timeLeft().get();
    }

    public Duration connectTimeout() {
        return CONNECT_TIMEOUT;
    }

    public TimeBudget readBudget() {
        // timeLeft = CONNECT_TIMEOUT + readTimeout
        return timeBudget.withReserved(CONNECT_TIMEOUT);
    }

}
