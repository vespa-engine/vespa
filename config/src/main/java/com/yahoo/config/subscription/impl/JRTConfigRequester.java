// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequest;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.JRTConfigRequestFactory;
import com.yahoo.vespa.config.protocol.Trace;

/**
 * This class fetches config payload using JRT, and acts as the callback target.
 * It uses the {@link JRTConfigSubscription} and {@link JRTClientConfigRequest}
 * as context, and puts the requests objects on a queue on the subscription,
 * for handling by the user thread.
 *
 * @author Vegard Havdal
 */
public class JRTConfigRequester implements RequestWaiter {

    private static final Logger log = Logger.getLogger(JRTConfigRequester.class.getName());
    public static final ConfigSourceSet defaultSourceSet = ConfigSourceSet.createDefault();
    private static final int TRACELEVEL = 6;
    private final TimingValues timingValues;
    private int fatalFailures = 0; // independent of transientFailures
    private int transientFailures = 0;  // independent of fatalFailures
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, new JRTSourceThreadFactory());
    private Instant suspendWarningLogged = Instant.MIN;
    private Instant noApplicationWarningLogged = Instant.MIN;
    private static final Duration delayBetweenWarnings = Duration.ofSeconds(60);
    private final ConnectionPool connectionPool;
    static final float randomFraction = 0.2f;
    /* Time to be added to server timeout to create client timeout. This is the time allowed for the server to respond after serverTimeout has elapsed. */
    private static final Double additionalTimeForClientTimeout = 5.0;

    private static final SimpleDateFormat yyyyMMddz;

    static {
        yyyyMMddz = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        yyyyMMddz.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * Returns a new requester
     * @param connectionPool The connectionPool to use
     * @param timingValues The timing values
     * @return new requester object
     */
    public static JRTConfigRequester get(ConnectionPool connectionPool, TimingValues timingValues) {
        return new JRTConfigRequester(connectionPool, timingValues);
    }

    /**
     * New requester
     *  @param connectionPool the connectionPool this requester should use
     * @param timingValues timeouts and delays used when sending JRT config requests
     */
    JRTConfigRequester(ConnectionPool connectionPool, TimingValues timingValues) {
        this.connectionPool = connectionPool;
        this.timingValues = timingValues;
    }

    /**
     * Requests the config for the {@link com.yahoo.config.ConfigInstance} on the given {@link ConfigSubscription}
     *
     * @param sub a subscription
     */
    public <T extends ConfigInstance> void request(JRTConfigSubscription<T> sub) {
        JRTClientConfigRequest req = JRTConfigRequestFactory.createFromSub(sub);
        doRequest(sub, req, timingValues.getSubscribeTimeout());
    }

    private <T extends ConfigInstance> void doRequest(JRTConfigSubscription<T> sub,
                                                      JRTClientConfigRequest req, long timeout) {
        com.yahoo.vespa.config.Connection connection = connectionPool.getCurrent();
        req.getRequest().setContext(new RequestContext(sub, req, connection));
        boolean reqOK = req.validateParameters();
        if (!reqOK) throw new ConfigurationRuntimeException("Error in parameters for config request: " + req);
        // Add some time to the timeout, we never want it to time out in JRT during normal operation
        double jrtClientTimeout = getClientTimeout(timeout);
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Requesting config for " + sub + " on connection " + connection + " with RPC timeout " + jrtClientTimeout + ",defcontent=" +
                    req.getDefContent().asString());
        }
        connection.invokeAsync(req.getRequest(), jrtClientTimeout, this);
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
                log.log(Level.SEVERE, "Failed to get subscription object from JRT config callback: " +
                        Exceptions.toMessageString(e));
            }
        }
    }

    private void doHandle(JRTConfigSubscription<ConfigInstance> sub, JRTClientConfigRequest jrtReq, Connection connection) {
        if (sub.getState() == ConfigSubscription.State.CLOSED) return; // Avoid error messages etc. after closing
        boolean validResponse = jrtReq.validateResponse();
        Trace trace = jrtReq.getResponseTrace();
        trace.trace(TRACELEVEL, "JRTConfigRequester.doHandle()");
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, trace.toString());
        }
        if (validResponse) {
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "Request callback, OK. Req: " + jrtReq + "\nSpec: " + connection);
            }
            handleOKRequest(jrtReq, sub, connection);
        } else {
            logWhenErrorResponse(jrtReq, connection);
            handleFailedRequest(jrtReq, sub, connection);
        }
    }

    private void logWhenErrorResponse(JRTClientConfigRequest jrtReq, Connection connection) {
        switch (jrtReq.errorCode()) {
            case com.yahoo.jrt.ErrorCode.CONNECTION:
                log.log(LogLevel.DEBUG, "Request callback failed: " + jrtReq.errorMessage() +
                        "\nConnection spec: " + connection);
                break;
            case ErrorCode.APPLICATION_NOT_LOADED:
            case ErrorCode.UNKNOWN_VESPA_VERSION:
                if (noApplicationWarningLogged.isBefore(Instant.now().minus(delayBetweenWarnings))) {
                    log.log(LogLevel.WARNING, "Request callback failed: " + ErrorCode.getName(jrtReq.errorCode()) +
                            ". Connection spec: " + connection.getAddress() +
                            ", error message: " + jrtReq.errorMessage());
                    noApplicationWarningLogged = Instant.now();
                }
                break;
            default:
                log.log(LogLevel.WARNING, "Request callback failed. Req: " + jrtReq + "\nSpec: " + connection.getAddress() +
                        " . Req error message: " + jrtReq.errorMessage());
                break;
        }
    }

    private void handleFailedRequest(JRTClientConfigRequest jrtReq, JRTConfigSubscription<ConfigInstance> sub, Connection connection) {
        final boolean configured = (sub.getConfigState().getConfig() != null);
        if (configured) {
            // The subscription object has an "old" config, which is all we have to offer back now
            log.log(LogLevel.INFO, "Failure of config subscription, clients will keep existing config until resolved: " + sub);
        }
        final ErrorType errorType = ErrorType.getErrorType(jrtReq.errorCode());
        connectionPool.setError(connection, jrtReq.errorCode());
        long delay = calculateFailedRequestDelay(errorType, transientFailures, fatalFailures, timingValues, configured);
        if (errorType == ErrorType.TRANSIENT) {
            handleTransientlyFailed(jrtReq, sub, delay, connection);
        } else {
            handleFatallyFailed(jrtReq, sub, delay);
        }
    }

    static long calculateFailedRequestDelay(ErrorType errorCode, int transientFailures, int fatalFailures,
                                            TimingValues timingValues, boolean configured) {
        long delay;
        if (configured)
            delay = timingValues.getConfiguredErrorDelay();
        else
            delay = timingValues.getUnconfiguredDelay();
        if (errorCode == ErrorType.TRANSIENT) {
            delay = delay * Math.min((transientFailures + 1), timingValues.getMaxDelayMultiplier());
        } else {
            delay = timingValues.getFixedDelay() + (delay * Math.min(fatalFailures, timingValues.getMaxDelayMultiplier()));
            delay = timingValues.getPlusMinusFractionRandom(delay, randomFraction);
        }
        return delay;
    }

    private void handleTransientlyFailed(JRTClientConfigRequest jrtReq,
                                         JRTConfigSubscription<ConfigInstance> sub,
                                         long delay,
                                         Connection connection) {
        transientFailures++;
        if (suspendWarningLogged.isBefore(Instant.now().minus(delayBetweenWarnings))) {
            log.log(LogLevel.INFO, "Connection to " + connection.getAddress() +
                    " failed or timed out, clients will keep existing config, will keep trying.");
            suspendWarningLogged = Instant.now();
        }
        if (sub.getState() != ConfigSubscription.State.OPEN) return;
        scheduleNextRequest(jrtReq, sub, delay, calculateErrorTimeout());
    }

    private long calculateErrorTimeout() {
        return timingValues.getPlusMinusFractionRandom(timingValues.getErrorTimeout(), randomFraction);
    }

    /**
     * This handles a fatal error both in the case that the subscriber is configured and not.
     * The difference is in the delay (passed from outside) and the log level used for
     * error message.
     *
     * @param jrtReq a JRT config request
     * @param sub    a config subscription
     * @param delay  delay before sending a new request
     */
    private void handleFatallyFailed(JRTClientConfigRequest jrtReq,
                                     JRTConfigSubscription<ConfigInstance> sub, long delay) {
        if (sub.getState() != ConfigSubscription.State.OPEN) return;
        fatalFailures++;
        // The logging depends on whether we are configured or not.
        Level logLevel = sub.getConfigState().getConfig() == null ? LogLevel.DEBUG : LogLevel.INFO;
        String logMessage = "Request for config " + jrtReq.getShortDescription() + "' failed with error code " +
                jrtReq.errorCode() + " (" + jrtReq.errorMessage() + "), scheduling new connect " +
                " in " + delay + " ms";
        log.log(logLevel, logMessage);
        scheduleNextRequest(jrtReq, sub, delay, calculateErrorTimeout());
    }

    private void handleOKRequest(JRTClientConfigRequest jrtReq,
                                 JRTConfigSubscription<ConfigInstance> sub,
                                 Connection connection) {
        // Reset counters pertaining to error handling here
        fatalFailures = 0;
        transientFailures = 0;
        suspendWarningLogged = Instant.MIN;
        noApplicationWarningLogged = Instant.MIN;
        connection.setSuccess();
        sub.setLastCallBackOKTS(System.currentTimeMillis());
        if (jrtReq.hasUpdatedGeneration()) {
            // We only want this latest generation to be in the queue, we do not preserve history in this system
            sub.getReqQueue().clear();
            boolean putOK = sub.getReqQueue().offer(jrtReq);
            if (!putOK) {
                sub.setException(new ConfigurationRuntimeException("Could not put returned request on queue of subscription " + sub));
            }
        }
        if (sub.getState() != ConfigSubscription.State.OPEN) return;
        scheduleNextRequest(jrtReq, sub,
                calculateSuccessDelay(),
                calculateSuccessTimeout());
    }

    private long calculateSuccessTimeout() {
        return timingValues.getPlusMinusFractionRandom(timingValues.getSuccessTimeout(), randomFraction);
    }

    private long calculateSuccessDelay() {
        return timingValues.getPlusMinusFractionRandom(timingValues.getFixedDelay(), randomFraction);
    }

    private void scheduleNextRequest(JRTClientConfigRequest jrtReq, JRTConfigSubscription<?> sub, long delay, long timeout) {
        if (delay < 0) delay = 0;
        JRTClientConfigRequest jrtReqNew = jrtReq.nextRequest(timeout);
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "My timing values: " + timingValues);
            log.log(LogLevel.DEBUG, "Scheduling new request " + delay + " millis from now for " + jrtReqNew.getConfigKey());
        }
        scheduler.schedule(new GetConfigTask(jrtReqNew, sub), delay, TimeUnit.MILLISECONDS);
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
            doRequest(sub, jrtReq, jrtReq.getTimeout());
        }
    }

    public void close() {
        // Fake that we have logged to avoid printing warnings after this
        suspendWarningLogged = Instant.now();
        noApplicationWarningLogged = Instant.now();

        connectionPool.close();
        scheduler.shutdown();
    }

    private class JRTSourceThreadFactory implements ThreadFactory {
        @SuppressWarnings("NullableProblems")
        @Override
        public Thread newThread(Runnable runnable) {
            ThreadFactory tf = Executors.defaultThreadFactory();
            Thread t = tf.newThread(runnable);
            // We want a daemon thread to avoid hanging threads in case something goes wrong in the config system
            t.setDaemon(true);
            return t;
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

    int getTransientFailures() {
        return transientFailures;
    }

    int getFatalFailures() {
        return fatalFailures;
    }

    // TODO: Should be package private, used in integrationtest.rb in system tests
    public ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    private Double getClientTimeout(long serverTimeout) {
        return (serverTimeout / 1000.0) + additionalTimeForClientTimeout;
    }
}
