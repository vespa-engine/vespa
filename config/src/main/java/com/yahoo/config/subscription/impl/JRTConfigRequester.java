// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.text.internal.SnippetGenerator;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequest;
import com.yahoo.vespa.config.protocol.JRTConfigRequestFactory;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

/**
 * Requests configs using RPC, and acts as the callback target.
 * It uses the {@link JRTConfigSubscription} and {@link JRTClientConfigRequest}
 * as context, and puts the request objects on a queue on the subscription,
 * for handling by the user thread.
 *
 * @author Vegard Havdal
 */
public class JRTConfigRequester implements RequestWaiter {

    private static final Logger log = Logger.getLogger(JRTConfigRequester.class.getName());
    public static final ConfigSourceSet defaultSourceSet = ConfigSourceSet.createDefault();
    private static final JRTManagedConnectionPools managedPool = new JRTManagedConnectionPools();
    private static final int TRACELEVEL = 6;
    private static final Duration delayBetweenWarnings = Duration.ofSeconds(60);
    static final float randomFraction = 0.2f;
    /* Time to be added to server timeout to create client timeout. This is the time allowed for the server to respond after serverTimeout has elapsed. */
    private static final Duration additionalTimeForClientTimeout = Duration.ofSeconds(10);

    private final TimingValues timingValues;
    private final ScheduledThreadPoolExecutor scheduler;

    private final ConnectionPool connectionPool;
    private final ConfigSourceSet configSourceSet;

    private Instant timeForLastLogWarning;
    private int failures = 0;
    private volatile boolean closed = false;

    /**
     * Returns a new requester
     *
     * @param connectionPool the connectionPool this requester should use
     * @param timingValues   timeouts and delays used when sending JRT config requests
     */
    JRTConfigRequester(ConfigSourceSet configSourceSet, ScheduledThreadPoolExecutor scheduler,
                       ConnectionPool connectionPool, TimingValues timingValues) {
        this.configSourceSet = configSourceSet;
        this.scheduler = scheduler;
        this.connectionPool = connectionPool;
        this.timingValues = timingValues;
        // Adjust so that we wait 5 seconds with logging warning in case there are some errors just when starting up
        timeForLastLogWarning = Instant.now().minus(delayBetweenWarnings).plus(Duration.ofSeconds(5));
    }

    /**
     * Only for testing
     */
    public JRTConfigRequester(ConnectionPool connectionPool, TimingValues timingValues) {
        this(null, new ScheduledThreadPoolExecutor(1), connectionPool, timingValues);
    }

    public static JRTConfigRequester create(ConfigSourceSet sourceSet, TimingValues timingValues) {
        return managedPool.acquire(sourceSet, timingValues);
    }

    /**
     * Requests the config for the {@link com.yahoo.config.ConfigInstance} on the given {@link ConfigSubscription}
     *
     * @param sub a subscription
     */
    public <T extends ConfigInstance> void request(JRTConfigSubscription<T> sub) {
        JRTClientConfigRequest req = JRTConfigRequestFactory.createFromSub(sub);
        doRequest(sub, req);
    }

    private <T extends ConfigInstance> void doRequest(JRTConfigSubscription<T> sub, JRTClientConfigRequest req) {
        Connection connection = connectionPool.getCurrent();
        Request request = req.getRequest();
        request.setContext(new RequestContext(sub, req, connection));
        if (!req.validateParameters())
            throw new ConfigurationRuntimeException("Error in parameters for config request: " + req);

        Duration jrtClientTimeout = getClientTimeout(req);
        log.log(FINE, () -> "Requesting config for " + sub + " on connection " + connection
                + " with client timeout " + jrtClientTimeout +
                (log.isLoggable(FINEST) ? (",defcontent=" + req.getDefContent().asString()) : ""));
        connection.invokeAsync(request, jrtClientTimeout, this);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleRequestDone(Request req) {
        JRTConfigSubscription<ConfigInstance> sub = null;
        try {
            RequestContext context = (RequestContext) req.getContext();
            sub = context.sub;
            doHandle(sub, context.jrtReq, context.connection);
        } catch (RuntimeException e) {
            if (sub != null) {
                // Sets this field, it will get thrown from the user thread
                sub.setException(e);
            } else {
                // Very unlikely
                log.log(SEVERE, "Failed to get subscription object from JRT config callback: " +
                        Exceptions.toMessageString(e));
            }
        }
    }

    private void doHandle(JRTConfigSubscription<ConfigInstance> sub, JRTClientConfigRequest jrtReq, Connection connection) {
        if (sub.isClosed()) return; // Avoid error messages etc. after closing

        boolean validResponse = jrtReq.validateResponse();
        log.log(FINE, () -> "Request callback " + (validResponse ? "valid" : "invalid") + ". Req: " + jrtReq + "\nSpec: " + connection);
        Trace trace = jrtReq.getResponseTrace();
        trace.trace(TRACELEVEL, "JRTConfigRequester.doHandle()");
        log.log(FINEST, () -> trace.toString());
        if (validResponse)
            handleOKRequest(jrtReq, sub);
        else
            handleFailedRequest(jrtReq, sub, connection);
    }

    private void logError(JRTClientConfigRequest jrtReq, Connection connection) {
        switch (jrtReq.errorCode()) {
            case com.yahoo.jrt.ErrorCode.CONNECTION:
                log.log(FINE, () -> "Request callback failed: " + jrtReq.errorMessage() +
                        "\nConnection spec: " + connection);
                break;
            case ErrorCode.APPLICATION_NOT_LOADED:
            case ErrorCode.UNKNOWN_VESPA_VERSION:
                logWarning(jrtReq, connection);
                break;
            default:
                log.log(WARNING, "Request callback failed. Req: " + jrtReq + "\nSpec: " + connection.getAddress() +
                        " . Req error message: " + jrtReq.errorMessage());
                break;
        }
    }

    private void logWarning(JRTClientConfigRequest jrtReq, Connection connection) {
        if ( ! closed && timeForLastLogWarning.isBefore(Instant.now().minus(delayBetweenWarnings))) {
            log.log(WARNING, "Request callback failed: " + ErrorCode.getName(jrtReq.errorCode()) +
                    ". Connection spec: " + connection.getAddress() +
                    ", error message: " + jrtReq.errorMessage());
            timeForLastLogWarning = Instant.now();
        }
    }

    private void handleFailedRequest(JRTClientConfigRequest jrtReq, JRTConfigSubscription<ConfigInstance> sub, Connection connection) {
        logError(jrtReq, connection);

        connectionPool.switchConnection(connection);
        if (failures < 10)
            failures++;
        long delay = calculateFailedRequestDelay(failures, timingValues);
        log.log(FINE, () -> "Request for config " + jrtReq.getShortDescription() + "' failed with error code " +
                jrtReq.errorCode() + " (" + jrtReq.errorMessage() + "), scheduling new request " +
                " in " + delay + " ms");
        scheduleNextRequest(jrtReq, sub, delay, calculateErrorTimeout());
    }

    static long calculateFailedRequestDelay(int failures, TimingValues timingValues) {
        long delay = timingValues.getFixedDelay() * (long)Math.pow(2, failures);
        delay = Math.max(timingValues.getFixedDelay(), Math.min(60_000, delay)); // between timingValues.getFixedDelay() and 60 seconds
        delay = timingValues.getPlusMinusFractionRandom(delay, randomFraction);

        return delay;
    }

    private long calculateErrorTimeout() {
        return timingValues.getPlusMinusFractionRandom(timingValues.getErrorTimeout(), randomFraction);
    }

    private void handleOKRequest(JRTClientConfigRequest jrtReq, JRTConfigSubscription<ConfigInstance> sub) {
        failures = 0;
        sub.setLastCallBackOKTS(Instant.now());
        log.log(FINE, () -> "OK response received in handleOkRequest: " + jrtReq);
        if (jrtReq.hasUpdatedGeneration()) {
            sub.updateConfig(jrtReq);
        } else if (jrtReq.hasUpdatedConfig()) {
            SnippetGenerator generator = new SnippetGenerator();
            int sizeHint = 500;
            String config = jrtReq.getNewPayload().toString();
            log.log(Level.WARNING, "Config " + jrtReq.getConfigKey() + " has changed without a change in config generation: generation " +
                    jrtReq.getNewGeneration() + ", config: " + generator.makeSnippet(config, sizeHint) +
                    ". This might happen when a newly upgraded config server responds with different config when bootstrapping  " +
                    " (changes to code generating config that are different between versions) or non-deterministic config generation" +
                    " (e.g. when using collections with non-deterministic iteration order)");
        }
        scheduleNextRequest(jrtReq, sub, calculateSuccessDelay(), calculateSuccessTimeout());
    }

    private long calculateSuccessTimeout() {
        return timingValues.getPlusMinusFractionRandom(timingValues.getSuccessTimeout(), randomFraction);
    }

    private long calculateSuccessDelay() {
        return timingValues.getPlusMinusFractionRandom(timingValues.getFixedDelay(), randomFraction);
    }

    private void scheduleNextRequest(JRTClientConfigRequest jrtReq, JRTConfigSubscription<?> sub, long delay, long timeout) {
        long delayBeforeSendingRequest = (delay < 0) ? 0 : delay;
        JRTClientConfigRequest jrtReqNew = jrtReq.nextRequest(timeout);
        log.log(FINEST, () -> timingValues.toString());
        log.log(FINE, () -> "Scheduling new request " + delayBeforeSendingRequest + " millis from now for " + jrtReqNew.getConfigKey());
        scheduler.schedule(new GetConfigTask(jrtReqNew, sub), delayBeforeSendingRequest, TimeUnit.MILLISECONDS);
    }

    /**
     * Task that can be scheduled in a timer for executing a getConfig request
     */
    private class GetConfigTask implements Runnable {
        private final JRTClientConfigRequest jrtReq;
        private final JRTConfigSubscription<?> sub;

        GetConfigTask(JRTClientConfigRequest jrtReq, JRTConfigSubscription<?> sub) {
            this.jrtReq = jrtReq;
            this.sub = sub;
        }

        public void run() {
            doRequest(sub, jrtReq);
        }
    }

    public void close() {
        closed = true;
        if (configSourceSet != null) {
            managedPool.release(configSourceSet);
        }
    }

    @SuppressWarnings("rawtypes")
    private static class RequestContext {
        final JRTConfigSubscription sub;
        final JRTClientConfigRequest jrtReq;
        final Connection connection;

        private RequestContext(JRTConfigSubscription sub, JRTClientConfigRequest jrtReq, Connection connection) {
            this.sub = sub;
            this.jrtReq = jrtReq;
            this.connection = connection;
        }
    }

    int getFailures() { return failures; }

    // TODO: Should be package private, used in integrationtest.rb in system tests
    public ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    private Duration getClientTimeout(JRTClientConfigRequest request) {
        return Duration.ofMillis(request.getTimeout()).plus(additionalTimeForClientTimeout);
    }
}
