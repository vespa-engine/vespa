// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private final boolean allowHostSharing;
    private final boolean allowEnclave;
    private final boolean requireAccessControl;
    private final CloudAccount account;
    private final Optional<String> snapshotPrivateKeySecretName;

    private Cloud(CloudName name, boolean dynamicProvisioning, boolean allowHostSharing, boolean allowEnclave,
                  boolean requireAccessControl, CloudAccount account, Optional<String> snapshotPrivateKeySecretName) {
        this.name = Objects.requireNonNull(name);
        this.dynamicProvisioning = dynamicProvisioning;
        this.allowHostSharing = allowHostSharing;
        this.allowEnclave = allowEnclave;
        this.requireAccessControl = requireAccessControl;
        this.account = Objects.requireNonNull(account);
        this.snapshotPrivateKeySecretName = Objects.requireNonNull(snapshotPrivateKeySecretName);
        if (allowEnclave && account.isUnspecified()) {
            throw new IllegalArgumentException("Account must be non-empty in '" + name + "'");
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

    /** Returns whether this allows deployments to enclave */
    public boolean allowEnclave() { return allowEnclave; }

    /** Returns whether to require access control for all clusters in this */
    public boolean requireAccessControl() {
        return requireAccessControl;
    }

    /** Returns the default account of this cloud */
    public CloudAccount account() {
        return account;
    }

    /** Returns whether load balancers use proxy protocol v1 or not (e.g. use source NAT). */
    public boolean useProxyProtocol() {
        return !name.equals(CloudName.AZURE);
    }

    /** Name of private key secret used for sealing snapshot encryption keys */
    public Optional<String> snapshotPrivateKeySecretName() {
        return snapshotPrivateKeySecretName;
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
        private boolean allowEnclave = false;
        private boolean requireAccessControl = false;
        private CloudAccount account = CloudAccount.empty;
        private Optional<String> snapshotPrivateKeySecretName = Optional.empty();

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

        public Builder allowEnclave(boolean allowEnclave) {
            this.allowEnclave = allowEnclave;
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

        public Builder snapshotPrivateKeySecretName(String snapshotPrivateKeySecretName) {
            this.snapshotPrivateKeySecretName = Optional.of(snapshotPrivateKeySecretName)
                                                        .filter(s -> !s.isEmpty());
            return this;
        }

        public Cloud build() {
            return new Cloud(name, dynamicProvisioning, allowHostSharing, allowEnclave, requireAccessControl, account, snapshotPrivateKeySecretName);
        }

    }

    @Override
    public String toString() {
        return "cloud " + name;
    }

}
