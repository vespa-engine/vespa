// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.model.api.Reindexing;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.model.api.TenantVault;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources.Architecture;
import com.yahoo.config.provision.SharedHosts;
import com.yahoo.vespa.flags.DoubleFlag;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.flags.custom.Sidecars;

import java.io.File;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static com.yahoo.vespa.config.server.ConfigServerSpec.fromConfig;
import static com.yahoo.vespa.flags.Dimension.CLUSTER_ID;
import static com.yahoo.vespa.flags.Dimension.CLUSTER_TYPE;

/**
 * Implementation of {@link ModelContext} for configserver.
 *
 * @author Ulf Lilleengen
 */
public class ModelContextImpl implements ModelContext {

    private final ApplicationPackage applicationPackage;
    private final Optional<Model> previousModel;
    private final DeployLogger deployLogger;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final FileRegistry fileRegistry;
    private final ExecutorService executor;
    private final HostProvisioner hostProvisioner;
    private final Provisioned provisioned;
    private final Optional<? extends Reindexing> reindexing;
    private final ModelContext.Properties properties;
    private final Optional<File> appDir;
    private final OnnxModelCost onnxModelCost;

    private final Optional<DockerImage> wantedDockerImageRepository;

    /** The version of Vespa we are building a model for */
    private final Version modelVespaVersion;

    /**
     * The Version of Vespa this model should specify that nodes should use. Note that this
     * is separate from the version of this model, as upgrades are not immediate.
     * We may build a config model of Vespa version "a" which specifies that nodes should
     * use Vespa version "b". The "a" model will then be used by nodes who have not yet
     * upgraded to version "b".
     */
    private final Version wantedNodeVespaVersion;

    public ModelContextImpl(ApplicationPackage applicationPackage,
                            Optional<Model> previousModel,
                            DeployLogger deployLogger,
                            ConfigDefinitionRepo configDefinitionRepo,
                            FileRegistry fileRegistry,
                            ExecutorService executor,
                            Optional<? extends Reindexing> reindexing,
                            HostProvisioner hostProvisioner,
                            Provisioned provisioned,
                            ModelContext.Properties properties,
                            Optional<File> appDir,
                            OnnxModelCost onnxModelCost,
                            Optional<DockerImage> wantedDockerImageRepository,
                            Version modelVespaVersion,
                            Version wantedNodeVespaVersion) {
        this.applicationPackage = applicationPackage;
        this.previousModel = previousModel;
        this.deployLogger = deployLogger;
        this.configDefinitionRepo = configDefinitionRepo;
        this.fileRegistry = fileRegistry;
        this.executor = executor;
        this.reindexing = reindexing;
        this.hostProvisioner = hostProvisioner;
        this.provisioned = provisioned;
        this.properties = properties;
        this.appDir = appDir;
        this.wantedDockerImageRepository = wantedDockerImageRepository;
        this.modelVespaVersion = modelVespaVersion;
        this.wantedNodeVespaVersion = wantedNodeVespaVersion;
        this.onnxModelCost = onnxModelCost;
    }

    @Override
    public ApplicationPackage applicationPackage() { return applicationPackage; }

    @Override
    public Optional<Model> previousModel() { return previousModel; }

    /**
     * Returns the host provisioner to use, or empty to use the default provisioner,
     * creating hosts from the application package defined hosts
     */
    @Override
    public HostProvisioner getHostProvisioner() { return hostProvisioner; }

    @Override
    public Provisioned provisioned() { return provisioned; }

    @Override
    public DeployLogger deployLogger() { return deployLogger; }

    @Override
    public ConfigDefinitionRepo configDefinitionRepo() { return configDefinitionRepo; }

    @Override
    public FileRegistry getFileRegistry() { return fileRegistry; }

    @Override
    public ExecutorService getExecutor() {
        return executor;
    }

    @Override
    public Optional<? extends Reindexing> reindexing() { return  reindexing; }

    @Override
    public ModelContext.Properties properties() { return properties; }

    @Override
    public Optional<File> appDir() { return appDir; }

    @Override public OnnxModelCost onnxModelCost() { return onnxModelCost; }

    @Override
    public Optional<DockerImage> wantedDockerImageRepo() { return wantedDockerImageRepository; }

    @Override
    public Version modelVespaVersion() { return modelVespaVersion; }

    @Override
    public Version wantedNodeVespaVersion() { return wantedNodeVespaVersion; }

    public static class FeatureFlags implements ModelContext.FeatureFlags {

        private final boolean useNonPublicEndpointForTest;
        private final double queryDispatchWarmup;
        private final String responseSequencer;
        private final int numResponseThreads;
        private final boolean useAsyncMessageHandlingOnSchedule;
        private final double feedConcurrency;
        private final double feedNiceness;
        private final List<String> allowedAthenzProxyIdentities;
        private final int maxActivationInhibitedOutOfSyncGroups;
        private final double resourceLimitDisk;
        private final double resourceLimitMemory;
        private final double resourceLimitAddressSpace;
        private final boolean containerDumpHeapOnShutdownTimeout;
        private final int maxUnCommittedMemory;
        private final boolean forwardIssuesAsErrors;
        private final List<String> ignoredHttpUserAgents;
        private final int mbus_network_threads;
        private final int mbus_java_num_targets;
        private final int mbus_java_events_before_wakeup;
        private final int mbus_cpp_num_targets;
        private final int mbus_cpp_events_before_wakeup;
        private final int rpc_num_targets;
        private final int rpc_events_before_wakeup;
        private final boolean sortBlueprintsByCost;
        private final int contentLayerMetadataFeatureLevel;
        private final String unknownConfigDefinition;
        private final boolean logserverOtelCol;
        private final SharedHosts sharedHosts;
        private final Architecture adminClusterArchitecture;
        private final double logserverNodeMemory;
        private final double clusterControllerNodeMemory;
        private final boolean useLegacyWandQueryParsing;
        private final boolean useSimpleAnnotations;
        private final boolean sendProtobufQuerytree;
        private final boolean forwardAllLogLevels;
        private final long zookeeperPreAllocSize;
        private final int maxContentNodeMaintenanceOpConcurrency;
        private final int maxDocumentOperationRequestSizeMib;
        private final Sidecars sidecarsForTest;
        private final boolean useTriton;
        private final boolean ignoreConnectivityChecksAtStartup;
        private final int searchCoreMaxOutstandingMoveOps;
        private final double docprocHandlerThreadpool;
        private final boolean applyOnRestartForApplicationMetadataConfig;
        private final DoubleFlag autoscalerTargetWriteCpuPercentageFlag;
        private final IntFlag heapSizePercentageFlag;
        private final double searchNodeReservedDiskSpaceFactor;

        public FeatureFlags(FlagSource source, ApplicationId appId, Version version) {
            this.useNonPublicEndpointForTest = Flags.USE_NON_PUBLIC_ENDPOINT_FOR_TEST.bindTo(source).with(appId).with(version).value();
            this.responseSequencer = Flags.RESPONSE_SEQUENCER_TYPE.bindTo(source).with(appId).with(version).value();
            this.numResponseThreads = Flags.RESPONSE_NUM_THREADS.bindTo(source).with(appId).with(version).value();
            this.useAsyncMessageHandlingOnSchedule = Flags.USE_ASYNC_MESSAGE_HANDLING_ON_SCHEDULE.bindTo(source).with(appId).with(version).value();
            this.feedConcurrency = PermanentFlags.FEED_CONCURRENCY.bindTo(source).with(appId).with(version).value();
            this.feedNiceness = PermanentFlags.FEED_NICENESS.bindTo(source).with(appId).with(version).value();
            this.mbus_network_threads = Flags.MBUS_NUM_NETWORK_THREADS.bindTo(source).with(appId).with(version).value();
            this.allowedAthenzProxyIdentities = PermanentFlags.ALLOWED_ATHENZ_PROXY_IDENTITIES.bindTo(source).with(appId).with(version).value();
            this.maxActivationInhibitedOutOfSyncGroups = Flags.MAX_ACTIVATION_INHIBITED_OUT_OF_SYNC_GROUPS.bindTo(source).with(appId).with(version).value();
            this.resourceLimitDisk = PermanentFlags.RESOURCE_LIMIT_DISK.bindTo(source).with(appId).with(version).value();
            this.resourceLimitMemory = PermanentFlags.RESOURCE_LIMIT_MEMORY.bindTo(source).with(appId).with(version).value();
            this.resourceLimitAddressSpace = PermanentFlags.RESOURCE_LIMIT_ADDRESS_SPACE.bindTo(source).with(appId).with(version).value();
            this.containerDumpHeapOnShutdownTimeout = PermanentFlags.CONTAINER_DUMP_HEAP_ON_SHUTDOWN_TIMEOUT.bindTo(source).with(appId).with(version).value();
            this.maxUnCommittedMemory = PermanentFlags.MAX_UNCOMMITTED_MEMORY.bindTo(source).with(appId).with(version).value();
            this.forwardIssuesAsErrors = PermanentFlags.FORWARD_ISSUES_AS_ERRORS.bindTo(source).with(appId).with(version).value();
            this.ignoredHttpUserAgents = PermanentFlags.IGNORED_HTTP_USER_AGENTS.bindTo(source).with(appId).with(version).value();
            this.mbus_java_num_targets = Flags.MBUS_JAVA_NUM_TARGETS.bindTo(source).with(appId).with(version).value();
            this.mbus_java_events_before_wakeup = Flags.MBUS_JAVA_EVENTS_BEFORE_WAKEUP.bindTo(source).with(appId).with(version).value();
            this.mbus_cpp_num_targets = Flags.MBUS_CPP_NUM_TARGETS.bindTo(source).with(appId).with(version).value();
            this.mbus_cpp_events_before_wakeup = Flags.MBUS_CPP_EVENTS_BEFORE_WAKEUP.bindTo(source).with(appId).with(version).value();
            this.rpc_num_targets = Flags.RPC_NUM_TARGETS.bindTo(source).with(appId).with(version).value();
            this.rpc_events_before_wakeup = Flags.RPC_EVENTS_BEFORE_WAKEUP.bindTo(source).with(appId).with(version).value();
            this.queryDispatchWarmup = PermanentFlags.QUERY_DISPATCH_WARMUP.bindTo(source).with(appId).with(version).value();
            this.heapSizePercentageFlag = PermanentFlags.HEAP_SIZE_PERCENTAGE.bindTo(source).with(appId).with(version);
            this.contentLayerMetadataFeatureLevel = Flags.CONTENT_LAYER_METADATA_FEATURE_LEVEL.bindTo(source).with(appId).with(version).value();
            this.unknownConfigDefinition = PermanentFlags.UNKNOWN_CONFIG_DEFINITION.bindTo(source).with(appId).with(version).value();
            this.sortBlueprintsByCost = PermanentFlags.SORT_BLUEPRINTS_BY_COST.bindTo(source).with(appId).with(version).value();
            this.logserverOtelCol = Flags.LOGSERVER_OTELCOL_AGENT.bindTo(source).with(appId).with(version).value();
            this.sharedHosts = PermanentFlags.SHARED_HOST.bindTo(source).with(appId).with(version).value();
            this.adminClusterArchitecture = Architecture.valueOf(PermanentFlags.ADMIN_CLUSTER_NODE_ARCHITECTURE.bindTo(source).with(appId).with(version).value());
            this.logserverNodeMemory = PermanentFlags.LOGSERVER_NODE_MEMORY.bindTo(source).with(appId).with(version).value();
            this.clusterControllerNodeMemory = PermanentFlags.CLUSTER_CONTROLLER_NODE_MEMORY.bindTo(source).with(appId).with(version).value();
            this.useLegacyWandQueryParsing = Flags.USE_LEGACY_WAND_QUERY_PARSING.bindTo(source).with(appId).with(version).value();
            this.useSimpleAnnotations = Flags.USE_SIMPLE_ANNOTATIONS.bindTo(source).with(appId).with(version).value();
            this.sendProtobufQuerytree = Flags.SEND_PROTOBUF_QUERYTREE.bindTo(source).with(appId).with(version).value();
            this.forwardAllLogLevels = PermanentFlags.FORWARD_ALL_LOG_LEVELS.bindTo(source).with(appId).with(version).value();
            this.zookeeperPreAllocSize = Flags.ZOOKEEPER_PRE_ALLOC_SIZE_KIB.bindTo(source).value();
            this.maxContentNodeMaintenanceOpConcurrency = Flags.MAX_CONTENT_NODE_MAINTENANCE_OP_CONCURRENCY.bindTo(source).with(appId).with(version).value();
            this.maxDocumentOperationRequestSizeMib = Flags.MAX_DOCUMENT_OPERATION_REQUEST_SIZE_MIB.bindTo(source).with(appId).with(version).value();
            this.sidecarsForTest = Flags.SIDECARS_FOR_TEST.bindTo(source).with(appId).with(version).value();
            this.useTriton = Flags.USE_TRITON.bindTo(source).with(appId).with(version).value();
            this.ignoreConnectivityChecksAtStartup = PermanentFlags.IGNORE_CONNECTIVITY_CHECKS_AT_STARTUP.bindTo(source).with(appId).value();
            this.searchCoreMaxOutstandingMoveOps = Flags.SEARCH_CORE_MAX_OUTSTANDING_MOVE_OPS.bindTo(source).with(appId).with(version).value();
            this.docprocHandlerThreadpool = Flags.DOCPROC_HANDLER_THREADPOOL.bindTo(source).with(appId).with(version).value();
            this.applyOnRestartForApplicationMetadataConfig = Flags.APPLY_ON_RESTART_FOR_APPLICATION_METADATA_CONFIG.bindTo(source).with(appId).with(version).value();
            this.autoscalerTargetWriteCpuPercentageFlag = Flags.AUTOSCALER_TARGET_WRITE_CPU_PERCENTAGE.bindTo(source).with(appId).with(version);
            this.searchNodeReservedDiskSpaceFactor = Flags.SEARCHNODE_RESERVED_DISK_SPACE_FACTOR.bindTo(source).with(appId).with(version).value();
        }

        @Override public boolean useNonPublicEndpointForTest() { return useNonPublicEndpointForTest; }
        @Override public int heapSizePercentage(Optional<String> clusterId) {
            return clusterId.map(id -> heapSizePercentageFlag.with(ClusterSpec.Id.from(id)).value())
                            .orElseGet(heapSizePercentageFlag::value); }
        @Override public double queryDispatchWarmup() { return queryDispatchWarmup; }
        @Override public String responseSequencerType() { return responseSequencer; }
        @Override public int defaultNumResponseThreads() { return numResponseThreads; }
        @Override public boolean useAsyncMessageHandlingOnSchedule() { return useAsyncMessageHandlingOnSchedule; }
        @Override public double feedConcurrency() { return feedConcurrency; }
        @Override public double feedNiceness() { return feedNiceness; }
        @Override public int mbusNetworkThreads() { return mbus_network_threads; }
        @Override public List<String> allowedAthenzProxyIdentities() { return allowedAthenzProxyIdentities; }
        @Override public int maxActivationInhibitedOutOfSyncGroups() { return maxActivationInhibitedOutOfSyncGroups; }
        @Override public double resourceLimitDisk() { return resourceLimitDisk; }
        @Override public double resourceLimitMemory() { return resourceLimitMemory; }
        @Override public double resourceLimitAddressSpace() { return resourceLimitAddressSpace; }
        @Override public boolean containerDumpHeapOnShutdownTimeout() { return containerDumpHeapOnShutdownTimeout; }
        @Override public int maxUnCommittedMemory() { return maxUnCommittedMemory; }
        @Override public boolean forwardIssuesAsErrors() { return forwardIssuesAsErrors; }
        @Override public List<String> ignoredHttpUserAgents() { return ignoredHttpUserAgents; }
        @Override public int mbusJavaRpcNumTargets() { return mbus_java_num_targets; }
        @Override public int mbusJavaEventsBeforeWakeup() { return mbus_java_events_before_wakeup; }
        @Override public int mbusCppRpcNumTargets() { return mbus_cpp_num_targets; }
        @Override public int mbusCppEventsBeforeWakeup() { return mbus_cpp_events_before_wakeup; }
        @Override public int rpcNumTargets() { return rpc_num_targets; }
        @Override public int rpcEventsBeforeWakeup() { return rpc_events_before_wakeup; }
        @Override public int contentLayerMetadataFeatureLevel() { return contentLayerMetadataFeatureLevel; }
        @Override public String unknownConfigDefinition() { return unknownConfigDefinition; }
        @Override public boolean sortBlueprintsByCost() { return sortBlueprintsByCost; }
        @Override public boolean logserverOtelCol() { return logserverOtelCol; }
        @Override public SharedHosts sharedHosts() { return sharedHosts; }
        @Override public Architecture adminClusterArchitecture() { return adminClusterArchitecture; }
        @Override public double logserverNodeMemory() { return logserverNodeMemory; }
        @Override public double clusterControllerNodeMemory() { return clusterControllerNodeMemory; }
        @Override public boolean useLegacyWandQueryParsing() { return useLegacyWandQueryParsing; }
        @Override public boolean useSimpleAnnotations() { return useSimpleAnnotations; }
        @Override public boolean sendProtobufQuerytree() { return sendProtobufQuerytree; }
        @Override public boolean forwardAllLogLevels() { return forwardAllLogLevels; }
        @Override public long zookeeperPreAllocSize() { return zookeeperPreAllocSize; }
        @Override public int maxContentNodeMaintenanceOpConcurrency() { return maxContentNodeMaintenanceOpConcurrency; }
        @Override public int maxDocumentOperationRequestSizeMib() { return maxDocumentOperationRequestSizeMib; }
        @Override public Object sidecarsForTest() { return sidecarsForTest; }
        @Override public boolean useTriton() { return useTriton; }
        @Override public boolean ignoreConnectivityChecksAtStartup() { return ignoreConnectivityChecksAtStartup; }
        @Override public int searchCoreMaxOutstandingMoveOps() { return searchCoreMaxOutstandingMoveOps; }
        @Override public double docprocHandlerThreadpool() { return docprocHandlerThreadpool; }
        @Override public boolean applyOnRestartForApplicationMetadataConfig() { return applyOnRestartForApplicationMetadataConfig; }
        @Override public double autoscalerTargetWriteCpuPercentage(Optional<String> clusterId) {
            return clusterId.map(id -> autoscalerTargetWriteCpuPercentageFlag.with(ClusterSpec.Id.from(id)).value())
                            .orElseGet(autoscalerTargetWriteCpuPercentageFlag::value);
        }
        @Override public double searchNodeReservedDiskSpaceFactor() { return searchNodeReservedDiskSpaceFactor; }
    }

    public static class Properties implements ModelContext.Properties {

        private final ModelContext.FeatureFlags featureFlags;
        private final ApplicationId applicationId;
        private final boolean multitenant;
        private final List<ConfigServerSpec> configServerSpecs;
        private final HostName loadBalancerName;
        private final URI ztsUrl;
        private final String tenantSecretDomain;
        private final String athenzDnsSuffix;
        private final boolean hostedVespa;
        private final Set<ContainerEndpoint> endpoints;
        private final boolean isBootstrap;
        private final boolean isFirstTimeDeployment;
        private final Optional<EndpointCertificateSecrets> endpointCertificateSecrets;
        private final Optional<AthenzDomain> athenzDomain;
        private final Quota quota;
        private final List<TenantVault> tenantVaults;
        private final List<TenantSecretStore> tenantSecretStores;
        private final StringFlag jvmGCOptionsFlag;
        private final IntFlag searchNodeInitializerThreadsFlag;
        private final boolean allowDisableMtls;
        private final List<X509Certificate> operatorCertificates;
        private final List<String> tlsCiphersOverride;
        private final List<String> environmentVariables;
        private final Optional<CloudAccount> cloudAccount;
        private final List<DataplaneToken> dataplaneTokens;
        private final boolean allowUserFilters;
        private final Duration endpointConnectionTtl;
        private final List<String> requestPrefixForLoggingContent;
        private final List<String> jdiscHttpComplianceViolations;
        private final StringFlag mallocImplFlag;
        private final IntFlag heapSizePercentageFlag;

        public Properties(ApplicationId applicationId,
                          Version modelVersion,
                          ConfigserverConfig configserverConfig,
                          Set<ContainerEndpoint> endpoints,
                          boolean isBootstrap,
                          boolean isFirstTimeDeployment,
                          FlagSource flagSource,
                          Optional<EndpointCertificateSecrets> endpointCertificateSecrets,
                          Optional<AthenzDomain> athenzDomain,
                          Optional<Quota> maybeQuota,
                          List<TenantVault> tenantVaults,
                          List<TenantSecretStore> tenantSecretStores,
                          List<X509Certificate> operatorCertificates,
                          Optional<CloudAccount> cloudAccount,
                          List<DataplaneToken> dataplaneTokens) {
            this.featureFlags = new FeatureFlags(flagSource, applicationId, modelVersion);
            this.applicationId = applicationId;
            this.multitenant = configserverConfig.multitenant() || configserverConfig.hostedVespa() || Boolean.getBoolean("multitenant");
            this.configServerSpecs = fromConfig(configserverConfig);
            this.loadBalancerName = configserverConfig.loadBalancerAddress().isEmpty() ? null : HostName.of(configserverConfig.loadBalancerAddress());
            this.ztsUrl = configserverConfig.ztsUrl() != null ? URI.create(configserverConfig.ztsUrl()) : null;
            this.tenantSecretDomain = configserverConfig.tenantSecretDomain();
            this.athenzDnsSuffix = configserverConfig.athenzDnsSuffix();
            this.hostedVespa = configserverConfig.hostedVespa();
            this.endpoints = endpoints;
            this.isBootstrap = isBootstrap;
            this.isFirstTimeDeployment = isFirstTimeDeployment;
            this.endpointCertificateSecrets = endpointCertificateSecrets;
            this.athenzDomain = athenzDomain;
            this.quota = maybeQuota.orElseGet(Quota::unlimited);
            this.tenantVaults = tenantVaults;
            this.tenantSecretStores = tenantSecretStores;
            this.jvmGCOptionsFlag = PermanentFlags.JVM_GC_OPTIONS.bindTo(flagSource)
                    .with(applicationId)
                    .withVersion(Optional.of(modelVersion));
            this.searchNodeInitializerThreadsFlag = PermanentFlags.SEARCHNODE_INITIALIZER_THREADS.bindTo(flagSource).with(applicationId);
            this.allowDisableMtls = PermanentFlags.ALLOW_DISABLE_MTLS.bindTo(flagSource).with(applicationId).value();
            this.operatorCertificates = operatorCertificates;
            this.tlsCiphersOverride = PermanentFlags.TLS_CIPHERS_OVERRIDE.bindTo(flagSource).with(applicationId).value();
            this.environmentVariables = PermanentFlags.ENVIRONMENT_VARIABLES.bindTo(flagSource).with(applicationId).value();
            this.cloudAccount = cloudAccount;
            this.allowUserFilters = PermanentFlags.ALLOW_USER_FILTERS.bindTo(flagSource).with(applicationId).value();
            this.endpointConnectionTtl = Duration.ofSeconds(PermanentFlags.ENDPOINT_CONNECTION_TTL.bindTo(flagSource).with(applicationId).value());
            this.dataplaneTokens = dataplaneTokens;
            this.requestPrefixForLoggingContent = PermanentFlags.LOG_REQUEST_CONTENT.bindTo(flagSource).with(applicationId).value();
            this.jdiscHttpComplianceViolations = PermanentFlags.JDISC_HTTP_COMPLIANCE_VIOLATIONS.bindTo(flagSource)
                    .with(applicationId).with(modelVersion).value();
            this.mallocImplFlag = Flags.VESPA_USE_MALLOC_IMPL.bindTo(flagSource)
                    .with(applicationId)
                    .withVersion(Optional.of(modelVersion));
            this.heapSizePercentageFlag = PermanentFlags.HEAP_SIZE_PERCENTAGE.bindTo(flagSource)
                                                                             .with(applicationId)
                                                                             .with(modelVersion);
        }

        @Override public ModelContext.FeatureFlags featureFlags() { return featureFlags; }

        @Override
        public boolean multitenant() { return multitenant; }

        @Override
        public ApplicationId applicationId() { return applicationId; }

        @Override
        public List<ConfigServerSpec> configServerSpecs() { return configServerSpecs; }

        @Override
        public HostName loadBalancerName() { return loadBalancerName; }

        @Override
        public URI ztsUrl() {
            return ztsUrl;
        }

        @Override
        public AthenzDomain tenantSecretDomain() {
            if (tenantSecretDomain.isEmpty())
                throw new IllegalArgumentException("Tenant secret domain is not set");
            return AthenzDomain.from(tenantSecretDomain);
        }

        @Override
        public String athenzDnsSuffix() {
            return athenzDnsSuffix;
        }

        @Override
        public boolean hostedVespa() { return hostedVespa; }

        @Override
        public Set<ContainerEndpoint> endpoints() { return endpoints; }

        @Override
        public boolean isBootstrap() { return isBootstrap; }

        @Override
        public boolean isFirstTimeDeployment() { return isFirstTimeDeployment; }

        @Override
        public Optional<EndpointCertificateSecrets> endpointCertificateSecrets() { return endpointCertificateSecrets; }

        @Override
        public Optional<AthenzDomain> athenzDomain() { return athenzDomain; }

        @Override public Quota quota() { return quota; }

        @Override
        public List<TenantVault> tenantVaults() {
            return tenantVaults;
        }

        @Override
        public List<TenantSecretStore> tenantSecretStores() {
            return tenantSecretStores;
        }

        @Override public String jvmGCOptions(Optional<ClusterSpec.Type> clusterType, Optional<ClusterSpec.Id> clusterId) {
            return flagValueForClusterTypeAndClusterId(jvmGCOptionsFlag, clusterType, clusterId);
        }

        @Override public String mallocImpl(Optional<ClusterSpec.Type> clusterType) {
            return flagValueForClusterTypeAndClusterId(mallocImplFlag, clusterType, Optional.empty());
        }

        @Override public int searchNodeInitializerThreads(String clusterId) {
            return intFlagValueForClusterId(searchNodeInitializerThreadsFlag, clusterId);
        }

        @Override public int heapSizePercentage(String clusterId) {
            return intFlagValueForClusterId(heapSizePercentageFlag, clusterId);
        }

        @Override
        public boolean allowDisableMtls() {
            return allowDisableMtls;
        }

        @Override
        public List<X509Certificate> operatorCertificates() {
            return operatorCertificates;
        }

        @Override public List<String> tlsCiphersOverride() { return tlsCiphersOverride; }

        public String flagValueForClusterTypeAndClusterId(
                StringFlag flag, Optional<ClusterSpec.Type> clusterType, Optional<ClusterSpec.Id> clusterId) {
            // Resolving the value here instead of the model context constructor is
            // suboptimal as the flag value may change during a single config generation,
            // which may trigger a warning from the jdisc container at best and potentially result in bad stuff.
            // Luckily this is feature flag that's rarely modified.
            var flagWithClusterType = clusterType.map(type -> flag.with(CLUSTER_TYPE, type.name()))
                    .orElse(flag);

            var flagWithContainerId = clusterId.map(id -> flagWithClusterType.with(CLUSTER_ID, id.value()))
                    .orElse(flagWithClusterType);

            return flagWithContainerId.value();
        }

        public int intFlagValueForClusterId(IntFlag flag, String clusterId) {
            return flag.with(CLUSTER_ID, clusterId).value();
        }

        @Override
        public List<String> environmentVariables() { return environmentVariables; }

        @Override
        public Optional<CloudAccount> cloudAccount() {
            return cloudAccount;
        }

        @Override
        public List<DataplaneToken> dataplaneTokens() {
            return dataplaneTokens;
        }

        @Override public boolean allowUserFilters() { return allowUserFilters; }

        @Override public Duration endpointConnectionTtl() { return endpointConnectionTtl; }

        @Override public List<String> requestPrefixForLoggingContent() { return requestPrefixForLoggingContent; }

        @Override public List<String> jdiscHttpComplianceViolations() { return jdiscHttpComplianceViolations; }
    }

}
