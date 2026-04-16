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
import com.yahoo.config.provision.CloudResourceTags;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources.Architecture;
import com.yahoo.config.provision.SharedHosts;
import com.yahoo.vespa.flags.Flag;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.UnboundFlag;

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
import static com.yahoo.vespa.flags.Dimension.HOSTNAME;

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

    /** A flag wrapper that can be safely exposed to the config model implementation.
     * Used to set fine-grained flag dimensions (e.g. hostname, cluster type, cluster id).
     * @see Properties#jvmGCOptionsFlag() for an example of this.
     */
    private record FeatureFlag<T, F extends Flag<T, F>, U extends UnboundFlag<T, F, U>>(F flag)
            implements ModelContext.FeatureFlag<T> {

        FeatureFlag(U unboundFlag, FlagSource source, ApplicationId appId, Version version) {
            this(unboundFlag.bindTo(source).with(appId).with(version).);
        }

        @Override
        public ModelContext.FeatureFlag<T> withClusterType(ClusterSpec.Type clusterType) {
            return new FeatureFlag<>(flag.with(CLUSTER_TYPE, clusterType.name()));
        }

        @Override
        public ModelContext.FeatureFlag<T> withClusterId(ClusterSpec.Id clusterId) {
            return new FeatureFlag<>(flag.with(CLUSTER_ID, clusterId.value()));
        }

        @Override
        public ModelContext.FeatureFlag<T> withHostname(String hostname) {
            return new FeatureFlag<>(flag.with(HOSTNAME, hostname));
        }

        @Override public T value() { return flag.boxedValue(); }
    }

    public static class FeatureFlags implements ModelContext.FeatureFlags {

        private final FlagSource source;
        private final ApplicationId appId;
        private final Version version;

        public FeatureFlags(FlagSource source, ApplicationId appId, Version version) {
            this.source = source;
            this.appId = appId;
            this.version = version;
        }

        private <T, F extends Flag<T, F>, U extends UnboundFlag<T, F, U>> ModelContext.FeatureFlag<T> flag(U unboundFlag) {
            return new FeatureFlag<>(unboundFlag, source, appId, version);
        }

        @Override public boolean useNonPublicEndpointForTest() { return flag(Flags.USE_NON_PUBLIC_ENDPOINT_FOR_TEST).value(); }
        @Override public int heapSizePercentage(Optional<String> clusterId) {
            var flag = flag(PermanentFlags.HEAP_SIZE_PERCENTAGE);
            return clusterId.map(id -> flag.withClusterId(ClusterSpec.Id.from(id)).value()).orElseGet(flag::value);
        }
        @Override public double queryDispatchWarmup() { return flag(PermanentFlags.QUERY_DISPATCH_WARMUP).value(); }
        @Override public String responseSequencerType() { return flag(Flags.RESPONSE_SEQUENCER_TYPE).value(); }
        @Override public int defaultNumResponseThreads() { return flag(Flags.RESPONSE_NUM_THREADS).value(); }
        @Override public boolean useAsyncMessageHandlingOnSchedule() { return flag(Flags.USE_ASYNC_MESSAGE_HANDLING_ON_SCHEDULE).value(); }
        @Override public double feedConcurrency() { return flag(PermanentFlags.FEED_CONCURRENCY).value(); }
        @Override public double feedNiceness() { return flag(PermanentFlags.FEED_NICENESS).value(); }
        @Override public int mbusNetworkThreads() { return flag(Flags.MBUS_NUM_NETWORK_THREADS).value(); }
        @Override public List<String> allowedAthenzProxyIdentities() { return flag(PermanentFlags.ALLOWED_ATHENZ_PROXY_IDENTITIES).value(); }
        @Override public int maxActivationInhibitedOutOfSyncGroups() { return flag(Flags.MAX_ACTIVATION_INHIBITED_OUT_OF_SYNC_GROUPS).value(); }
        @Override public double resourceLimitDisk() { return flag(PermanentFlags.RESOURCE_LIMIT_DISK).value(); }
        @Override public double resourceLimitMemory() { return flag(PermanentFlags.RESOURCE_LIMIT_MEMORY).value(); }
        @Override public double resourceLimitAddressSpace() { return flag(PermanentFlags.RESOURCE_LIMIT_ADDRESS_SPACE).value(); }
        @Override public boolean containerDumpHeapOnShutdownTimeout() { return flag(PermanentFlags.CONTAINER_DUMP_HEAP_ON_SHUTDOWN_TIMEOUT).value(); }
        @Override public int maxUnCommittedMemory() { return flag(PermanentFlags.MAX_UNCOMMITTED_MEMORY).value(); }
        @Override public boolean forwardIssuesAsErrors() { return flag(PermanentFlags.FORWARD_ISSUES_AS_ERRORS).value(); }
        @Override public List<String> ignoredHttpUserAgents() { return flag(PermanentFlags.IGNORED_HTTP_USER_AGENTS).value(); }
        @Override public int mbusJavaRpcNumTargets() { return flag(Flags.MBUS_JAVA_NUM_TARGETS).value(); }
        @Override public int mbusJavaEventsBeforeWakeup() { return flag(Flags.MBUS_JAVA_EVENTS_BEFORE_WAKEUP).value(); }
        @Override public int mbusCppRpcNumTargets() { return flag(Flags.MBUS_CPP_NUM_TARGETS).value(); }
        @Override public int mbusCppEventsBeforeWakeup() { return flag(Flags.MBUS_CPP_EVENTS_BEFORE_WAKEUP).value(); }
        @Override public int rpcNumTargets() { return flag(Flags.RPC_NUM_TARGETS).value(); }
        @Override public int rpcEventsBeforeWakeup() { return flag(Flags.RPC_EVENTS_BEFORE_WAKEUP).value(); }
        @Override public String unknownConfigDefinition() { return flag(PermanentFlags.UNKNOWN_CONFIG_DEFINITION).value(); }
        @Override public boolean sortBlueprintsByCost() { return flag(PermanentFlags.SORT_BLUEPRINTS_BY_COST).value(); }
        @Override public boolean logserverOtelCol() { return flag(Flags.LOGSERVER_OTELCOL_AGENT).value(); }
        @Override public SharedHosts sharedHosts() { return flag(PermanentFlags.SHARED_HOST).value(); }
        @Override public Architecture adminClusterArchitecture() { return Architecture.valueOf(flag(PermanentFlags.ADMIN_CLUSTER_NODE_ARCHITECTURE).value()); }
        @Override public double logserverNodeMemory() { return flag(PermanentFlags.LOGSERVER_NODE_MEMORY).value(); }
        @Override public double clusterControllerNodeMemory() { return flag(PermanentFlags.CLUSTER_CONTROLLER_NODE_MEMORY).value(); }
        @Override public boolean useLegacyWandQueryParsing() { return flag(Flags.USE_LEGACY_WAND_QUERY_PARSING).value(); }
        @Override public boolean useSimpleAnnotations() { return flag(Flags.USE_SIMPLE_ANNOTATIONS).value(); }
        @Override public boolean sendProtobufQuerytree() { return flag(Flags.SEND_PROTOBUF_QUERYTREE).value(); }
        @Override public boolean forwardAllLogLevels() { return flag(PermanentFlags.FORWARD_ALL_LOG_LEVELS).value(); }
        @Override public long zookeeperPreAllocSize() { return flag(Flags.ZOOKEEPER_PRE_ALLOC_SIZE_KIB).value(); }
        @Override public int maxContentNodeMaintenanceOpConcurrency() { return flag(Flags.MAX_CONTENT_NODE_MAINTENANCE_OP_CONCURRENCY).value(); }
        @Override public int maxDocumentOperationRequestSizeMib() { return flag(Flags.MAX_DOCUMENT_OPERATION_REQUEST_SIZE_MIB).value(); }
        @Override public Object sidecarsForTest() { return flag(Flags.SIDECARS_FOR_TEST).value(); }
        @Override public boolean useTriton() { return flag(Flags.USE_TRITON).value(); }
        @Override public ModelContext.FeatureFlag<Boolean> useTritonFlag() { return flag(Flags.USE_TRITON); }
        @Override public boolean scaleMetricsproxyHeapByNodeCount() { return flag(Flags.SCALE_METRICSPROXY_HEAP_BY_NODE_COUNT).value(); }
        @Override public boolean ignoreConnectivityChecksAtStartup() { return flag(PermanentFlags.IGNORE_CONNECTIVITY_CHECKS_AT_STARTUP).value(); }
        @Override public int searchCoreMaxOutstandingMoveOps() { return flag(Flags.SEARCH_CORE_MAX_OUTSTANDING_MOVE_OPS).value(); }
        @Override public double docprocHandlerThreadpool() { return flag(Flags.DOCPROC_HANDLER_THREADPOOL).value(); }
        @Override public boolean applyOnRestartForApplicationMetadataConfig() { return flag(Flags.APPLY_ON_RESTART_FOR_APPLICATION_METADATA_CONFIG).value(); }
        @Override public double autoscalerTargetWriteCpuPercentage(Optional<String> clusterId) {
            var flag = flag(Flags.AUTOSCALER_TARGET_WRITE_CPU_PERCENTAGE);
            return clusterId.map(id -> flag.withClusterId(ClusterSpec.Id.from(id)).value()).orElseGet(flag::value);
        }
    }

    public static class Properties implements ModelContext.Properties {

        private final ModelContext.FeatureFlags featureFlags;
        private final ApplicationId applicationId;
        private final FlagSource flagSource;
        private final Version modelVersion;
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
        private final List<X509Certificate> operatorCertificates;
        private final Optional<CloudAccount> cloudAccount;
        private final CloudResourceTags cloudResourceTags;
        private final List<DataplaneToken> dataplaneTokens;

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
                          CloudResourceTags cloudResourceTags,
                          List<DataplaneToken> dataplaneTokens) {
            this.featureFlags = new FeatureFlags(flagSource, applicationId, modelVersion);
            this.applicationId = applicationId;
            this.flagSource = flagSource;
            this.modelVersion = modelVersion;
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
            this.operatorCertificates = operatorCertificates;
            this.cloudAccount = cloudAccount;
            this.cloudResourceTags = cloudResourceTags;
            this.dataplaneTokens = dataplaneTokens;
        }

        @Override public ModelContext.FeatureFlags featureFlags() { return featureFlags; }
        @Override public boolean multitenant() { return multitenant; }
        @Override public ApplicationId applicationId() { return applicationId; }
        @Override public List<ConfigServerSpec> configServerSpecs() { return configServerSpecs; }
        @Override public HostName loadBalancerName() { return loadBalancerName; }
        @Override public URI ztsUrl() { return ztsUrl; }
        @Override public String athenzDnsSuffix() { return athenzDnsSuffix; }
        @Override public boolean hostedVespa() { return hostedVespa; }
        @Override public Set<ContainerEndpoint> endpoints() { return endpoints; }
        @Override public boolean isBootstrap() { return isBootstrap; }
        @Override public boolean isFirstTimeDeployment() { return isFirstTimeDeployment; }
        @Override public Optional<EndpointCertificateSecrets> endpointCertificateSecrets() { return endpointCertificateSecrets; }
        @Override public Optional<AthenzDomain> athenzDomain() { return athenzDomain; }
        @Override public Quota quota() { return quota; }
        @Override public List<TenantVault> tenantVaults() { return tenantVaults; }
        @Override public List<TenantSecretStore> tenantSecretStores() { return tenantSecretStores; }
        @Override public List<X509Certificate> operatorCertificates() { return operatorCertificates; }
        @Override public Optional<CloudAccount> cloudAccount() { return cloudAccount; }
        @Override public CloudResourceTags cloudResourceTags() { return cloudResourceTags; }
        @Override public List<DataplaneToken> dataplaneTokens() { return dataplaneTokens; }
        @Override public boolean allowDisableMtls() { return flag(PermanentFlags.ALLOW_DISABLE_MTLS).value(); }
        @Override public List<String> tlsCiphersOverride() { return flag(PermanentFlags.TLS_CIPHERS_OVERRIDE).value(); }
        @Override public List<String> environmentVariables() { return flag(PermanentFlags.ENVIRONMENT_VARIABLES).value(); }
        @Override public boolean allowUserFilters() { return flag(PermanentFlags.ALLOW_USER_FILTERS).value(); }
        @Override public Duration endpointConnectionTtl() { return Duration.ofSeconds(flag(PermanentFlags.ENDPOINT_CONNECTION_TTL).value()); }
        @Override public List<String> requestPrefixForLoggingContent() { return flag(PermanentFlags.LOG_REQUEST_CONTENT).value(); }
        @Override public List<String> jdiscHttpComplianceViolations() { return flag(PermanentFlags.JDISC_HTTP_COMPLIANCE_VIOLATIONS).value(); }
        @Override public ModelContext.FeatureFlag<String> jvmGCOptionsFlag() { return flag(PermanentFlags.JVM_GC_OPTIONS); }

        private <T, F extends Flag<T, F>, U extends UnboundFlag<T, F, U>> ModelContext.FeatureFlag<T> flag(U unboundFlag) {
            return new FeatureFlag<>(unboundFlag, flagSource, applicationId, modelVersion);
        }

        @Override public AthenzDomain tenantSecretDomain() {
            if (tenantSecretDomain.isEmpty())
                throw new IllegalArgumentException("Tenant secret domain is not set");
            return AthenzDomain.from(tenantSecretDomain);
        }

        @SuppressWarnings("removal")
        @Override public String jvmGCOptions(Optional<ClusterSpec.Type> clusterType, Optional<ClusterSpec.Id> clusterId) {
            var flag = flag(PermanentFlags.JVM_GC_OPTIONS);
            var withType = clusterType.map(type -> flag.withClusterType(type)).orElse(flag);
            return clusterId.map(id -> withType.withClusterId(id)).orElse(withType).value();
        }

        @Override public String mallocImpl(Optional<ClusterSpec.Type> clusterType) {
            var flag = flag(Flags.VESPA_USE_MALLOC_IMPL);
            return clusterType.map(type -> flag.withClusterType(type)).orElse(flag).value();
        }

        @Override public int searchNodeInitializerThreads(String clusterId) {
            return flag(PermanentFlags.SEARCHNODE_INITIALIZER_THREADS)
                    .withClusterId(ClusterSpec.Id.from(clusterId)).value();
        }

        @Override public int heapSizePercentage(String clusterId) {
            return flag(PermanentFlags.HEAP_SIZE_PERCENTAGE)
                    .withClusterId(ClusterSpec.Id.from(clusterId)).value();
        }
    }

}
