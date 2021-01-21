// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.model.api.ApplicationRoles;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.Zone;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A test-only Properties class
 *
 * <p>Unfortunately this has to be placed in non-test source tree since lots of code already have test code (fix later)
 *
 * @author hakonhall
 */
@SuppressWarnings("deprecation")
public class TestProperties implements ModelContext.Properties, ModelContext.FeatureFlags {

    private boolean multitenant = false;
    private ApplicationId applicationId = ApplicationId.defaultId();
    private List<ConfigServerSpec> configServerSpecs = Collections.emptyList();
    private boolean hostedVespa = false;
    private Zone zone;
    private final Set<ContainerEndpoint> endpoints = Collections.emptySet();
    private boolean useDedicatedNodeForLogserver = false;
    private boolean useThreePhaseUpdates = false;
    private double defaultTermwiseLimit = 1.0;
    private String jvmGCOptions = null;
    private String sequencerType = "LATENCY";
    private String responseSequencerType = "ADAPTIVE";
    private int responseNumThreads = 2;
    private Optional<EndpointCertificateSecrets> endpointCertificateSecrets = Optional.empty();
    private AthenzDomain athenzDomain;
    private ApplicationRoles applicationRoles;
    private Quota quota = Quota.unlimited();
    private boolean useAccessControlTlsHandshakeClientAuth;
    private boolean useAsyncMessageHandlingOnSchedule = false;
    private int mergeChunkSize = 0x400000 - 0x1000; // 4M -4k
    private double feedConcurrency = 0.5;
    private boolean enableAutomaticReindexing = false;
    private boolean reconfigurableZookeeperServer = false;

    @Override public ModelContext.FeatureFlags featureFlags() { return this; }
    @Override public boolean multitenant() { return multitenant; }
    @Override public ApplicationId applicationId() { return applicationId; }
    @Override public List<ConfigServerSpec> configServerSpecs() { return configServerSpecs; }
    @Override public HostName loadBalancerName() { return null; }
    @Override public URI ztsUrl() { return null; }
    @Override public String athenzDnsSuffix() { return null; }
    @Override public boolean hostedVespa() { return hostedVespa; }
    @Override public Zone zone() { return zone; }
    @Override public Set<ContainerEndpoint> endpoints() { return endpoints; }
    @Override public String jvmGCOptions() { return jvmGCOptions; }
    @Override public String feedSequencerType() { return sequencerType; }
    @Override public boolean isBootstrap() { return false; }
    @Override public boolean isFirstTimeDeployment() { return false; }
    @Override public boolean useDedicatedNodeForLogserver() { return useDedicatedNodeForLogserver; }
    @Override public Optional<EndpointCertificateSecrets> endpointCertificateSecrets() { return endpointCertificateSecrets; }
    @Override public double defaultTermwiseLimit() { return defaultTermwiseLimit; }
    @Override public boolean useThreePhaseUpdates() { return useThreePhaseUpdates; }
    @Override public Optional<AthenzDomain> athenzDomain() { return Optional.ofNullable(athenzDomain); }
    @Override public Optional<ApplicationRoles> applicationRoles() { return Optional.ofNullable(applicationRoles); }
    @Override public String responseSequencerType() { return responseSequencerType; }
    @Override public int defaultNumResponseThreads() { return responseNumThreads; }
    @Override public boolean skipCommunicationManagerThread() { return false; }
    @Override public boolean skipMbusRequestThread() { return false; }
    @Override public boolean skipMbusReplyThread() { return false; }
    @Override public Quota quota() { return quota; }
    @Override public boolean useAccessControlTlsHandshakeClientAuth() { return useAccessControlTlsHandshakeClientAuth; }
    @Override public boolean useAsyncMessageHandlingOnSchedule() { return useAsyncMessageHandlingOnSchedule; }
    @Override public int mergeChunkSize() { return mergeChunkSize; }
    @Override public double feedConcurrency() { return feedConcurrency; }
    @Override public boolean enableAutomaticReindexing() { return enableAutomaticReindexing; }
    @Override public boolean reconfigurableZookeeperServer() { return reconfigurableZookeeperServer; }

    public TestProperties setFeedConcurrency(double feedConcurrency) {
        this.feedConcurrency = feedConcurrency;
        return this;
    }

    public TestProperties setMergeChunkSize(int size) {
        mergeChunkSize = size;
        return this;
    }

    public TestProperties setAsyncMessageHandlingOnSchedule(boolean value) {
        useAsyncMessageHandlingOnSchedule = value;
        return this;
    }

    public TestProperties setJvmGCOptions(String gcOptions) {
        jvmGCOptions = gcOptions;
        return this;
    }
    public TestProperties setFeedSequencerType(String type) {
        sequencerType = type;
        return this;
    }
    public TestProperties setResponseSequencerType(String type) {
        responseSequencerType = type;
        return this;
    }
    public TestProperties setResponseNumThreads(int numThreads) {
        responseNumThreads = numThreads;
        return this;
    }
    public TestProperties setDefaultTermwiseLimit(double limit) {
        defaultTermwiseLimit = limit;
        return this;
    }

    public TestProperties setUseThreePhaseUpdates(boolean useThreePhaseUpdates) {
        this.useThreePhaseUpdates = useThreePhaseUpdates;
        return this;
    }

    public TestProperties setApplicationId(ApplicationId applicationId) {
        this.applicationId = applicationId;
        return this;
    }

    public TestProperties setHostedVespa(boolean hostedVespa) {
        this.hostedVespa = hostedVespa;
        return this;
    }

    public TestProperties setMultitenant(boolean multitenant) {
        this.multitenant = multitenant;
        return this;
    }

    public TestProperties setConfigServerSpecs(List<Spec> configServerSpecs) {
        this.configServerSpecs = ImmutableList.copyOf(configServerSpecs);
        return this;
    }

    public TestProperties setUseDedicatedNodeForLogserver(boolean useDedicatedNodeForLogserver) {
        this.useDedicatedNodeForLogserver = useDedicatedNodeForLogserver;
        return this;
    }

    public TestProperties setEndpointCertificateSecrets(Optional<EndpointCertificateSecrets> endpointCertificateSecrets) {
        this.endpointCertificateSecrets = endpointCertificateSecrets;
        return this;
    }

    public TestProperties setZone(Zone zone) {
        this.zone = zone;
        return this;
    }

    public TestProperties setAthenzDomain(AthenzDomain domain) {
        this.athenzDomain = domain;
        return this;
    }

    public TestProperties setApplicationRoles(ApplicationRoles applicationRoles) {
        this.applicationRoles = applicationRoles;
        return this;
    }

    public TestProperties setQuota(Quota quota) {
        this.quota = quota;
        return this;
    }

    public TestProperties useAccessControlTlsHandshakeClientAuth(boolean useAccessControlTlsHandshakeClientAuth) {
        this.useAccessControlTlsHandshakeClientAuth = useAccessControlTlsHandshakeClientAuth;
        return this;
    }

    public TestProperties enableAutomaticReindexing(boolean enabled) { this.enableAutomaticReindexing = enabled; return this; }

    public TestProperties reconfigurableZookeeperServer(boolean enabled) {
        this.reconfigurableZookeeperServer = enabled;
        return this;
    }


    public static class Spec implements ConfigServerSpec {

        private final String hostName;
        private final int configServerPort;
        private final int zooKeeperPort;

        public String getHostName() {
            return hostName;
        }

        public int getConfigServerPort() {
            return configServerPort;
        }

        public int getZooKeeperPort() {
            return zooKeeperPort;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ConfigServerSpec) {
                ConfigServerSpec other = (ConfigServerSpec)o;

                return hostName.equals(other.getHostName()) &&
                        configServerPort == other.getConfigServerPort() &&
                        zooKeeperPort == other.getZooKeeperPort();
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return hostName.hashCode();
        }

        public Spec(String hostName, int configServerPort, int zooKeeperPort) {
            this.hostName = hostName;
            this.configServerPort = configServerPort;
            this.zooKeeperPort = zooKeeperPort;
        }
    }

}
