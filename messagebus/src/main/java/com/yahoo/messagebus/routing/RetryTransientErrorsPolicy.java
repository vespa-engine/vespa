// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.messagebus.ErrorCode;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements a retry policy that allows resending of any error that is not fatal. It also does progressive back-off,
 * delaying each attempt by the given time multiplied by the retry attempt.
 *
 * @author Simon Thoresen Hult
 */
public class RetryTransientErrorsPolicy implements RetryPolicy {

    private static final double US = 1000000;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private volatile AtomicLong baseDelayUS = new AtomicLong(1000);

    /**
     * Sets whether or not this policy should allow retries or not.
     *
     * @param enabled True to allow retries.
     * @return This, to allow chaining.
     */
    public RetryTransientErrorsPolicy setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        return this;
    }

    /**
     * Sets the base delay in seconds to wait between retries. This amount is multiplied by the retry number.
     *
     * @param baseDelay The time in seconds.
     * @return This, to allow chaining.
     */
    public RetryTransientErrorsPolicy setBaseDelay(double baseDelay) {
        this.baseDelayUS.set((long)(baseDelay*US));
        return this;
    }

    @Override
    public boolean canRetry(int errorCode) {
        return enabled.get() && errorCode < ErrorCode.FATAL_ERROR;
    }

    @Override
    public double getRetryDelay(int retry) {
        long retryMultiplier = 0l;
        if (retry > 1) {
            retryMultiplier = 1L << Math.min(20, retry-1);
        }

        return Math.min(10.0, (retryMultiplier*baseDelayUS.get())/US);
    }
}
