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
    private final ValidationOverrides validationOverrides;

    private ProvisionContext(Builder builder) {
        this.provisionLogger = Objects.requireNonNull(builder.provisionLogger, "provisionLogger must be set");
        this.validationOverrides = builder.validationOverrides().build();
    }

    /** Returns the logger which receives messages which are returned to the requestor. */
    public ProvisionLogger provisionLogger() { return provisionLogger; }

    public ValidationOverrides validationOverrides() { return validationOverrides; }

    public static class Builder {

        private ProvisionLogger provisionLogger;

        private final ValidationOverrides.Builder validationOverrides = new ValidationOverrides.Builder();

        public Builder setLogger(ProvisionLogger provisionLogger) {
            this.provisionLogger = provisionLogger;
            return this;
        }

        public ValidationOverrides.Builder validationOverrides() { return validationOverrides; }

        public ProvisionContext build() {
            return new ProvisionContext(this);
        }

    }

    /**
     * Validation overrides available in provisioning.
     * These allow applications to override (disable) certain validations which will otherwise
     * fail deployment. See config-model-api:com.yahoo.config.application.api.ValidationId.
     */
    public static class ValidationOverrides {

        private final boolean warnOnly;
        private final boolean overrideResourcesReduction;

        public ValidationOverrides(Builder builder) {
            this.warnOnly = builder.warnOnly;
            this.overrideResourcesReduction = builder.overrideResourcesReduction;
        }

        /** True to warn instead of throwing on validation failures. */
        public boolean warnOnly() { return warnOnly; }

        /** True if the resources reduction validation is overridden. */
        public boolean overrideResourcesReduction() {
            return overrideResourcesReduction;
        }

        public static class Builder {

            private boolean warnOnly                   = false;
            private boolean overrideResourcesReduction = false;

            public Builder setWarnOnly(boolean warnOnly) {
                this.warnOnly = warnOnly;
                return this;
            }

            public Builder setOverrideResourcesReduction(boolean overrideResourcesReduction) {
                this.overrideResourcesReduction = overrideResourcesReduction;
                return this;
            }

            public ValidationOverrides build() {
                return new ValidationOverrides(this);
            }

        }

    }

}
