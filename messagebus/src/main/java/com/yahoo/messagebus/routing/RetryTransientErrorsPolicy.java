// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.messagebus.ErrorCode;

/**
 * Implements a retry policy that allows resending of any error that is not fatal. It also does progressive back-off,
 * delaying each attempt by the given time multiplied by the retry attempt.
 *
 * @author Simon Thoresen Hult
 */
public class RetryTransientErrorsPolicy implements RetryPolicy {

    private volatile boolean enabled = true;
    private volatile double baseDelay = 1;

    /**
     * Sets whether or not this policy should allow retries or not.
     *
     * @param enabled True to allow retries.
     * @return This, to allow chaining.
     */
    public RetryTransientErrorsPolicy setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Sets the base delay in seconds to wait between retries. This amount is multiplied by the retry number.
     *
     * @param baseDelay The time in seconds.
     * @return This, to allow chaining.
     */
    public RetryTransientErrorsPolicy setBaseDelay(double baseDelay) {
        this.baseDelay = baseDelay;
        return this;
    }

    @Override
    public boolean canRetry(int errorCode) {
        return enabled && errorCode < ErrorCode.FATAL_ERROR;
    }

    @Override
    public double getRetryDelay(int retry) {
        return baseDelay * retry;
    }
}
