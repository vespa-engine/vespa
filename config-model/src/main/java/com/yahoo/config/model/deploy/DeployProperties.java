// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Version;
import com.yahoo.config.provision.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection of properties for a deployment.
 *
 * @author lulf
 * @since 5.17
 */
public class DeployProperties {

    private final boolean multitenant;
    private final ApplicationId applicationId;
    private final List<ConfigServerSpec> serverSpecs = new ArrayList<>();
    private final String loadBalancerAddress;
    private final boolean hostedVespa;
    private final Version vespaVersion;
    private final Zone zone;

    private DeployProperties(boolean multitenant,
                             ApplicationId applicationId,
                             List<ConfigServerSpec> configServerSpecs,
                             String loadBalancerAddress,
                             boolean hostedVespa,
                             Version vespaVersion,
                             Zone zone) {
        this.loadBalancerAddress = loadBalancerAddress;
        this.vespaVersion = vespaVersion;
        this.zone = zone;
        this.multitenant = multitenant || hostedVespa || Boolean.getBoolean("multitenant");
        this.applicationId = applicationId;
        this.serverSpecs.addAll(configServerSpecs);
        this.hostedVespa = hostedVespa;
    }


    public boolean multitenant() {
        return multitenant;
    }

    public ApplicationId applicationId() {
        return applicationId;
    }

    public List<ConfigServerSpec> configServerSpecs() {
        return serverSpecs;
    }

    public String loadBalancerAddress() {
        return loadBalancerAddress;
    }

    public boolean hostedVespa() {
        return hostedVespa;
    }

    /** Returns the config model version this is building */
    public Version vespaVersion() {
        return vespaVersion;
    }

    public Zone zone() { return zone; }

    public static class Builder {

        private ApplicationId applicationId = ApplicationId.defaultId();
        private boolean multitenant = false;
        private List<ConfigServerSpec> configServerSpecs = new ArrayList<>();
        private String loadBalancerAddress;
        private boolean hostedVespa = false;
        private Version vespaVersion = Version.fromIntValues(1, 0, 0);
        private Zone zone = Zone.defaultZone();

        public Builder applicationId(ApplicationId applicationId) {
            this.applicationId = applicationId;
            return this;
        }

        public Builder multitenant(boolean multitenant) {
            this.multitenant = multitenant;
            return this;
        }

        public Builder configServerSpecs(List<ConfigServerSpec> configServerSpecs) {
            this.configServerSpecs = configServerSpecs;
            return this;
        }

        public Builder loadBalancerAddress(String loadBalancerAddress) {
            this.loadBalancerAddress = loadBalancerAddress;
            return this;
        }

        public Builder vespaVersion(Version version) {
            this.vespaVersion = version;
            return this;
        }

        public Builder hostedVespa(boolean hostedVespa) {
            this.hostedVespa = hostedVespa;
            return this;
        }

        public Builder zone(Zone zone) {
            this.zone = zone;
            return this;
        }

        public DeployProperties build() {
            return new DeployProperties(multitenant, applicationId, configServerSpecs, loadBalancerAddress, hostedVespa, vespaVersion, zone);
        }
    }

}
