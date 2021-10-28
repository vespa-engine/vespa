// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc4.retry;

import java.time.Duration;

/**
 * An abstraction that calculates the next delay based on the current retry count.
 *
 * @author bjorncs
 */
@FunctionalInterface
interface DelaySupplier {
    Duration getDelay(int executionCount);

    class Fixed implements DelaySupplier {
        private final Duration delay;

        Fixed(Duration delay) {
            this.delay = delay;
        }

        @Override
        public Duration getDelay(int executionCount) { return delay; }
    }

    class Exponential implements DelaySupplier {
        private final Duration startDelay;
        private final Duration maxDelay;

        Exponential(Duration startDelay, Duration maxDelay) {
            this.startDelay = startDelay;
            this.maxDelay = maxDelay;
        }

        @Override
        public Duration getDelay(int executionCount) {
            Duration nextDelay = startDelay;
            for (int i = 1; i < executionCount; ++i) {
                nextDelay = nextDelay.multipliedBy(2);
            }
            return maxDelay.compareTo(nextDelay) > 0 ? nextDelay : maxDelay;
        }
    }
}
