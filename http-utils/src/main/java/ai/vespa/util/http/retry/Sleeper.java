// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.retry;

import java.time.Duration;

/**
 * An abstraction used for mocking {@link Thread#sleep(long)} in unit tests.
 *
 * @author bjorncs
 */
interface Sleeper {
    void sleep(Duration duration);

    class Default implements Sleeper {
        @Override
        public void sleep(Duration duration) {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}

