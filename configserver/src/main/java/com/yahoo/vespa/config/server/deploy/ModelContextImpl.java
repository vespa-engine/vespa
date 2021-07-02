// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ApplicationRoles;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.model.api.Reindexing;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.vespa.config.server.tenant.SecretStoreExternalIdRetriever;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.flags.UnboundFlag;

import java.io.File;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

import static com.yahoo.vespa.config.server.ConfigServerSpec.fromConfig;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CLUSTER_TYPE;

/**
 * Implementation of {@link ModelContext} for configserver.
 *
 * @author Ulf Lilleengen
 */
public class ModelContextImpl implements ModelContext {

    private final ApplicationPackage applicationPackage;
    private final Optional<Model> previousModel;
    private final Optional<ApplicationPackage> permanentApplicationPackage;
    private final DeployLogger deployLogger;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final FileRegistry fileRegistry;
    private final HostProvisioner hostProvisioner;
    private final Provisioned provisioned;
    private final Optional<? extends Reindexing> reindexing;
    private final ModelContext.Properties properties;
    private final Optional<File> appDir;

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
                            Optional<ApplicationPackage> permanentApplicationPackage,
                            DeployLogger deployLogger,
                            ConfigDefinitionRepo configDefinitionRepo,
                            FileRegistry fileRegistry,
                            Optional<? extends Reindexing> reindexing,
                            HostProvisioner hostProvisioner,
                            Provisioned provisioned,
                            ModelContext.Properties properties,
                            Optional<File> appDir,
                            Optional<DockerImage> wantedDockerImageRepository,
                            Version modelVespaVersion,
                            Version wantedNodeVespaVersion) {
        this.applicationPackage = applicationPackage;
        this.previousModel = previousModel;
        this.permanentApplicationPackage = permanentApplicationPackage;
        this.deployLogger = deployLogger;
        this.configDefinitionRepo = configDefinitionRepo;
        this.fileRegistry = fileRegistry;
        this.reindexing = reindexing;
        this.hostProvisioner = hostProvisioner;
        this.provisioned = provisioned;
        this.properties = properties;
        this.appDir = appDir;
        this.wantedDockerImageRepository = wantedDockerImageRepository;
        this.modelVespaVersion = modelVespaVersion;
        this.wantedNodeVespaVersion = wantedNodeVespaVersion;
    }

    @Override
    public ApplicationPackage applicationPackage() { return applicationPackage; }

    @Override
    public Optional<Model> previousModel() { return previousModel; }

    @Override
    public Optional<ApplicationPackage> permanentApplicationPackage() { return permanentApplicationPackage; }

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
    public Optional<? extends Reindexing> reindexing() { return  reindexing; }

    @Override
    public ModelContext.Properties properties() { return properties; }

    @Override
    public Optional<File> appDir() { return appDir; }

    @Override
    public Optional<DockerImage> wantedDockerImageRepo() { return wantedDockerImageRepository; }

    @Override
    public Version modelVespaVersion() { return modelVespaVersion; }

    @Override
    public Version wantedNodeVespaVersion() { return wantedNodeVespaVersion; }

    public static class FeatureFlags implements ModelContext.FeatureFlags {

        private final NodeResources dedicatedClusterControllerFlavor;
        private final double defaultTermwiseLimit;
        private final boolean useThreePhaseUpdates;
        private final String feedSequencer;
        private final String responseSequencer;
        private final int numResponseThreads;
        private final boolean skipCommunicationManagerThread;
        private final boolean skipMbusRequestThread;
        private final boolean skipMbusReplyThread;
        private final boolean useAsyncMessageHandlingOnSchedule;
        private final double feedConcurrency;
        private final boolean enableFeedBlockInDistributor;
        private final List<String> allowedAthenzProxyIdentities;
        private final int maxActivationInhibitedOutOfSyncGroups;
        private final ToIntFunction<ClusterSpec.Type> jvmOmitStackTraceInFastThrow;
        private final boolean enableCustomAclMapping;
        private final boolean useExternalRankExpression;
        private final boolean distributeExternalRankExpressions;
        private final int numDistributorStripes;
        private final boolean requireConnectivityCheck;
        private final int maxConcurrentMergesPerContentNode;
        private final int maxMergeQueueSize;
        private final int largeRankExpressionLimit;
        private final boolean throwIfResourceLimitsSpecified;
        private final boolean dryRunOnnxOnSetup;
        private final double resourceLimitDisk;
        private final double resourceLimitMemory;

        public FeatureFlags(FlagSource source, ApplicationId appId) {
            this.dedicatedClusterControllerFlavor = parseDedicatedClusterControllerFlavor(flagValue(source, appId, Flags.DEDICATED_CLUSTER_CONTROLLER_FLAVOR));
            this.defaultTermwiseLimit = flagValue(source, appId, Flags.DEFAULT_TERM_WISE_LIMIT);
            this.useThreePhaseUpdates = flagValue(source, appId, Flags.USE_THREE_PHASE_UPDATES);
            this.feedSequencer = flagValue(source, appId, Flags.FEED_SEQUENCER_TYPE);
            this.responseSequencer = flagValue(source, appId, Flags.RESPONSE_SEQUENCER_TYPE);
            this.numResponseThreads = flagValue(source, appId, Flags.RESPONSE_NUM_THREADS);
            this.skipCommunicationManagerThread = flagValue(source, appId, Flags.SKIP_COMMUNICATIONMANAGER_THREAD);
            this.skipMbusRequestThread = flagValue(source, appId, Flags.SKIP_MBUS_REQUEST_THREAD);
            this.skipMbusReplyThread = flagValue(source, appId, Flags.SKIP_MBUS_REPLY_THREAD);
            this.useAsyncMessageHandlingOnSchedule = flagValue(source, appId, Flags.USE_ASYNC_MESSAGE_HANDLING_ON_SCHEDULE);
            this.feedConcurrency = flagValue(source, appId, Flags.FEED_CONCURRENCY);
            this.enableFeedBlockInDistributor = flagValue(source, appId, Flags.ENABLE_FEED_BLOCK_IN_DISTRIBUTOR);
            this.allowedAthenzProxyIdentities = flagValue(source, appId, Flags.ALLOWED_ATHENZ_PROXY_IDENTITIES);
            this.maxActivationInhibitedOutOfSyncGroups = flagValue(source, appId, Flags.MAX_ACTIVATION_INHIBITED_OUT_OF_SYNC_GROUPS);
            this.jvmOmitStackTraceInFastThrow = type -> flagValueAsInt(source, appId, type, PermanentFlags.JVM_OMIT_STACK_TRACE_IN_FAST_THROW);
            this.enableCustomAclMapping = flagValue(source, appId, Flags.ENABLE_CUSTOM_ACL_MAPPING);
            this.numDistributorStripes = flagValue(source, appId, Flags.NUM_DISTRIBUTOR_STRIPES);
            this.useExternalRankExpression = flagValue(source, appId, Flags.USE_EXTERNAL_RANK_EXPRESSION);
            this.distributeExternalRankExpressions = flagValue(source, appId, Flags.DISTRIBUTE_EXTERNAL_RANK_EXPRESSION);
            this.largeRankExpressionLimit = flagValue(source, appId, Flags.LARGE_RANK_EXPRESSION_LIMIT);
            this.requireConnectivityCheck = flagValue(source, appId, Flags.REQUIRE_CONNECTIVITY_CHECK);
            this.maxConcurrentMergesPerContentNode = flagValue(source, appId, Flags.MAX_CONCURRENT_MERGES_PER_NODE);
            this.maxMergeQueueSize = flagValue(source, appId, Flags.MAX_MERGE_QUEUE_SIZE);
            this.throwIfResourceLimitsSpecified = flagValue(source, appId, Flags.THROW_EXCEPTION_IF_RESOURCE_LIMITS_SPECIFIED);
            this.dryRunOnnxOnSetup = flagValue(source, appId, Flags.DRY_RUN_ONNX_ON_SETUP);
            this.resourceLimitDisk = flagValue(source, appId, PermanentFlags.RESOURCE_LIMIT_DISK);
            this.resourceLimitMemory = flagValue(source, appId, PermanentFlags.RESOURCE_LIMIT_MEMORY);
        }

        @Override public Optional<NodeResources> dedicatedClusterControllerFlavor() { return Optional.ofNullable(dedicatedClusterControllerFlavor); }
        @Override public double defaultTermwiseLimit() { return defaultTermwiseLimit; }
        @Override public boolean useThreePhaseUpdates() { return useThreePhaseUpdates; }
        @Override public String feedSequencerType() { return feedSequencer; }
        @Override public String responseSequencerType() { return responseSequencer; }
        @Override public int defaultNumResponseThreads() { return numResponseThreads; }
        @Override public boolean skipCommunicationManagerThread() { return skipCommunicationManagerThread; }
        @Override public boolean skipMbusRequestThread() { return skipMbusRequestThread; }
        @Override public boolean skipMbusReplyThread() { return skipMbusReplyThread; }
        @Override public boolean useAsyncMessageHandlingOnSchedule() { return useAsyncMessageHandlingOnSchedule; }
        @Override public double feedConcurrency() { return feedConcurrency; }
        @Override public boolean enableFeedBlockInDistributor() { return enableFeedBlockInDistributor; }
        @Override public List<String> allowedAthenzProxyIdentities() { return allowedAthenzProxyIdentities; }
        @Override public int maxActivationInhibitedOutOfSyncGroups() { return maxActivationInhibitedOutOfSyncGroups; }
        @Override public String jvmOmitStackTraceInFastThrowOption(ClusterSpec.Type type) {
            return translateJvmOmitStackTraceInFastThrowIntToString(jvmOmitStackTraceInFastThrow, type);
        }
        @Override public boolean enableCustomAclMapping() { return enableCustomAclMapping; }
        @Override public int numDistributorStripes() { return numDistributorStripes; }
        @Override public boolean useExternalRankExpressions() { return useExternalRankExpression; }
        @Override public boolean distributeExternalRankExpressions() { return distributeExternalRankExpressions; }
        @Override public int largeRankExpressionLimit() { return largeRankExpressionLimit; }
        @Override public boolean requireConnectivityCheck() { return requireConnectivityCheck; }
        @Override public int maxConcurrentMergesPerNode() { return maxConcurrentMergesPerContentNode; }
        @Override public int maxMergeQueueSize() { return maxMergeQueueSize; }
        @Override public boolean throwIfResourceLimitsSpecified() { return throwIfResourceLimitsSpecified; }
        @Override public boolean dryRunOnnxOnSetup() { return dryRunOnnxOnSetup; }
        @Override public double resourceLimitDisk() { return resourceLimitDisk; }
        @Override public double resourceLimitMemory() { return resourceLimitMemory; }

        private static <V> V flagValue(FlagSource source, ApplicationId appId, UnboundFlag<? extends V, ?, ?> flag) {
            return flag.bindTo(source)
                    .with(FetchVector.Dimension.APPLICATION_ID, appId.serializedForm())
                    .boxedValue();
        }

        private static <V> V flagValue(FlagSource source, TenantName tenant, UnboundFlag<? extends V, ?, ?> flag) {
            return flag.bindTo(source)
                    .with(FetchVector.Dimension.TENANT_ID, tenant.value())
                    .boxedValue();
        }

        private static <V> V flagValue(FlagSource source,
                                       ApplicationId appId,
                                       ClusterSpec.Type clusterType,
                                       UnboundFlag<? extends V, ?, ?> flag) {
            return flag.bindTo(source)
                       .with(FetchVector.Dimension.APPLICATION_ID, appId.serializedForm())
                       .with(FetchVector.Dimension.CLUSTER_TYPE, clusterType.name())
                       .boxedValue();
        }

        static int flagValueAsInt(FlagSource source,
                                  ApplicationId appId,
                                  ClusterSpec.Type clusterType,
                                  UnboundFlag<? extends Boolean, ?, ?> flag) {
            return flagValue(source, appId, clusterType, flag) ? 1 : 0;
        }

        private String translateJvmOmitStackTraceInFastThrowIntToString(ToIntFunction<ClusterSpec.Type> function,
                                                                        ClusterSpec.Type clusterType) {
            return function.applyAsInt(clusterType) == 1 ? "" : "-XX:-OmitStackTraceInFastThrow";
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
        private final Optional<ApplicationRoles> applicationRoles;
        private final Quota quota;
        private final List<TenantSecretStore> tenantSecretStores;
        private final SecretStore secretStore;
        private final StringFlag jvmGCOptionsFlag;
        private final boolean allowDisableMtls;
        private final List<X509Certificate> operatorCertificates;
        private final List<String> tlsCiphersOverride;

        public Properties(ApplicationId applicationId,
                          ConfigserverConfig configserverConfig,
                          Zone zone,
                          Set<ContainerEndpoint> endpoints,
                          boolean isBootstrap,
                          boolean isFirstTimeDeployment,
                          FlagSource flagSource,
                          Optional<EndpointCertificateSecrets> endpointCertificateSecrets,
                          Optional<AthenzDomain> athenzDomain,
                          Optional<ApplicationRoles> applicationRoles,
                          Optional<Quota> maybeQuota,
                          List<TenantSecretStore> tenantSecretStores,
                          SecretStore secretStore,
                          List<X509Certificate> operatorCertificates) {
            this.featureFlags = new FeatureFlags(flagSource, applicationId);
            this.applicationId = applicationId;
            this.multitenant = configserverConfig.multitenant() || configserverConfig.hostedVespa() || Boolean.getBoolean("multitenant");
            this.configServerSpecs = fromConfig(configserverConfig);
            this.loadBalancerName = HostName.from(configserverConfig.loadBalancerAddress());
            this.ztsUrl = configserverConfig.ztsUrl() != null ? URI.create(configserverConfig.ztsUrl()) : null;
            this.athenzDnsSuffix = configserverConfig.athenzDnsSuffix();
            this.hostedVespa = configserverConfig.hostedVespa();
            this.zone = zone;
            this.endpoints = endpoints;
            this.isBootstrap = isBootstrap;
            this.isFirstTimeDeployment = isFirstTimeDeployment;
            this.endpointCertificateSecrets = endpointCertificateSecrets;
            this.athenzDomain = athenzDomain;
            this.applicationRoles = applicationRoles;
            this.quota = maybeQuota.orElseGet(Quota::unlimited);
            this.tenantSecretStores = tenantSecretStores;
            this.secretStore = secretStore;
            this.jvmGCOptionsFlag = PermanentFlags.JVM_GC_OPTIONS.bindTo(flagSource)
                                                                 .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm());
            this.allowDisableMtls = PermanentFlags.ALLOW_DISABLE_MTLS.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            this.operatorCertificates = operatorCertificates;
            this.tlsCiphersOverride = PermanentFlags.TLS_CIPHERS_OVERRIDE.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
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

        @Override
        public Optional<ApplicationRoles> applicationRoles() {
            return applicationRoles;
        }

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

        public String flagValueForClusterType(StringFlag flag, Optional<ClusterSpec.Type> clusterType) {
            return clusterType.map(type -> flag.with(CLUSTER_TYPE, type.name()))
                              .orElse(flag)
                              .value();
        }

    }

    private static NodeResources parseDedicatedClusterControllerFlavor(String flagValue) {
        String[] parts = flagValue.split("-");
        if (parts.length != 3)
            return null;

        return new NodeResources(Double.parseDouble(parts[0]),
                                 Double.parseDouble(parts[1]),
                                 Double.parseDouble(parts[2]),
                                 0.1,
                                 NodeResources.DiskSpeed.any,
                                 NodeResources.StorageType.any);
    }

}
