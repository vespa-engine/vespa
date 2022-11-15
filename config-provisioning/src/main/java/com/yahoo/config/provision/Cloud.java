// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * Properties of the cloud service where the zone is deployed.
 *
 * @author mpolden
 */
public class Cloud {

    private final CloudName name;

    private final boolean dynamicProvisioning;
    private final boolean allowHostSharing;
    private final boolean requireAccessControl;
    private final CloudAccount account;

    private Cloud(CloudName name, boolean dynamicProvisioning, boolean allowHostSharing, boolean requireAccessControl,
                  CloudAccount account) {
        this.name = Objects.requireNonNull(name);
        this.dynamicProvisioning = dynamicProvisioning;
        this.allowHostSharing = allowHostSharing;
        this.requireAccessControl = requireAccessControl;
        this.account = Objects.requireNonNull(account);
        if (name.equals(CloudName.AWS) && account.isUnspecified()) {
            throw new IllegalArgumentException("Account must be non-empty in cloud '" + name + "'");
        }
    }

    /** The name of this */
    public CloudName name() {
        return name;
    }

    /** Returns whether this can provision hosts dynamically */
    public boolean dynamicProvisioning() {
        return dynamicProvisioning;
    }

    /** Returns whether this allows host sharing */
    public boolean allowHostSharing() { return allowHostSharing; }

    /** Returns whether to require access control for all clusters in this */
    public boolean requireAccessControl() {
        return requireAccessControl;
    }

    /** Returns the default account of this cloud */
    public CloudAccount account() {
        return account;
    }

    /** For testing purposes only */
    public static Cloud defaultCloud() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private CloudName name = CloudName.DEFAULT;
        private boolean dynamicProvisioning = false;
        private boolean allowHostSharing = true;
        private boolean requireAccessControl = false;
        private CloudAccount account = CloudAccount.empty;

        public Builder() {}

        public Builder name(CloudName name) {
            this.name = name;
            return this;
        }

        public Builder dynamicProvisioning(boolean dynamicProvisioning) {
            this.dynamicProvisioning = dynamicProvisioning;
            return this;
        }

        public Builder allowHostSharing(boolean allowHostSharing) {
            this.allowHostSharing = allowHostSharing;
            return this;
        }

        public Builder requireAccessControl(boolean requireAccessControl) {
            this.requireAccessControl = requireAccessControl;
            return this;
        }

        public Builder account(CloudAccount account) {
            this.account = account;
            return this;
        }

        public Cloud build() {
            return new Cloud(name, dynamicProvisioning, allowHostSharing, requireAccessControl, account);
        }

    }

    @Override
    public String toString() {
        return "cloud " + name;
    }

}
