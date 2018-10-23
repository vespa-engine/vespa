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
 * <p>The various timeouts is set according to the following considerations:</p>
 *
 * <ol>
 *     <li>Some time is reserved for the execution in this process, e.g. execution leading to the REST call,
 *     handling of the response, exception handling, etc, such that we can finish processing this request
 *     before the {@link #timeBudget} deadline. This is typically in the order of ms.</li>
 *     <li>A timeout will be passed to the Cluster Controller backend. We'll give a timeout such that if one
 *     CC times out, the next CC will be given exactly the same timeout. This may or may not be a good strategy:
 *     (A) There's typically a 3rd CC. But if the first 2 fails with timeout, the chance the last is OK
 *     is negligible. (B) If picking the CC is random, then giving the full timeout to the first
 *     should be sufficient since a later retry will hit the healthy CC. (C) Because we have been using
 *     DROP in networking rules, clients may time out (host out of app or whatever). This would suggest
 *     allowing more than 1 full request.</li>
 *     <li>The timeout passed to the CC backend should be such that if it honors that, the Orchestrator
 *     should not time out. This means some kernel and network overhead should be subtracted from the timeout
 *     passed to the CC.</li>
 *     <li>We're only able to set the connect and read/write timeouts(!) Since we're communicating within
 *     data center, assume connections are in the order of ms, while a single read may stall close up to the CC
 *     timeout.</li>
 * </ol>
 *
 * @author hakonhall
 */
public class ClusterControllerClientTimeouts implements JaxRsTimeouts {
    // In data center connect timeout
    static final Duration CONNECT_TIMEOUT = Duration.ofMillis(50);
    // Per call overhead
    static final Duration IN_PROCESS_OVERHEAD_PER_CALL = Duration.ofMillis(50);
    // In data center kernel and network overhead.
    static final Duration NETWORK_OVERHEAD_PER_CALL = CONNECT_TIMEOUT;
    // Minimum time reserved for post-RPC processing to finish BEFORE the deadline, including ZK write.
    static final Duration IN_PROCESS_OVERHEAD = Duration.ofMillis(100);
    // Number of JAX-RS RPC calls to account for within the time budget.
    static final int NUM_CALLS = 2;
    // Minimum server-side timeout
    static final Duration MIN_SERVER_TIMEOUT = Duration.ofMillis(10);

    private final String clusterName;
    private final TimeBudget timeBudget;
    private final Duration maxClientTimeout;

    /**
     * Creates a timeouts instance.
     *
     * The {@link #timeBudget} SHOULD be the time budget for a single logical call to the Cluster Controller.
     * A logical call to CC may in fact call the CC several times, if the first onces are down and/or not
     * the master.
     *
     * @param clusterName The name of the content cluster this request is for.
     * @param timeBudget  The time budget for a single logical call to the the Cluster Controller.
     */
    public ClusterControllerClientTimeouts(String clusterName, TimeBudget timeBudget) {
        this.clusterName = clusterName;
        this.timeBudget = timeBudget;

        // timeLeft = inProcessOverhead + numCalls * clientTimeout
        maxClientTimeout = timeBudget.originalTimeout().get().minus(IN_PROCESS_OVERHEAD).dividedBy(NUM_CALLS);
    }

    @Override
    public Duration getConnectTimeout() {
        return CONNECT_TIMEOUT;
    }

    @Override
    public Duration getReadTimeout() {
        Duration timeLeft = timeBudget.timeLeft().get();
        if (timeLeft.toMillis() <= 0) {
            throw new UncheckedTimeoutException("Exceeded the timeout " + timeBudget.originalTimeout().get() +
                    " against content cluster '" + clusterName + "' by " + timeLeft.negated());
        }

        Duration clientTimeout = min(timeLeft, maxClientTimeout);
        verifyPositive(timeLeft, maxClientTimeout);

        // clientTimeout = overheadPerCall + connectTimeout + readTimeout
        Duration readTimeout = clientTimeout.minus(IN_PROCESS_OVERHEAD_PER_CALL).minus(CONNECT_TIMEOUT);
        verifyPositive(timeLeft, readTimeout);

        return readTimeout;
    }

    public Duration getServerTimeout() {
        // readTimeout = networkOverhead + serverTimeout
        Duration serverTimeout = getReadTimeout().minus(NETWORK_OVERHEAD_PER_CALL);
        if (serverTimeout.toMillis() < MIN_SERVER_TIMEOUT.toMillis()) {
            throw new UncheckedTimeoutException("Server would be given too little time to complete: " +
                    serverTimeout + ". Original timeout was " + timeBudget.originalTimeout().get());
        }

        return serverTimeout;
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    private void verifyLargerThan(Duration timeLeft, Duration availableDuration) {
        if (availableDuration.toMillis() <= 0) {
            throw new UncheckedTimeoutException("Too little time left (" + timeLeft +
                    ") to call content cluster '" + clusterName +
                    "', original timeout was " + timeBudget.originalTimeout().get());
        }
    }

    private void verifyPositive(Duration timeLeft, Duration duration) { verifyLargerThan(timeLeft, duration); }
}
