// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.slobrok.api;

/**
 * Interface used to control how fast the mirror and register classes
 * will retry in case of errors. The reset method is used to indicate
 * that all is ok. When things start failing, the get method will be
 * invoked for each new attempt in order to get the appropriate delay
 * between retries (typically increasing for each invocation). The
 * shouldWarn method is used to ask if a certain delay returned from
 * get should result in a warning being logged. When things get back
 * to normal operation, the reset method is invoked to indicate that
 * we are no longer in a failure state.
 **/
public interface BackOffPolicy
{
    /**
     * Reset backoff logic.
     **/
    public void reset();

    /**
     * Obtain the number of seconds to wait before the next
     * attempt.
     *
     * @return delay in seconds
     **/
    public double get();

    /**
     * Check if a certain delay should result in a warning being
     * logged.
     *
     * @return true if we should log
     * @param t current delay value
     **/
    public boolean shouldWarn(double t);

    /**
     * Check if a certain delay should result in an information message logged.
     *
     * @return true if we should log
     * @param t current delay value
     **/
    public boolean shouldInform(double t);
}
