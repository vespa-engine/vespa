// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * The context passed to a {@link Provisioner} when preparing host allocation for an application.
 *
 * @author bratseth
 */
public class ProvisionContext {

    private final ProvisionLogger provisionLogger;

    private ProvisionContext(Builder builder) {
        this.provisionLogger = Objects.requireNonNull(builder.provisionLogger, "provisionLogger must be set");
    }

    /** Returns the logger which receives messages which are returned to the requestor. */
    public ProvisionLogger provisionLogger() { return provisionLogger; }

    public static class Builder {

        private ProvisionLogger provisionLogger;

        public Builder setLogger(ProvisionLogger provisionLogger) {
            this.provisionLogger = provisionLogger;
            return this;
        }

        public ProvisionContext build() {
            return new ProvisionContext(this);
        }

    }

}
