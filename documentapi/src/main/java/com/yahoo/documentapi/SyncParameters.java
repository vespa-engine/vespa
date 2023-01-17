// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import java.time.Duration;
import java.util.Optional;

/**
 * Parameters for creating a synchronous session
 *
 * @author bjorncs
 * @author Simon Thoresen Hult
 */
public class SyncParameters extends Parameters {

    private final Duration defaultTimeout;

    private SyncParameters() {
        this(null);
    }

    private SyncParameters(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public Optional<Duration> defaultTimeout() {
        return Optional.ofNullable(defaultTimeout);
    }

    public static class Builder {

        private Duration defaultTimeout;

        /** @deprecated use {@link #defaultTimeout} */
        @Deprecated // TODO: Remove on Vespa 9
        public void setDefaultTimeout(Duration defaultTimeout) {
            defaultTimeout(defaultTimeout);
        }

        /** Set default timeout for all messagebus operations. */
        public Builder defaultTimeout(Duration defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
            return this;
        }

        public SyncParameters build() {
            return new SyncParameters(defaultTimeout);
        }
    }

}
