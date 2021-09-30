// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import java.time.Duration;
import java.util.Random;

/**
 * Calculate a delay using an exponential backoff algorithm. Based on ExponentialBackOff in google-http-client.
 *
 * @author mpolden
 */
public class ExponentialBackoff {

    private static final double RANDOMIZATION_FACTOR = 0.5;

    private final Duration initialDelay;
    private final Duration maxDelay;
    private final Random random;

    public ExponentialBackoff(Duration initialDelay, Duration maxDelay) {
        this(initialDelay, maxDelay, new Random());
    }

    ExponentialBackoff(Duration initialDelay, Duration maxDelay, Random random) {
        this.initialDelay = requireNonNegative(initialDelay);
        this.maxDelay = requireNonNegative(maxDelay);
        this.random = random;
    }

    /** Return the delay of given attempt */
    public Duration delay(int attempt) {
        if (attempt < 1) throw new IllegalArgumentException("Attempt must be positive");
        double currentDelay = attempt * initialDelay.toMillis();
        double delta = RANDOMIZATION_FACTOR * currentDelay;
        double lowerDelay = currentDelay - delta;
        double upperDelay = currentDelay + delta;
        long millis = (long) Math.min(lowerDelay + (random.nextDouble() * (upperDelay - lowerDelay + 1)),
                                      maxDelay.toMillis());
        return Duration.ofMillis(millis);
    }

    private static Duration requireNonNegative(Duration d) {
        if (d.isNegative()) throw new IllegalArgumentException("Invalid duration: " + d);
        return d;
    }

}
