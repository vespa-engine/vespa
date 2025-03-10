// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.model.api.TenantVault;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Collection;
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
public class TestProperties implements ModelContext.Properties, ModelContext.FeatureFlags {

    private boolean multitenant = false;
    private ApplicationId applicationId = ApplicationId.defaultId();
    private List<ConfigServerSpec> configServerSpecs = List.of();
    private boolean hostedVespa = false;
    private Zone zone = Zone.defaultZone();
    private Set<ContainerEndpoint> endpoints = Set.of();
    private boolean useDedicatedNodeForLogserver = false;
    private String jvmGCOptions = null;
    private String summaryDecodePolicy = "eager";
    private boolean firstTimeDeployment = false;
    private String responseSequencerType = "ADAPTIVE";
    private int responseNumThreads = 2;
    private Optional<EndpointCertificateSecrets> endpointCertificateSecrets = Optional.empty();
    private AthenzDomain athenzDomain;
    private Quota quota = Quota.unlimited();
    private boolean useAsyncMessageHandlingOnSchedule = true;
    private double feedConcurrency = 0.5;
    private double feedNiceness = 0.0;
    private int maxActivationInhibitedOutOfSyncGroups = 0;
    private List<TenantVault> tenantVaults = List.of();
    private List<TenantSecretStore> tenantSecretStores = List.of();
    private boolean allowDisableMtls = true;
    private List<X509Certificate> operatorCertificates = List.of();
    private double resourceLimitDisk = 0.75;
    private double resourceLimitMemory = 0.8;
    private double resourceLimitLowWatermarkDifference = 0.0;
    private double minNodeRatioPerGroup = 0.0;
    private int maxUnCommittedMemory = 123456;
    private String searchMmapAdvise = "NORMAL";
    private boolean useV8GeoPositions = true;
    private List<String> environmentVariables = List.of();
    private int mbus_java_num_targets = 2;
    private int mbus_java_events_before_wakeup = 1;
    private int mbus_cpp_num_targets = 2;
    private int mbus_cpp_events_before_wakeup = 1;
    private int rpc_num_targets = 2;
    private int rpc_events_before_wakeup = 1;
    private int mbus_network_threads = 1;
    private int heapSizePercentage = ApplicationContainerCluster.defaultHeapSizePercentageOfAvailableMemory;
    private Optional<CloudAccount> cloudAccount = Optional.empty();
    private boolean allowUserFilters = true;
    private List<DataplaneToken> dataplaneTokens;
    private int contentLayerMetadataFeatureLevel = 0;
    private int persistenceThreadMaxFeedOpBatchSize = 1;
    private boolean logserverOtelCol = false;
    private boolean symmetricPutAndActivateReplicaSelection = false;
    private int maxContentNodeMaintenanceOpConcurrency = -1;

    @Override public ModelContext.FeatureFlags featureFlags() { return this; }
    @Override public boolean multitenant() { return multitenant; }
    @Override public ApplicationId applicationId() { return applicationId; }
    @Override public List<ConfigServerSpec> configServerSpecs() { return configServerSpecs; }
    @Override public HostName loadBalancerName() { return null; }
    @Override public URI ztsUrl() { return null; }
    @Override public AthenzDomain tenantSecretDomain() { return null; }
    @Override public String athenzDnsSuffix() { return null; }
    @Override public boolean hostedVespa() { return hostedVespa; }
    @Override public Zone zone() { return zone; }
    @Override public Set<ContainerEndpoint> endpoints() { return endpoints; }
    @Override public String jvmGCOptions(Optional<ClusterSpec.Type> clusterType) { return jvmGCOptions; }
    @Override public boolean isBootstrap() { return false; }
    @Override public boolean isFirstTimeDeployment() { return firstTimeDeployment; }
    @Override public boolean useDedicatedNodeForLogserver() { return useDedicatedNodeForLogserver; }
    @Override public Optional<EndpointCertificateSecrets> endpointCertificateSecrets() { return endpointCertificateSecrets; }
    @Override public Optional<AthenzDomain> athenzDomain() { return Optional.ofNullable(athenzDomain); }
    @Override public String responseSequencerType() { return responseSequencerType; }
    @Override public int defaultNumResponseThreads() { return responseNumThreads; }
    @Override public Quota quota() { return quota; }
    @Override public boolean useAsyncMessageHandlingOnSchedule() { return useAsyncMessageHandlingOnSchedule; }
    @Override public double feedConcurrency() { return feedConcurrency; }
    @Override public double feedNiceness() { return feedNiceness; }
    @Override public int maxActivationInhibitedOutOfSyncGroups() { return maxActivationInhibitedOutOfSyncGroups; }
    @Override public List<TenantVault> tenantVaults() { return tenantVaults; }
    @Override public List<TenantSecretStore> tenantSecretStores() { return tenantSecretStores; }
    @Override public boolean allowDisableMtls() { return allowDisableMtls; }
    @Override public List<X509Certificate> operatorCertificates() { return operatorCertificates; }
    @Override public double resourceLimitDisk() { return resourceLimitDisk; }
    @Override public double resourceLimitMemory() { return resourceLimitMemory; }
    @Override public double resourceLimitLowWatermarkDifference() { return resourceLimitLowWatermarkDifference; }
    @Override public double minNodeRatioPerGroup() { return minNodeRatioPerGroup; }
    @Override public int maxUnCommittedMemory() { return maxUnCommittedMemory; }
    @Override public String searchMmapAdvise() { return searchMmapAdvise; }
    @Override public boolean useV8GeoPositions() { return useV8GeoPositions; }
    @Override public List<String> environmentVariables() { return environmentVariables; }
    @Override public int mbusNetworkThreads() { return mbus_network_threads; }
    @Override public int mbusJavaRpcNumTargets() { return mbus_java_num_targets; }
    @Override public int mbusJavaEventsBeforeWakeup() { return mbus_java_events_before_wakeup; }
    @Override public int mbusCppRpcNumTargets() { return mbus_cpp_num_targets; }
    @Override public int mbusCppEventsBeforeWakeup() { return mbus_cpp_events_before_wakeup; }
    @Override public int rpcNumTargets() { return rpc_num_targets; }
    @Override public int heapSizePercentage() { return heapSizePercentage; }
    @Override public int rpcEventsBeforeWakeup() { return rpc_events_before_wakeup; }
    @Override public String summaryDecodePolicy() { return summaryDecodePolicy; }
    @Override public Optional<CloudAccount> cloudAccount() { return cloudAccount; }
    @Override public boolean allowUserFilters() { return allowUserFilters; }
    @Override public List<DataplaneToken> dataplaneTokens() { return dataplaneTokens; }
    @Override public int contentLayerMetadataFeatureLevel() { return contentLayerMetadataFeatureLevel; }
    @Override public int persistenceThreadMaxFeedOpBatchSize() { return persistenceThreadMaxFeedOpBatchSize; }
    @Override public boolean logserverOtelCol() { return logserverOtelCol; }
    @Override public boolean symmetricPutAndActivateReplicaSelection() { return symmetricPutAndActivateReplicaSelection; }
    @Override public int maxContentNodeMaintenanceOpConcurrency() { return maxContentNodeMaintenanceOpConcurrency; }

    public TestProperties maxUnCommittedMemory(int maxUnCommittedMemory) {
        this.maxUnCommittedMemory = maxUnCommittedMemory;
        return this;
    }

    public TestProperties setSearchMmapAdvise(String searchMmapAdvise) {
        this.searchMmapAdvise = searchMmapAdvise;
        return this;
    }

    public TestProperties setFeedConcurrency(double feedConcurrency) {
        this.feedConcurrency = feedConcurrency;
        return this;
    }

    public TestProperties setFeedNiceness(double feedNiceness) {
        this.feedNiceness = feedNiceness;
        return this;
    }

    public TestProperties setHeapSizePercentage(int percentage) {
        this.heapSizePercentage = percentage;
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

    public TestProperties setSummaryDecodePolicy(String type) {
        summaryDecodePolicy = type;
        return this;
    }

    public TestProperties setResponseSequencerType(String type) {
        responseSequencerType = type;
        return this;
    }
    public TestProperties setFirstTimeDeployment(boolean firstTimeDeployment) {
        this.firstTimeDeployment = firstTimeDeployment;
        return this;
    }
    public TestProperties setResponseNumThreads(int numThreads) {
        responseNumThreads = numThreads;
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
        this.configServerSpecs = List.copyOf(configServerSpecs);
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

    public TestProperties setQuota(Quota quota) {
        this.quota = quota;
        return this;
    }

    public TestProperties maxActivationInhibitedOutOfSyncGroups(int nGroups) {
        maxActivationInhibitedOutOfSyncGroups = nGroups;
        return this;
    }

    public TestProperties setTenantVaults(List<TenantVault> tenantVaults) {
        this.tenantVaults = List.copyOf(tenantVaults);
        return this;
    }

    public TestProperties setTenantSecretStores(List<TenantSecretStore> secretStores) {
        this.tenantSecretStores = List.copyOf(secretStores);
        return this;
    }

    public TestProperties allowDisableMtls(boolean value) {
        this.allowDisableMtls = value;
        return this;
    }

    public TestProperties setOperatorCertificates(List<X509Certificate> operatorCertificates) {
        this.operatorCertificates = List.copyOf(operatorCertificates);
        return this;
    }

    public TestProperties setResourceLimitDisk(double value) {
        this.resourceLimitDisk = value;
        return this;
    }

    public TestProperties setResourceLimitMemory(double value) {
        this.resourceLimitMemory = value;
        return this;
    }

    public TestProperties setResourceLimitLowWatermarkDifference(double value) {
        this.resourceLimitLowWatermarkDifference = value;
        return this;
    }

    public TestProperties setMinNodeRatioPerGroup(double value) {
        this.minNodeRatioPerGroup = value;
        return this;
    }

    public TestProperties setUseV8GeoPositions(boolean value) {
        this.useV8GeoPositions = value;
        return this;
    }

    public TestProperties setEnvironmentVariables(List<String> value) {
        this.environmentVariables = value;
        return this;
    }

    public TestProperties setMbusNetworkThreads(int value) {
        this.mbus_network_threads = value;
        return this;
    }
    public TestProperties setMbusJavaRpcNumTargets(int value) {
        this.mbus_java_num_targets = value;
        return this;
    }
    public TestProperties setMbusJavaEventsBeforeWakeup(int value) {
        this.mbus_java_events_before_wakeup = value;
        return this;
    }
    public TestProperties setMbusCppEventsBeforeWakeup(int value) {
        this.mbus_cpp_events_before_wakeup = value;
        return this;
    }
    public TestProperties setMbusCppRpcNumTargets(int value) {
        this.mbus_cpp_num_targets = value;
        return this;
    }
    public TestProperties setRpcNumTargets(int value) {
        this.rpc_num_targets = value;
        return this;
    }
    public TestProperties setRpcEventsBeforeWakeup(int value) {
        this.rpc_events_before_wakeup = value;
        return this;
    }

    public TestProperties setCloudAccount(CloudAccount cloudAccount) {
        this.cloudAccount = Optional.ofNullable(cloudAccount);
        return this;
    }

    public TestProperties setAllowUserFilters(boolean b) { this.allowUserFilters = b; return this; }

    public TestProperties setDataplaneTokens(Collection<DataplaneToken> tokens) {
        this.dataplaneTokens = List.copyOf(tokens);
        return this;
    }

    public TestProperties setContentLayerMetadataFeatureLevel(int level) {
        this.contentLayerMetadataFeatureLevel = level;
        return this;
    }

    public TestProperties setPersistenceThreadMaxFeedOpBatchSize(int maxBatchSize) {
        this.persistenceThreadMaxFeedOpBatchSize = maxBatchSize;
        return this;
    }

    public TestProperties setLogserverOtelCol(boolean logserverOtelCol) {
        this.logserverOtelCol = logserverOtelCol;
        return this;
    }

    public TestProperties setSymmetricPutAndActivateReplicaSelection(boolean symmetricReplicaSelection) {
        this.symmetricPutAndActivateReplicaSelection = symmetricReplicaSelection;
        return this;
    }

    public TestProperties setContainerEndpoints(Set<ContainerEndpoint> containerEndpoints) {
        this.endpoints = containerEndpoints;
        return this;
    }

    public TestProperties setMaxContentNodeMaintenanceOpConcurrency(int maxConcurrency) {
        this.maxContentNodeMaintenanceOpConcurrency = maxConcurrency;
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
            if (o instanceof ConfigServerSpec rhsSpec) {

                return hostName.equals(rhsSpec.getHostName()) &&
                        configServerPort == rhsSpec.getConfigServerPort() &&
                        zooKeeperPort == rhsSpec.getZooKeeperPort();
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
