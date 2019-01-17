// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.component.Version;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Collection of properties for a deployment.
 *
 * @author Ulf Lilleengen
 */
public class DeployProperties {

    private final boolean multitenant;
    private final ApplicationId applicationId;
    private final List<ConfigServerSpec> serverSpecs = new ArrayList<>();
    private final HostName loadBalancerName;
    private final URI ztsUrl;
    private final String athenzDnsSuffix;
    private final boolean hostedVespa;
    private final Version vespaVersion;
    private final boolean isBootstrap;
    private final boolean isFirstTimeDeployment;
    private final boolean useDedicatedNodeForLogserver;


    private DeployProperties(boolean multitenant,
                             ApplicationId applicationId,
                             List<ConfigServerSpec> configServerSpecs,
                             HostName loadBalancerName,
                             boolean hostedVespa,
                             URI ztsUrl,
                             String athenzDnsSuffix,
                             Version vespaVersion,
                             boolean isBootstrap,
                             boolean isFirstTimeDeployment,
                             boolean useDedicatedNodeForLogserver) {
        this.loadBalancerName = loadBalancerName;
        this.ztsUrl = ztsUrl;
        this.athenzDnsSuffix = athenzDnsSuffix;
        this.vespaVersion = vespaVersion;
        this.multitenant = multitenant || hostedVespa || Boolean.getBoolean("multitenant");
        this.applicationId = applicationId;
        this.serverSpecs.addAll(configServerSpecs);
        this.hostedVespa = hostedVespa;
        this.isBootstrap = isBootstrap;
        this.isFirstTimeDeployment = isFirstTimeDeployment;
        this.useDedicatedNodeForLogserver = useDedicatedNodeForLogserver;
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

    public HostName loadBalancerName() {
        return loadBalancerName;
    }

    public URI ztsUrl() {
        return ztsUrl;
    }

    public String athenzDnsSuffix() {
        return athenzDnsSuffix;
    }

    public boolean hostedVespa() {
        return hostedVespa;
    }

    /** Returns the config model version this is building */
    public Version vespaVersion() {
        return vespaVersion;
    }

    /** Returns whether this deployment happens during bootstrap *prepare* (not set on activate) */
    public boolean isBootstrap() { return isBootstrap; }

    /** Returns whether this is the first deployment for this application (used during *prepare*, not set on activate) */
    public boolean isFirstTimeDeployment() { return isFirstTimeDeployment; }

    public boolean useDedicatedNodeForLogserver() { return useDedicatedNodeForLogserver; }

    public static class Builder {

        private ApplicationId applicationId = ApplicationId.defaultId();
        private boolean multitenant = false;
        private List<ConfigServerSpec> configServerSpecs = new ArrayList<>();
        private HostName loadBalancerName;
        private URI ztsUrl;
        private String athenzDnsSuffix;
        private boolean hostedVespa = false;
        private Version vespaVersion = new Version(1, 0, 0);
        private boolean isBootstrap = false;
        private boolean isFirstTimeDeployment = false;
        private boolean useDedicatedNodeForLogserver = false;

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

        public Builder loadBalancerName(HostName loadBalancerName) {
            this.loadBalancerName = loadBalancerName;
            return this;
        }

        public Builder athenzDnsSuffix(String athenzDnsSuffix) {
            this.athenzDnsSuffix = athenzDnsSuffix;
            return this;
        }

        public Builder ztsUrl(URI ztsUrl) {
            this.ztsUrl = ztsUrl;
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

        public Builder isBootstrap(boolean isBootstrap) {
            this.isBootstrap = isBootstrap;
            return this;
        }

        public Builder isFirstTimeDeployment(boolean isFirstTimeDeployment) {
            this.isFirstTimeDeployment = isFirstTimeDeployment;
            return this;
        }

        public Builder useDedicatedNodeForLogserver(boolean useDedicatedNodeForLogserver) {
            this.useDedicatedNodeForLogserver = useDedicatedNodeForLogserver;
            return this;
        }

        public DeployProperties build() {
            return new DeployProperties(multitenant, applicationId, configServerSpecs, loadBalancerName, hostedVespa,
                                        ztsUrl, athenzDnsSuffix, vespaVersion, isBootstrap, isFirstTimeDeployment,
                                        useDedicatedNodeForLogserver);
        }
    }

}
