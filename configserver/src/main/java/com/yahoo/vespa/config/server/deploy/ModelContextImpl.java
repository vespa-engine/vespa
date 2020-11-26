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
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.UnboundFlag;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.config.server.ConfigServerSpec.fromConfig;

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
    private final Optional<HostProvisioner> hostProvisioner;
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
                            Optional<HostProvisioner> hostProvisioner,
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
    // TODO: Don't allow empty here but create the right provisioner when this is set up instead
    @Override
    public Optional<HostProvisioner> hostProvisioner() { return hostProvisioner; }

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

        private final boolean enableAutomaticReindexing;
        private final boolean dynamicReconfigurationOfZookeeperCluster;

        public FeatureFlags(FlagSource source, ApplicationId appId) {
            this.enableAutomaticReindexing = flagValue(source, appId, Flags.ENABLE_AUTOMATIC_REINDEXING);
            this.dynamicReconfigurationOfZookeeperCluster = flagValue(source, appId, Flags.DYNAMIC_RECONFIGURATION_OF_ZOOKEEPER_CLUSTER);
        }

        @Override public boolean enableAutomaticReindexing() { return enableAutomaticReindexing; }
        @Override public boolean dynamicReconfigurationOfZookeeperCluster() { return dynamicReconfigurationOfZookeeperCluster; }

        private static <V> V flagValue(FlagSource source, ApplicationId appId, UnboundFlag<? extends V, ?, ?> flag) {
            return flag.bindTo(source)
                    .with(FetchVector.Dimension.APPLICATION_ID, appId.serializedForm())
                    .boxedValue();
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
        private final boolean useThreePhaseUpdates;
        private final boolean useDirectStorageApiRpc;
        private final boolean useFastValueTensorImplementation;
        private final Optional<EndpointCertificateSecrets> endpointCertificateSecrets;
        private final double defaultTermwiseLimit;
        private final String jvmGCOPtions;
        private final String feedSequencer;
        private final String responseSequencer;
        private final int numResponseThreads;
        private final boolean skipCommunicationManagerThread;
        private final boolean skipMbusRequestThread;
        private final boolean skipMbusReplyThread;
        private final Optional<AthenzDomain> athenzDomain;
        private final Optional<ApplicationRoles> applicationRoles;
        private final Quota quota;
        private final boolean useAccessControlTlsHandshakeClientAuth;
        private final boolean useAsyncMessageHandlingOnSchedule;
        private final int contentNodeBucketDBStripeBits;
        private final int mergeChunkSize;
        private final double feedConcurrency;

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
                          Optional<Quota> maybeQuota) {
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
            defaultTermwiseLimit = Flags.DEFAULT_TERM_WISE_LIMIT.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            useThreePhaseUpdates = Flags.USE_THREE_PHASE_UPDATES.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            useDirectStorageApiRpc = Flags.USE_DIRECT_STORAGE_API_RPC.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            useFastValueTensorImplementation = Flags.USE_FAST_VALUE_TENSOR_IMPLEMENTATION.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            jvmGCOPtions = Flags.JVM_GC_OPTIONS.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            feedSequencer = Flags.FEED_SEQUENCER_TYPE.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            responseSequencer = Flags.RESPONSE_SEQUENCER_TYPE.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            numResponseThreads = Flags.RESPONSE_NUM_THREADS.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            skipCommunicationManagerThread = Flags.SKIP_COMMUNICATIONMANAGER_THREAD.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            skipMbusRequestThread = Flags.SKIP_MBUS_REQUEST_THREAD.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            skipMbusReplyThread = Flags.SKIP_MBUS_REPLY_THREAD.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            this.athenzDomain = athenzDomain;
            this.applicationRoles = applicationRoles;
            this.quota = maybeQuota.orElseGet(Quota::unlimited);
            this.useAccessControlTlsHandshakeClientAuth =
                    Flags.USE_ACCESS_CONTROL_CLIENT_AUTHENTICATION.bindTo(flagSource)
                            .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm())
                            .value();
            useAsyncMessageHandlingOnSchedule = Flags.USE_ASYNC_MESSAGE_HANDLING_ON_SCHEDULE.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            contentNodeBucketDBStripeBits = Flags.CONTENT_NODE_BUCKET_DB_STRIPE_BITS.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            mergeChunkSize = Flags.MERGE_CHUNK_SIZE.bindTo(flagSource)
                    .with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            feedConcurrency = Flags.FEED_CONCURRENCY.bindTo(flagSource)
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
        public double defaultTermwiseLimit() { return defaultTermwiseLimit; }

        @Override
        public boolean useThreePhaseUpdates() {
            return useThreePhaseUpdates;
        }

        @Override
        public boolean useDirectStorageApiRpc() {
            return useDirectStorageApiRpc;
        }

        @Override
        public boolean useFastValueTensorImplementation() {
            return useFastValueTensorImplementation;
        }

        @Override
        public Optional<AthenzDomain> athenzDomain() { return athenzDomain; }

        @Override
        public Optional<ApplicationRoles> applicationRoles() {
            return applicationRoles;
        }

        @Override public String jvmGCOptions() { return jvmGCOPtions; }
        @Override public String feedSequencerType() { return feedSequencer; }
        @Override public String responseSequencerType() { return responseSequencer; }
        @Override public int defaultNumResponseThreads() { return numResponseThreads; }
        @Override public boolean skipCommunicationManagerThread() { return skipCommunicationManagerThread; }
        @Override public boolean skipMbusRequestThread() { return skipMbusRequestThread; }
        @Override public boolean skipMbusReplyThread() { return skipMbusReplyThread; }
        @Override public Quota quota() { return quota; }
        @Override public boolean useAccessControlTlsHandshakeClientAuth() { return useAccessControlTlsHandshakeClientAuth; }
        @Override public boolean useAsyncMessageHandlingOnSchedule() { return useAsyncMessageHandlingOnSchedule; }
        @Override public int contentNodeBucketDBStripeBits() { return contentNodeBucketDBStripeBits; }
        @Override public int mergeChunkSize() { return mergeChunkSize; }
        @Override public double feedConcurrency() { return feedConcurrency; }
    }

}
