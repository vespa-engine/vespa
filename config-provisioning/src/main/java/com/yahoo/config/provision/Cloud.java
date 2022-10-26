// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;
import java.util.Optional;

/**
 * Properties of the cloud service where the zone is deployed.
 *
 * @author mpolden
 */
public class Cloud {

    private final CloudName name;

    private final boolean dynamicProvisioning;
    private final boolean requireAccessControl;
    private final Optional<CloudAccount> account;

    private Cloud(CloudName name, boolean dynamicProvisioning, boolean requireAccessControl, Optional<CloudAccount> account) {
        this.name = Objects.requireNonNull(name);
        this.dynamicProvisioning = dynamicProvisioning;
        this.requireAccessControl = requireAccessControl;
        this.account = Objects.requireNonNull(account);
        if (name.equals(CloudName.AWS) && account.isEmpty()) {
            throw new IllegalArgumentException("Account must be set in cloud '" + name + "'");
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

    /** Returns whether to require access control for all clusters in this */
    public boolean requireAccessControl() {
        return requireAccessControl;
    }

    /** Returns the default account of this cloud, if any */
    public Optional<CloudAccount> account() {
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
        private boolean requireAccessControl = false;
        private CloudAccount account = null;

        public Builder() {}

        public Builder name(CloudName name) {
            this.name = name;
            return this;
        }

        public Builder dynamicProvisioning(boolean dynamicProvisioning) {
            this.dynamicProvisioning = dynamicProvisioning;
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
            return new Cloud(name, dynamicProvisioning, requireAccessControl, Optional.ofNullable(account));
        }

    }

    @Override
    public String toString() {
        return "cloud " + name;
    }

}
