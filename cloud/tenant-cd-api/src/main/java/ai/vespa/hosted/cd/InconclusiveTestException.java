// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import java.time.Duration;
import java.util.Optional;

/**
 * Signals that a test method cannot yield a conclusive result at this time, and must be retried later.
 * <p>
 * Optionally, the test may indicate when to retry, and when to give up, through {@link #retryAfter()}
 * and {@link #giveUpAfter()}. These are honored for production tests only, and are hints which the
 * platform may clamp. When unspecified, the platform default applies (currently: retry every
 * 30 minutes, with no deadline).
 *
 * @author jonmv
 */
public class InconclusiveTestException extends RuntimeException {

    private final Duration retryAfter;
    private final Duration giveUpAfter;

    public InconclusiveTestException() { this(null, null, null); }

    public InconclusiveTestException(String message) { this(message, null, null); }

    /**
     * @param message     detail message, or null
     * @param retryAfter  delay until the next retry attempt, or null for the platform default
     * @param giveUpAfter deadline measured from the start of the deployment run, past which the run
     *                    concludes as a test failure, or null for the platform default (no deadline)
     */
    public InconclusiveTestException(String message, Duration retryAfter, Duration giveUpAfter) {
        super(message);
        if (retryAfter != null && retryAfter.isNegative()) throw new IllegalArgumentException("retryAfter must be non-negative");
        if (giveUpAfter != null && giveUpAfter.isNegative()) throw new IllegalArgumentException("giveUpAfter must be non-negative");
        this.retryAfter = retryAfter;
        this.giveUpAfter = giveUpAfter;
    }

    /** Delay until the next retry attempt, if specified. A hint the platform may clamp; production tests only. */
    public Optional<Duration> retryAfter() { return Optional.ofNullable(retryAfter); }

    /** Deadline measured from the start of the deployment run, if specified. A hint the platform may clamp; production tests only. */
    public Optional<Duration> giveUpAfter() { return Optional.ofNullable(giveUpAfter); }

}
