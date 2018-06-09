// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.jrt.Request;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.ErrorType;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class JRTConfigRequesterTest {

    @Test
    public void testDelayCalculation() {
        TimingValues defaultTimingValues = new TimingValues();
        Random random = new Random(0); // Use seed to make tests predictable
        TimingValues timingValues = new TimingValues(defaultTimingValues, random);

        // transientFailures and fatalFailures are not set until after delay has been calculated,
        // so 0 is the case for the first failure
        int transientFailures = 0;
        int fatalFailures = 0;
        boolean configured = false;

        // First time failure, not configured
        long delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.TRANSIENT,
                transientFailures, fatalFailures, timingValues, configured);
        assertThat(delay, is(timingValues.getUnconfiguredDelay()));
        transientFailures = 5;
        delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.TRANSIENT,
                transientFailures, fatalFailures, timingValues, configured);
        assertThat(delay, is((transientFailures + 1) * timingValues.getUnconfiguredDelay()));
        transientFailures = 0;


        delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.FATAL,
                transientFailures, fatalFailures, timingValues, configured);
        assertTrue(delay > (1 - JRTConfigRequester.randomFraction) * timingValues.getFixedDelay());
        assertTrue(delay < (1 + JRTConfigRequester.randomFraction) * timingValues.getFixedDelay());
        assertThat(delay, is(5462L));

        // First time failure, configured
        configured = true;

        delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.TRANSIENT,
                transientFailures, fatalFailures, timingValues, configured);
        assertThat(delay, is(timingValues.getConfiguredErrorDelay()));

        delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.FATAL,
                transientFailures, fatalFailures, timingValues, configured);
        assertTrue(delay > (1 - JRTConfigRequester.randomFraction) * timingValues.getFixedDelay());
        assertTrue(delay < (1 + JRTConfigRequester.randomFraction) * timingValues.getFixedDelay());
        assertThat(delay, is(5663L));


        // nth time failure, not configured
        fatalFailures = 1;
        configured = false;
        delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.TRANSIENT,
                transientFailures, fatalFailures, timingValues, configured);
        assertThat(delay, is(timingValues.getUnconfiguredDelay()));
        delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.FATAL,
                transientFailures, fatalFailures, timingValues, configured);
        final long l = timingValues.getFixedDelay() + timingValues.getUnconfiguredDelay();
        assertTrue(delay > (1 - JRTConfigRequester.randomFraction) * l);
        assertTrue(delay < (1 + JRTConfigRequester.randomFraction) * l);
        assertThat(delay, is(5377L));


        // nth time failure, configured
        fatalFailures = 1;
        configured = true;
        delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.TRANSIENT,
                transientFailures, fatalFailures, timingValues, configured);
        assertThat(delay, is(timingValues.getConfiguredErrorDelay()));
        delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.FATAL,
                transientFailures, fatalFailures, timingValues, configured);
        final long l1 = timingValues.getFixedDelay() + timingValues.getConfiguredErrorDelay();
        assertTrue(delay > (1 - JRTConfigRequester.randomFraction) * l1);
        assertTrue(delay < (1 + JRTConfigRequester.randomFraction) * l1);
        assertThat(delay, is(20851L));


        // 1 more than max delay multiplier time failure, configured
        fatalFailures = timingValues.getMaxDelayMultiplier() + 1;
        configured = true;
        delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.TRANSIENT,
                transientFailures, fatalFailures, timingValues, configured);
        assertThat(delay, is(timingValues.getConfiguredErrorDelay()));
        assertTrue(delay < timingValues.getMaxDelayMultiplier() * timingValues.getConfiguredErrorDelay());
        delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.FATAL,
                transientFailures, fatalFailures, timingValues, configured);
        final long l2 = timingValues.getFixedDelay() + timingValues.getMaxDelayMultiplier() * timingValues.getConfiguredErrorDelay();
        assertTrue(delay > (1 - JRTConfigRequester.randomFraction) * l2);
        assertTrue(delay < (1 + JRTConfigRequester.randomFraction) * l2);
        assertThat(delay, is(163520L));
    }

    @Test
    public void testDelay() {
        TimingValues timingValues = new TimingValues();

        // transientFailures and fatalFailures are not set until after delay has been calculated,
        // so 0 is the case for the first failure
        int transientFailures = 0;
        int fatalFailures = 0;

        // First time failure, configured
        long delay = JRTConfigRequester.calculateFailedRequestDelay(ErrorType.TRANSIENT,
                transientFailures, fatalFailures, timingValues, true);
        assertThat(delay, is(timingValues.getConfiguredErrorDelay()));
        assertThat(delay, is((transientFailures + 1) * timingValues.getConfiguredErrorDelay()));
    }

    @Test
    public void testErrorTypes() {
        List<Integer> transientErrors = Arrays.asList(com.yahoo.jrt.ErrorCode.CONNECTION, com.yahoo.jrt.ErrorCode.TIMEOUT);
        List<Integer> fatalErrors = Arrays.asList(ErrorCode.UNKNOWN_CONFIG, ErrorCode.UNKNOWN_DEFINITION, ErrorCode.OUTDATED_CONFIG,
                ErrorCode.UNKNOWN_DEF_MD5, ErrorCode.ILLEGAL_NAME, ErrorCode.ILLEGAL_VERSION, ErrorCode.ILLEGAL_CONFIGID,
                ErrorCode.ILLEGAL_DEF_MD5, ErrorCode.ILLEGAL_CONFIG_MD5, ErrorCode.ILLEGAL_TIMEOUT, ErrorCode.INTERNAL_ERROR,
                9999); // unknown should also be fatal
        for (Integer i : transientErrors) {
            assertThat(ErrorType.getErrorType(i), is(ErrorType.TRANSIENT));
        }
        for (Integer i : fatalErrors) {
            assertThat(ErrorType.getErrorType(i), is(ErrorType.FATAL));
        }
    }

    @Test
    public void testFirstRequestAfterSubscribing() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        final TimingValues timingValues = getTestTimingValues();
        JRTConfigSubscription<SimpletypesConfig> sub = createSubscription(subscriber, timingValues);

        final MockConnection connection = new MockConnection();
        JRTConfigRequester requester = new JRTConfigRequester(connection, timingValues);
        assertThat(requester.getConnectionPool(), is(connection));
        requester.request(sub);
        final Request request = connection.getRequest();
        assertNotNull(request);
        assertThat(connection.getNumberOfRequests(), is(1));
        JRTServerConfigRequestV3 receivedRequest = JRTServerConfigRequestV3.createFromRequest(request);
        assertTrue(receivedRequest.validateParameters());
        assertThat(receivedRequest.getTimeout(), is(timingValues.getSubscribeTimeout()));
        assertThat(requester.getFatalFailures(), is(0));
        assertThat(requester.getTransientFailures(), is(0));
    }

    @Test
    public void testFatalError() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        final TimingValues timingValues = getTestTimingValues();

        final MockConnection connection = new MockConnection(new ErrorResponseHandler());
        JRTConfigRequester requester = new JRTConfigRequester(connection, timingValues);
        requester.request(createSubscription(subscriber, timingValues));
        waitUntilResponse(connection);
        assertThat(requester.getFatalFailures(), is(1));
        assertThat(requester.getTransientFailures(), is(0));
    }

    @Test
    public void testFatalErrorSubscribed() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        final TimingValues timingValues = getTestTimingValues();
        JRTConfigSubscription<SimpletypesConfig> sub = createSubscription(subscriber, timingValues);
        sub.setConfig(1L, false, config());

        final MockConnection connection = new MockConnection(new ErrorResponseHandler());
        JRTConfigRequester requester = new JRTConfigRequester(connection, timingValues);
        requester.request(sub);
        waitUntilResponse(connection);
        assertThat(requester.getFatalFailures(), is(1));
        assertThat(requester.getTransientFailures(), is(0));
    }

    @Test
    public void testTransientError() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        final TimingValues timingValues = getTestTimingValues();

        final MockConnection connection = new MockConnection(new ErrorResponseHandler(com.yahoo.jrt.ErrorCode.TIMEOUT));
        JRTConfigRequester requester = new JRTConfigRequester(connection, timingValues);
        requester.request(createSubscription(subscriber, timingValues));
        waitUntilResponse(connection);
        assertThat(requester.getFatalFailures(), is(0));
        assertThat(requester.getTransientFailures(), is(1));
    }

    @Test
    public void testTransientErrorSubscribed() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        final TimingValues timingValues = getTestTimingValues();
        JRTConfigSubscription<SimpletypesConfig> sub = createSubscription(subscriber, timingValues);
        sub.setConfig(1L, false, config());

        final MockConnection connection = new MockConnection(new ErrorResponseHandler(com.yahoo.jrt.ErrorCode.TIMEOUT));
        JRTConfigRequester requester = new JRTConfigRequester(connection, timingValues);
        requester.request(sub);
        waitUntilResponse(connection);
        assertThat(requester.getFatalFailures(), is(0));
        assertThat(requester.getTransientFailures(), is(1));
    }

    @Test
    public void testUnknownConfigDefinitionError() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        final TimingValues timingValues = getTestTimingValues();
        JRTConfigSubscription<SimpletypesConfig> sub = createSubscription(subscriber, timingValues);
        sub.setConfig(1L, false, config());

        final MockConnection connection = new MockConnection(new ErrorResponseHandler(ErrorCode.UNKNOWN_DEFINITION));
        JRTConfigRequester requester = new JRTConfigRequester(connection, timingValues);
        assertThat(requester.getConnectionPool(), is(connection));
        requester.request(sub);
        waitUntilResponse(connection);
        assertThat(requester.getFatalFailures(), is(1));
        assertThat(requester.getTransientFailures(), is(0));
        // TODO Check that no further request was sent?
    }

    @Test
    public void testClosedSubscription() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        final TimingValues timingValues = getTestTimingValues();
        JRTConfigSubscription<SimpletypesConfig> sub = createSubscription(subscriber, timingValues);
        sub.close();

        final MockConnection connection = new MockConnection(new MockConnection.OKResponseHandler());
        JRTConfigRequester requester = new JRTConfigRequester(connection, timingValues);
        requester.request(sub);
        assertThat(connection.getNumberOfRequests(), is(1));
        // Check that no further request was sent?
        try {
            Thread.sleep(timingValues.getFixedDelay()*2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertThat(connection.getNumberOfRequests(), is(1));
    }

    @Test
    public void testTimeout() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        final TimingValues timingValues = getTestTimingValues();
        JRTConfigSubscription<SimpletypesConfig> sub = createSubscription(subscriber, timingValues);
        sub.close();

        final MockConnection connection = new MockConnection(
                new DelayedResponseHandler(timingValues.getSubscribeTimeout()),
                2); // fake that we have more than one source
        JRTConfigRequester requester = new JRTConfigRequester(connection, timingValues);
        requester.request(createSubscription(subscriber, timingValues));
        // Check that no further request was sent?
        try {
            Thread.sleep(timingValues.getFixedDelay()*2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertTrue(connection.getNumberOfFailovers() >= 1);
    }

    private JRTConfigSubscription<SimpletypesConfig> createSubscription(ConfigSubscriber subscriber, TimingValues timingValues) {
        return new JRTConfigSubscription<>(
                new ConfigKey<>(SimpletypesConfig.class, "testid"), subscriber, null, timingValues);
    }

    private SimpletypesConfig config() {
        SimpletypesConfig.Builder builder = new SimpletypesConfig.Builder();
        return new SimpletypesConfig(builder);
    }

    private void waitUntilResponse(MockConnection connection) {
        int i = 0;
        while (i < 1000 && connection.getRequest() == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            i++;
        }
    }

    public static TimingValues getTestTimingValues() { return new TimingValues(
            1000,  // successTimeout
            500,   // errorTimeout
            500,   // initialTimeout
            2000,  // subscribeTimeout
            250,   // unconfiguredDelay
            500,   // configuredErrorDelay
            250,   // fixedDelay
            5);    // maxDelayMultiplier
    }

    private static class ErrorResponseHandler extends MockConnection.OKResponseHandler {
        private final int errorCode;

        public ErrorResponseHandler() {
            this(ErrorCode.INTERNAL_ERROR);
        }

        public ErrorResponseHandler(int errorCode) {
            this.errorCode = errorCode;
        }

        @Override
        public void run() {
            System.out.println("Running error response handler");
            request().setError(errorCode, "error");
            requestWaiter().handleRequestDone(request());
        }
    }

    private static class DelayedResponseHandler extends MockConnection.OKResponseHandler {
        private final long waitTimeMilliSeconds;

        public DelayedResponseHandler(long waitTimeMilliSeconds) {
            this.waitTimeMilliSeconds = waitTimeMilliSeconds;
        }

        @Override
        public void run() {
            System.out.println("Running delayed response handler (waiting " + waitTimeMilliSeconds +
            ") before responding");
            try {
                Thread.sleep(waitTimeMilliSeconds);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            request().setError(com.yahoo.jrt.ErrorCode.TIMEOUT, "error");
            requestWaiter().handleRequestDone(request());
        }
    }

}
