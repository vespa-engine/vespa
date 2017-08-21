// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import java.time.temporal.TemporalAmount;
import java.util.Optional;

/**
 * Parameters for creating a synchronous session
 *
 * @author bjorncs
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class SyncParameters extends Parameters {
    private final TemporalAmount defaultTimeout;

    /**
     * @deprecated Use {@link Builder} instead.
     */
    @Deprecated
    // TODO Vespa 7: Make private
    public SyncParameters() {
        this(null);
    }

    private SyncParameters(TemporalAmount defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public Optional<TemporalAmount> defaultTimeout() {
        return Optional.ofNullable(defaultTimeout);
    }

    public static class Builder {
        private TemporalAmount defaultTimeout;

        /**
         * Set default timeout for all messagebus operations.
         */
        public void setDefaultTimeout(TemporalAmount defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
        }

        public SyncParameters build() {
            return new SyncParameters(defaultTimeout);
        }
    }
}
