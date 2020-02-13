// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private long successTimeout = 600000;
    private long errorTimeout = 20000;
    private long initialTimeout = 15000;
    private long subscribeTimeout = 55000;
    private long configuredErrorTimeout = -1;  // Don't ever timeout (and do not use error response) when we are already configured
    private long nextConfigTimeout = defaultNextConfigTimeout;

    private long fixedDelay = 5000;
    private long unconfiguredDelay = 1000;
    private long configuredErrorDelay = 15000;
    private int  maxDelayMultiplier = 10;
    private final Random rand;

    public TimingValues() {
        this.rand = new Random(System.currentTimeMillis());
    }

    // TODO Should add nextConfigTimeout in all constructors
    public TimingValues(long successTimeout,
                        long errorTimeout,
                        long initialTimeout,
                        long subscribeTimeout,
                        long unconfiguredDelay,
                        long configuredErrorDelay,
                        long fixedDelay,
                        int maxDelayMultiplier) {
        this.successTimeout = successTimeout;
        this.errorTimeout = errorTimeout;
        this.initialTimeout = initialTimeout;
        this.subscribeTimeout = subscribeTimeout;
        this.unconfiguredDelay = unconfiguredDelay;
        this.configuredErrorDelay = configuredErrorDelay;
        this.fixedDelay = fixedDelay;
        this.maxDelayMultiplier = maxDelayMultiplier;
        this.rand = new Random(System.currentTimeMillis());
    }

    private TimingValues(long successTimeout,
                         long errorTimeout,
                         long initialTimeout,
                         long subscribeTimeout,
                         long unconfiguredDelay,
                         long configuredErrorDelay,
                         long fixedDelay,
                         int maxDelayMultiplier,
                         Random rand) {
        this.successTimeout = successTimeout;
        this.errorTimeout = errorTimeout;
        this.initialTimeout = initialTimeout;
        this.subscribeTimeout = subscribeTimeout;
        this.unconfiguredDelay = unconfiguredDelay;
        this.configuredErrorDelay = configuredErrorDelay;
        this.fixedDelay = fixedDelay;
        this.maxDelayMultiplier = maxDelayMultiplier;
        this.rand = rand;
    }

    public TimingValues(TimingValues tv, Random random) {
        this(tv.successTimeout,
                tv.errorTimeout,
                tv.initialTimeout,
                tv.subscribeTimeout,
                tv.unconfiguredDelay,
                tv.configuredErrorDelay,
                tv.fixedDelay,
                tv.maxDelayMultiplier,
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
     * Returns initial timeout to use as server timeout when a config is requested for the first time.
     *
     * @return timeout in milliseconds.
     */
    public long getInitialTimeout() {
        return initialTimeout;
    }

    public TimingValues setInitialTimeout(long t) {
        initialTimeout = t;
        return this;
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
     * Returns the time to retry getting config from the remote sources, until the next error response will
     * be set as config. Counted from the last ok request was received. A negative value means that
     * we will always retry getting config and never set an error response as config.
     *
     * @return timeout in milliseconds.
     */
    public long getConfiguredErrorTimeout() {
        return configuredErrorTimeout;
    }

    public TimingValues setConfiguredErrorTimeout(long t) {
        configuredErrorTimeout = t;
        return this;
    }

    /**
     * Returns timeout used when calling {@link com.yahoo.config.subscription.ConfigSubscriber#nextConfig()} or
     * {@link com.yahoo.config.subscription.ConfigSubscriber#nextGeneration()}
     *
     * @return timeout in milliseconds.
     */
    public long getNextConfigTimeout() {
        return nextConfigTimeout;
    }

    public TimingValues setNextConfigTimeout(long t) {
        nextConfigTimeout = t;
        return this;
    }

    /**
     * Returns time to wait until next attempt to get config after a failed request when the client has not
     * gotten a successful response to a config subscription (i.e, the client has not been configured).
     * A negative value means that there will never be a next attempt. If a negative value is set, the
     * user must also setSubscribeTimeout(0) to prevent a deadlock while subscribing.
     *
     * @return delay in milliseconds, a negative value means never.
     */
    public long getUnconfiguredDelay() {
        return unconfiguredDelay;
    }

    public TimingValues setUnconfiguredDelay(long d) {
        unconfiguredDelay = d;
        return this;
    }

    /**
     * Returns time to wait until next attempt to get config after a failed request when the client has
     * previously gotten a successful response to a config subscription (i.e, the client is configured).
     * A negative value means that there will never be a next attempt.
     *
     * @return delay in milliseconds, a negative value means never.
     */
    public long getConfiguredErrorDelay() {
        return configuredErrorDelay;
    }

    public TimingValues setConfiguredErrorDelay(long d) {
        configuredErrorDelay = d;
        return this;
    }

    /**
     * Returns maximum multiplier to use when calculating delay (the delay is multiplied by the number of
     * failed requests, unless that number is this maximum multiplier).
     *
     * @return timeout in milliseconds.
     */
    public int getMaxDelayMultiplier() {
        return maxDelayMultiplier;
    }


    public TimingValues setSuccessTimeout(long successTimeout) {
        this.successTimeout = successTimeout;
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

    /**
     * Returns a number +/- a random component
     *
     * @param val      input
     * @param fraction for instance 0.1 for +/- 10%
     * @return a number
     */
    public long getPlusMinusFractionRandom(long val, float fraction) {
        return Math.round(val - (val * fraction) + (rand.nextFloat() * 2L * val * fraction));
    }

    Random getRandom() {
        return rand;
    }

    @Override
    public String toString() {
        return "TimingValues [successTimeout=" + successTimeout
                + ", errorTimeout=" + errorTimeout + ", initialTimeout="
                + initialTimeout + ", subscribeTimeout=" + subscribeTimeout
                + ", configuredErrorTimeout=" + configuredErrorTimeout
                + ", fixedDelay=" + fixedDelay + ", unconfiguredDelay="
                + unconfiguredDelay + ", configuredErrorDelay="
                + configuredErrorDelay + ", maxDelayMultiplier="
                + maxDelayMultiplier + ", rand=" + rand + "]";
    }


}
