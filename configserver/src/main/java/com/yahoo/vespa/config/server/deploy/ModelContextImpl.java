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
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeResources.Architecture;
import com.yahoo.config.provision.SharedHosts;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.vespa.config.server.tenant.SecretStoreExternalIdRetriever;
import com.yahoo.vespa.flags.Dimension;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.flags.UnboundFlag;

import java.io.File;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

import static com.yahoo.vespa.config.server.ConfigServerSpec.fromConfig;
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

        private final String queryDispatchPolicy;
        private final double queryDispatchWarmup;
        private final double defaultTermwiseLimit;
        private final String feedSequencer;
        private final String responseSequencer;
        private final int numResponseThreads;
        private final boolean useAsyncMessageHandlingOnSchedule;
        private final double feedConcurrency;
        private final double feedNiceness;
        private final List<String> allowedAthenzProxyIdentities;
        private final int maxActivationInhibitedOutOfSyncGroups;
        private final Predicate<ClusterSpec.Type> jvmOmitStackTraceInFastThrow;
        private final double resourceLimitDisk;
        private final double resourceLimitMemory;
        private final double minNodeRatioPerGroup;
        private final boolean containerDumpHeapOnShutdownTimeout;
        private final boolean loadCodeAsHugePages;
        private final double containerShutdownTimeout;
        private final int maxUnCommittedMemory;
        private final boolean forwardIssuesAsErrors;
        private final boolean useV8GeoPositions;
        private final int maxCompactBuffers;
        private final List<String> ignoredHttpUserAgents;
        private final boolean enableProxyProtocolMixedMode;
        private final boolean sharedStringRepoNoReclaim;
        private final String logFileCompressionAlgorithm;
        private final int mbus_network_threads;
        private final int mbus_java_num_targets;
        private final int mbus_java_events_before_wakeup;
        private final int mbus_cpp_num_targets;
        private final int mbus_cpp_events_before_wakeup;
        private final int rpc_num_targets;
        private final int rpc_events_before_wakeup;
        private final int heapPercentage;
        private final String summaryDecodePolicy;
        private final boolean sortBlueprintsByCost;
        private final boolean alwaysMarkPhraseExpensive;
        private final int contentLayerMetadataFeatureLevel;
        private final String unknownConfigDefinition;
        private final int searchHandlerThreadpool;
        private final int persistenceThreadMaxFeedOpBatchSize;
        private final boolean logserverOtelCol;
        private final SharedHosts sharedHosts;
        private final Architecture adminClusterArchitecture;

        public FeatureFlags(FlagSource source, ApplicationId appId, Version version) {
            this.defaultTermwiseLimit = Flags.DEFAULT_TERM_WISE_LIMIT.bindTo(source).with(appId).with(version).value();
            this.feedSequencer = Flags.FEED_SEQUENCER_TYPE.bindTo(source).with(appId).with(version).value();
            this.responseSequencer = Flags.RESPONSE_SEQUENCER_TYPE.bindTo(source).with(appId).with(version).value();
            this.numResponseThreads = Flags.RESPONSE_NUM_THREADS.bindTo(source).with(appId).with(version).value();
            this.useAsyncMessageHandlingOnSchedule = Flags.USE_ASYNC_MESSAGE_HANDLING_ON_SCHEDULE.bindTo(source).with(appId).with(version).value();
            this.feedConcurrency = Flags.FEED_CONCURRENCY.bindTo(source).with(appId).with(version).value();
            this.feedNiceness = Flags.FEED_NICENESS.bindTo(source).with(appId).with(version).value();
            this.mbus_network_threads = Flags.MBUS_NUM_NETWORK_THREADS.bindTo(source).with(appId).with(version).value();
            this.allowedAthenzProxyIdentities = Flags.ALLOWED_ATHENZ_PROXY_IDENTITIES.bindTo(source).with(appId).with(version).value();
            this.maxActivationInhibitedOutOfSyncGroups = Flags.MAX_ACTIVATION_INHIBITED_OUT_OF_SYNC_GROUPS.bindTo(source).with(appId).with(version).value();
            this.jvmOmitStackTraceInFastThrow = type -> PermanentFlags.JVM_OMIT_STACK_TRACE_IN_FAST_THROW.bindTo(source).with(appId).with(version).with(type).value();
            this.resourceLimitDisk = PermanentFlags.RESOURCE_LIMIT_DISK.bindTo(source).with(appId).with(version).value();
            this.resourceLimitMemory = PermanentFlags.RESOURCE_LIMIT_MEMORY.bindTo(source).with(appId).with(version).value();
            this.minNodeRatioPerGroup = Flags.MIN_NODE_RATIO_PER_GROUP.bindTo(source).with(appId).with(version).value();
            this.containerDumpHeapOnShutdownTimeout = Flags.CONTAINER_DUMP_HEAP_ON_SHUTDOWN_TIMEOUT.bindTo(source).with(appId).with(version).value();
            this.loadCodeAsHugePages = Flags.LOAD_CODE_AS_HUGEPAGES.bindTo(source).with(appId).with(version).value();
            this.containerShutdownTimeout = Flags.CONTAINER_SHUTDOWN_TIMEOUT.bindTo(source).with(appId).with(version).value();
            this.maxUnCommittedMemory = Flags.MAX_UNCOMMITTED_MEMORY.bindTo(source).with(appId).with(version).value();
            this.forwardIssuesAsErrors = PermanentFlags.FORWARD_ISSUES_AS_ERRORS.bindTo(source).with(appId).with(version).value();
            this.useV8GeoPositions = Flags.USE_V8_GEO_POSITIONS.bindTo(source).with(appId).with(version).value();
            this.maxCompactBuffers = Flags.MAX_COMPACT_BUFFERS.bindTo(source).with(appId).with(version).value();
            this.ignoredHttpUserAgents = PermanentFlags.IGNORED_HTTP_USER_AGENTS.bindTo(source).with(appId).with(version).value();
            this.enableProxyProtocolMixedMode = Flags.ENABLE_PROXY_PROTOCOL_MIXED_MODE.bindTo(source).with(appId).with(version).value();
            this.sharedStringRepoNoReclaim = Flags.SHARED_STRING_REPO_NO_RECLAIM.bindTo(source).with(appId).with(version).value();
            this.logFileCompressionAlgorithm = Flags.LOG_FILE_COMPRESSION_ALGORITHM.bindTo(source).with(appId).with(version).value();
            this.mbus_java_num_targets = Flags.MBUS_JAVA_NUM_TARGETS.bindTo(source).with(appId).with(version).value();
            this.mbus_java_events_before_wakeup = Flags.MBUS_JAVA_EVENTS_BEFORE_WAKEUP.bindTo(source).with(appId).with(version).value();
            this.mbus_cpp_num_targets = Flags.MBUS_CPP_NUM_TARGETS.bindTo(source).with(appId).with(version).value();
            this.mbus_cpp_events_before_wakeup = Flags.MBUS_CPP_EVENTS_BEFORE_WAKEUP.bindTo(source).with(appId).with(version).value();
            this.rpc_num_targets = Flags.RPC_NUM_TARGETS.bindTo(source).with(appId).with(version).value();
            this.rpc_events_before_wakeup = Flags.RPC_EVENTS_BEFORE_WAKEUP.bindTo(source).with(appId).with(version).value();
            this.queryDispatchPolicy = Flags.QUERY_DISPATCH_POLICY.bindTo(source).with(appId).with(version).value();
            this.queryDispatchWarmup = PermanentFlags.QUERY_DISPATCH_WARMUP.bindTo(source).with(appId).with(version).value();
            this.heapPercentage = PermanentFlags.HEAP_SIZE_PERCENTAGE.bindTo(source).with(appId).with(version).value();
            this.summaryDecodePolicy = Flags.SUMMARY_DECODE_POLICY.bindTo(source).with(appId).with(version).value();
            this.contentLayerMetadataFeatureLevel = Flags.CONTENT_LAYER_METADATA_FEATURE_LEVEL.bindTo(source).with(appId).with(version).value();
            this.unknownConfigDefinition = Flags.UNKNOWN_CONFIG_DEFINITION.bindTo(source).with(appId).with(version).value();
            this.searchHandlerThreadpool = Flags.SEARCH_HANDLER_THREADPOOL.bindTo(source).with(appId).with(version).value();
            this.alwaysMarkPhraseExpensive = Flags.ALWAYS_MARK_PHRASE_EXPENSIVE.bindTo(source).with(appId).with(version).value();
            this.sortBlueprintsByCost = Flags.SORT_BLUEPRINTS_BY_COST.bindTo(source).with(appId).with(version).value();
            this.persistenceThreadMaxFeedOpBatchSize = Flags.PERSISTENCE_THREAD_MAX_FEED_OP_BATCH_SIZE.bindTo(source).with(appId).with(version).value();
            this.logserverOtelCol = Flags.LOGSERVER_OTELCOL_AGENT.bindTo(source).with(appId).with(version).value();
            this.sharedHosts = PermanentFlags.SHARED_HOST.bindTo(source).with( appId).with(version).value();
            this.adminClusterArchitecture = Architecture.valueOf(PermanentFlags.ADMIN_CLUSTER_NODE_ARCHITECTURE.bindTo(source).with(appId).with(version).value());
        }

        @Override public int heapSizePercentage() { return heapPercentage; }
        @Override public String queryDispatchPolicy() { return queryDispatchPolicy; }
        @Override public double queryDispatchWarmup() { return queryDispatchWarmup; }
        @Override public String summaryDecodePolicy() { return summaryDecodePolicy; }
        @Override public double defaultTermwiseLimit() { return defaultTermwiseLimit; }
        @Override public String feedSequencerType() { return feedSequencer; }
        @Override public String responseSequencerType() { return responseSequencer; }
        @Override public int defaultNumResponseThreads() { return numResponseThreads; }
        @Override public boolean useAsyncMessageHandlingOnSchedule() { return useAsyncMessageHandlingOnSchedule; }
        @Override public double feedConcurrency() { return feedConcurrency; }
        @Override public double feedNiceness() { return feedNiceness; }
        @Override public int mbusNetworkThreads() { return mbus_network_threads; }
        @Override public List<String> allowedAthenzProxyIdentities() { return allowedAthenzProxyIdentities; }
        @Override public int maxActivationInhibitedOutOfSyncGroups() { return maxActivationInhibitedOutOfSyncGroups; }
        @Override public String jvmOmitStackTraceInFastThrowOption(ClusterSpec.Type type) {
            return translateJvmOmitStackTraceInFastThrowToString(jvmOmitStackTraceInFastThrow, type);
        }
        @Override public double resourceLimitDisk() { return resourceLimitDisk; }
        @Override public double resourceLimitMemory() { return resourceLimitMemory; }
        @Override public double minNodeRatioPerGroup() { return minNodeRatioPerGroup; }
        @Override public double containerShutdownTimeout() { return containerShutdownTimeout; }
        @Override public boolean containerDumpHeapOnShutdownTimeout() { return containerDumpHeapOnShutdownTimeout; }
        @Override public boolean loadCodeAsHugePages() { return loadCodeAsHugePages; }
        @Override public int maxUnCommittedMemory() { return maxUnCommittedMemory; }
        @Override public boolean forwardIssuesAsErrors() { return forwardIssuesAsErrors; }
        @Override public boolean useV8GeoPositions() { return useV8GeoPositions; }
        @Override public int maxCompactBuffers() { return maxCompactBuffers; }
        @Override public List<String> ignoredHttpUserAgents() { return ignoredHttpUserAgents; }
        @Override public boolean enableProxyProtocolMixedMode() { return enableProxyProtocolMixedMode; }
        @Override public boolean sharedStringRepoNoReclaim() { return sharedStringRepoNoReclaim; }
        @Override public int mbusJavaRpcNumTargets() { return mbus_java_num_targets; }
        @Override public int mbusJavaEventsBeforeWakeup() { return mbus_java_events_before_wakeup; }
        @Override public int mbusCppRpcNumTargets() { return mbus_cpp_num_targets; }
        @Override public int mbusCppEventsBeforeWakeup() { return mbus_cpp_events_before_wakeup; }
        @Override public int rpcNumTargets() { return rpc_num_targets; }
        @Override public int rpcEventsBeforeWakeup() { return rpc_events_before_wakeup; }
        @Override public String logFileCompressionAlgorithm(String defVal) {
            var fflag = this.logFileCompressionAlgorithm;
            if (fflag != null && ! fflag.isEmpty()) {
                return fflag;
            }
            return defVal;
        }
        @Override public boolean alwaysMarkPhraseExpensive() { return alwaysMarkPhraseExpensive; }
        @Override public int contentLayerMetadataFeatureLevel() { return contentLayerMetadataFeatureLevel; }
        @Override public String unknownConfigDefinition() { return unknownConfigDefinition; }
        @Override public int searchHandlerThreadpool() { return searchHandlerThreadpool; }
        @Override public boolean sortBlueprintsByCost() { return sortBlueprintsByCost; }
        @Override public int persistenceThreadMaxFeedOpBatchSize() { return persistenceThreadMaxFeedOpBatchSize; }
        @Override public boolean logserverOtelCol() { return logserverOtelCol; }
        @Override public SharedHosts sharedHosts() { return sharedHosts; }
        @Override public Architecture adminClusterArchitecture() { return adminClusterArchitecture; }

        private String translateJvmOmitStackTraceInFastThrowToString(Predicate<ClusterSpec.Type> function,
                                                                     ClusterSpec.Type clusterType) {
            return function.test(clusterType) ? "" : "-XX:-OmitStackTraceInFastThrow";
        }

    }

    public static class Properties implements ModelContext.Properties {

        private final ModelContext.FeatureFlags featureFlags;
        private final ApplicationId applicationId;
        private final boolean multitenant;
        private final List<ConfigServerSpec> configServerSpecs;
        private final HostName loadBalancerName;
        private final URI ztsUrl;
        private final String athenzDnsSuffix;
        private final boolean hostedVespa;
        private final Zone zone;
        private final Set<ContainerEndpoint> endpoints;
        private final boolean isBootstrap;
        private final boolean isFirstTimeDeployment;
        private final Optional<EndpointCertificateSecrets> endpointCertificateSecrets;
        private final Optional<AthenzDomain> athenzDomain;
        private final Quota quota;
        private final List<TenantSecretStore> tenantSecretStores;
        private final SecretStore secretStore;
        private final StringFlag jvmGCOptionsFlag;
        private final boolean allowDisableMtls;
        private final List<X509Certificate> operatorCertificates;
        private final List<String> tlsCiphersOverride;
        private final List<String> zoneDnsSuffixes;
        private final List<String> environmentVariables;
        private final Optional<CloudAccount> cloudAccount;
        private final List<DataplaneToken> dataplaneTokens;
        private final boolean allowUserFilters;
        private final Duration endpointConnectionTtl;

        public Properties(ApplicationId applicationId,
                          Version modelVersion,
                          ConfigserverConfig configserverConfig,
                          Zone zone,
                          Set<ContainerEndpoint> endpoints,
                          boolean isBootstrap,
                          boolean isFirstTimeDeployment,
                          FlagSource flagSource,
                          Optional<EndpointCertificateSecrets> endpointCertificateSecrets,
                          Optional<AthenzDomain> athenzDomain,
                          Optional<Quota> maybeQuota,
                          List<TenantSecretStore> tenantSecretStores,
                          SecretStore secretStore,
                          List<X509Certificate> operatorCertificates,
                          Optional<CloudAccount> cloudAccount,
                          List<DataplaneToken> dataplaneTokens) {
            this.featureFlags = new FeatureFlags(flagSource, applicationId, modelVersion);
            this.applicationId = applicationId;
            this.multitenant = configserverConfig.multitenant() || configserverConfig.hostedVespa() || Boolean.getBoolean("multitenant");
            this.configServerSpecs = fromConfig(configserverConfig);
            this.loadBalancerName = configserverConfig.loadBalancerAddress().isEmpty() ? null : HostName.of(configserverConfig.loadBalancerAddress());
            this.ztsUrl = configserverConfig.ztsUrl() != null ? URI.create(configserverConfig.ztsUrl()) : null;
            this.athenzDnsSuffix = configserverConfig.athenzDnsSuffix();
            this.hostedVespa = configserverConfig.hostedVespa();
            this.zone = zone;
            this.endpoints = endpoints;
            this.isBootstrap = isBootstrap;
            this.isFirstTimeDeployment = isFirstTimeDeployment;
            this.endpointCertificateSecrets = endpointCertificateSecrets;
            this.athenzDomain = athenzDomain;
            this.quota = maybeQuota.orElseGet(Quota::unlimited);
            this.tenantSecretStores = tenantSecretStores;
            this.secretStore = secretStore;
            this.jvmGCOptionsFlag = PermanentFlags.JVM_GC_OPTIONS.bindTo(flagSource)
                    .with(Dimension.INSTANCE_ID, applicationId.serializedForm())
                    .with(Dimension.APPLICATION, applicationId.toSerializedFormWithoutInstance());
            this.allowDisableMtls = PermanentFlags.ALLOW_DISABLE_MTLS.bindTo(flagSource).with(applicationId).value();
            this.operatorCertificates = operatorCertificates;
            this.tlsCiphersOverride = PermanentFlags.TLS_CIPHERS_OVERRIDE.bindTo(flagSource).with(applicationId).value();
            this.zoneDnsSuffixes = configserverConfig.zoneDnsSuffixes();
            this.environmentVariables = PermanentFlags.ENVIRONMENT_VARIABLES.bindTo(flagSource).with(applicationId).value();
            this.cloudAccount = cloudAccount;
            this.allowUserFilters = PermanentFlags.ALLOW_USER_FILTERS.bindTo(flagSource).with(applicationId).value();
            this.endpointConnectionTtl = Duration.ofSeconds(PermanentFlags.ENDPOINT_CONNECTION_TTL.bindTo(flagSource).with(applicationId).value());
            this.dataplaneTokens = dataplaneTokens;
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
        public String athenzDnsSuffix() {
            return athenzDnsSuffix;
        }

        @Override
        public boolean hostedVespa() { return hostedVespa; }

        @Override
        public Zone zone() { return zone; }

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
        public List<TenantSecretStore> tenantSecretStores() {
            return SecretStoreExternalIdRetriever.populateExternalId(secretStore, applicationId.tenant(), zone.system(), tenantSecretStores);
        }

        @Override public String jvmGCOptions(Optional<ClusterSpec.Type> clusterType) {
            return flagValueForClusterType(jvmGCOptionsFlag, clusterType);
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

        @Override
        public List<String> zoneDnsSuffixes() {
            return zoneDnsSuffixes;
        }

        public String flagValueForClusterType(StringFlag flag, Optional<ClusterSpec.Type> clusterType) {
            return clusterType.map(type -> flag.with(CLUSTER_TYPE, type.name()))
                              .orElse(flag)
                              .value();
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
    }
}
