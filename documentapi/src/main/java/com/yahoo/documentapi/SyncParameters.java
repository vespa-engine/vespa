// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    /**
     * @deprecated Use {@link Builder} instead.
     */
    @Deprecated
    // TODO Vespa 7: Make private
    public SyncParameters() {
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

        /**
         * Set default timeout for all messagebus operations.
         */
        public void setDefaultTimeout(Duration defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
        }

        public SyncParameters build() {
            return new SyncParameters(defaultTimeout);
        }
    }
}
