// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import java.util.Random;

/**
 * Timeouts, delays and retries used in RPC config protocol.
 *
 * @author Gunnar Gauslaa Bergem
 */
public class TimingValues {
    public static final long defaultNextConfigTimeout = 1000;
    // See getters below for an explanation of how these values are used and interpreted
    // All time values in milliseconds.
    private final long successTimeout;
    private final long errorTimeout;
    private final long initialTimeout;
    private long subscribeTimeout = 55000;

    private long fixedDelay = 5000;
    private final Random rand;

    public TimingValues() {
        successTimeout = 600000;
        errorTimeout = 20000;
        initialTimeout = 15000;
        this.rand = new Random(System.currentTimeMillis());
    }

    // TODO Should add nextConfigTimeout in all constructors
    public TimingValues(long successTimeout,
                        long errorTimeout,
                        long initialTimeout,
                        long subscribeTimeout,
                        long fixedDelay) {
        this.successTimeout = successTimeout;
        this.errorTimeout = errorTimeout;
        this.initialTimeout = initialTimeout;
        this.subscribeTimeout = subscribeTimeout;
        this.fixedDelay = fixedDelay;
        this.rand = new Random(System.currentTimeMillis());
    }

    private TimingValues(long successTimeout,
                         long errorTimeout,
                         long initialTimeout,
                         long subscribeTimeout,
                         long fixedDelay,
                         Random rand) {
        this.successTimeout = successTimeout;
        this.errorTimeout = errorTimeout;
        this.initialTimeout = initialTimeout;
        this.subscribeTimeout = subscribeTimeout;
        this.fixedDelay = fixedDelay;
        this.rand = rand;
    }

    public TimingValues(TimingValues tv, Random random) {
        this(tv.successTimeout,
                tv.errorTimeout,
                tv.initialTimeout,
                tv.subscribeTimeout,
                tv.fixedDelay,
                random);
    }

    /**
     * Returns timeout to use as server timeout when previous config request was a success.
     *
     * @return timeout in milliseconds.
     */
    public long getSuccessTimeout() {
        return successTimeout;
    }

    /**
     * Returns timeout to use as server timeout when we got an error with the previous config request.
     *
     * @return timeout in milliseconds.
     */
    public long getErrorTimeout() {
        return errorTimeout;
    }

    /**
     * Returns timeout to use as server timeout when subscribing for the first time.
     *
     * @return timeout in milliseconds.
     */
    public long getSubscribeTimeout() {
        return subscribeTimeout;
    }

    public TimingValues setSubscribeTimeout(long t) {
        subscribeTimeout = t;
        return this;
    }

    /**
     * Returns fixed delay that is used when retrying getting config no matter if it was a success or an error
     * and independent of number of retries.
     *
     * @return timeout in milliseconds.
     */
    public long getFixedDelay() {
        return fixedDelay;
    }

    public TimingValues setFixedDelay(long t) {
        fixedDelay = t;
        return this;
    }

    /**
     * Returns a number +/- a random component
     *
     * @param value      input
     * @param fraction for instance 0.1 for +/- 10%
     * @return a number
     */
    public long getPlusMinusFractionRandom(long value, float fraction) {
        return Math.round(value - (value * fraction) + (rand.nextFloat() * 2L * value * fraction));
    }

    @Override
    public String toString() {
        return "TimingValues [successTimeout=" + successTimeout
               + ", errorTimeout=" + errorTimeout
               + ", initialTimeout=" + initialTimeout
               + ", subscribeTimeout=" + subscribeTimeout
               + ", fixedDelay=" + fixedDelay
               + ", rand=" + rand + "]";
    }


}
