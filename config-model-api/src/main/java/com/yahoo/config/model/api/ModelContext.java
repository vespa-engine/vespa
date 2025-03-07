// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources.Architecture;
import com.yahoo.config.provision.SharedHosts;
import com.yahoo.config.provision.Zone;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Model context containing state provided to model factories.
 *
 * @author Ulf Lilleengen
 */
public interface ModelContext {

    ApplicationPackage applicationPackage();
    Optional<Model> previousModel();
    HostProvisioner getHostProvisioner();
    Provisioned provisioned();
    DeployLogger deployLogger();
    ConfigDefinitionRepo configDefinitionRepo();
    FileRegistry getFileRegistry();
    ExecutorService getExecutor();
    default Optional<? extends Reindexing> reindexing() { return Optional.empty(); }
    Properties properties();
    default Optional<File> appDir() { return Optional.empty(); }
    OnnxModelCost onnxModelCost();

    /** The Docker image repo we want to use for images for this deployment (optional, will use default if empty) */
    default Optional<DockerImage> wantedDockerImageRepo() { return Optional.empty(); }

    /** The Vespa version this model is built for */
    Version modelVespaVersion();

    /** The Vespa version we want nodes to become */
    Version wantedNodeVespaVersion();

    /**
     * How to remove a temporary feature flags:
     * 1)
     * - Remove flag definition from Flags
     * - Remove method implementation from ModelContextImpl.FeatureFlags
     * - Modify default implementation of below method to return the new default value
     * - Remove all usage of below method from config-model
     *
     * 2)
     * - (optional) Track Vespa version that introduced changes from 1) in annotation field 'removeAfter'
     *
     * 3)
     *  - Remove below method once all config-model versions in hosted production include changes from 1)
     *  - Remove all flag data files from hosted-feature-flag repository
     */
    interface FeatureFlags {
        @ModelFeatureFlag(owners = {"hmusum"}) default String responseSequencerType() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"hmusum"}, removeAfter = "8.483") default String queryDispatchPolicy() { return "adaptive"; }
        @ModelFeatureFlag(owners = {"hmusum"}) default double queryDispatchWarmup() { return 5.0; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int defaultNumResponseThreads() { return 2; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int mbusNetworkThreads() { return 1; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int mbusJavaRpcNumTargets() { return 2; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int mbusJavaEventsBeforeWakeup() { return 1; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int mbusCppRpcNumTargets() { return 2; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int mbusCppEventsBeforeWakeup() { return 1; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int rpcNumTargets() { return 2; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int rpcEventsBeforeWakeup() { return 1; }
        @ModelFeatureFlag(owners = {"hmusum"}) default boolean useAsyncMessageHandlingOnSchedule() { return true; }
        @ModelFeatureFlag(owners = {"hmusum"}) default double feedConcurrency() { return 0.5; }
        @ModelFeatureFlag(owners = {"hmusum"}) default double feedNiceness() { return 0.0; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int maxUnCommittedMemory() { return 130000; }
        @ModelFeatureFlag(owners = {"vekterli"}) default String searchMmapAdvise() { return "NORMAL"; }
        @ModelFeatureFlag(owners = {"hmusum"}, removeAfter = "8.479") default boolean loadCodeAsHugePages() { return false; }
        @ModelFeatureFlag(owners = {"bjorncs"}) default boolean containerDumpHeapOnShutdownTimeout() { return false; }
        @ModelFeatureFlag(owners = {"hmusum"}, removeAfter = "8.478") default double containerShutdownTimeout() { return 50.0; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int heapSizePercentage() { return 0; }
        @ModelFeatureFlag(owners = {"bjorncs", "tokle"}) default List<String> allowedAthenzProxyIdentities() { return List.of(); }
        @ModelFeatureFlag(owners = {"vekterli"}) default int maxActivationInhibitedOutOfSyncGroups() { return 0; }
        @ModelFeatureFlag(owners = {"hmusum"}) default double resourceLimitDisk() { return 0.75; }
        @ModelFeatureFlag(owners = {"hmusum"}) default double resourceLimitMemory() { return 0.8; }
        @ModelFeatureFlag(owners = {"hmusum"}) default double resourceLimitLowWatermarkDifference() { return 0.0; }
        @ModelFeatureFlag(owners = {"vekterli"}) default double minNodeRatioPerGroup() { return 0.0; }
        @ModelFeatureFlag(owners = {"arnej"}) default boolean forwardIssuesAsErrors() { return true; }
        @ModelFeatureFlag(owners = {"arnej"}) default boolean useV8GeoPositions() { return false; }
        @ModelFeatureFlag(owners = {"hmusum", "toregge"}, removeAfter = "8.480") default int maxCompactBuffers() { return 1; }
        @ModelFeatureFlag(owners = {"arnej", "andreer"}) default List<String> ignoredHttpUserAgents() { return List.of(); }
        @ModelFeatureFlag(owners = {"arnej"}) default String logFileCompressionAlgorithm(String defVal) { return defVal; }
        @ModelFeatureFlag(owners = {"hmusum"}, comment = "Select summary decode type") default String summaryDecodePolicy() { return "eager"; }
        @ModelFeatureFlag(owners = {"vekterli"}) default int contentLayerMetadataFeatureLevel() { return 0; }
        @ModelFeatureFlag(owners = {"hmusum"}) default String unknownConfigDefinition() { return "warn"; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int searchHandlerThreadpool() { return 2; }
        @ModelFeatureFlag(owners = {"hmusum"}, removeAfter = "8.485") default boolean alwaysMarkPhraseExpensive() { return false; }
        @ModelFeatureFlag(owners = {"havardpe"}) default boolean sortBlueprintsByCost() { return false; }
        @ModelFeatureFlag(owners = {"vekterli"}) default int persistenceThreadMaxFeedOpBatchSize() { return 1; }
        @ModelFeatureFlag(owners = {"olaa"}) default boolean logserverOtelCol() { return false; }
        @ModelFeatureFlag(owners = {"bratseth"}) default SharedHosts sharedHosts() { return SharedHosts.empty(); }
        @ModelFeatureFlag(owners = {"bratseth"}) default Architecture adminClusterArchitecture() { return Architecture.x86_64; }
        @ModelFeatureFlag(owners = {"arnej"}) default double logserverNodeMemory() { return 0.0; }
        @ModelFeatureFlag(owners = {"arnej"}) default double clusterControllerNodeMemory() { return 0.0; }
        @ModelFeatureFlag(owners = {"vekterli"}) default boolean symmetricPutAndActivateReplicaSelection() { return false; }
        @ModelFeatureFlag(owners = {"vekterli"}, removeAfter = "8.489") default boolean enforceStrictlyIncreasingClusterStateVersions() { return true; }
        @ModelFeatureFlag(owners = {"arnej"}) default boolean useLegacyWandQueryParsing() { return true; }
        @ModelFeatureFlag(owners = {"hmusum"}) default boolean forwardAllLogLevels() { return true; }
        @ModelFeatureFlag(owners = {"hmusum"}) default long zookeeperPreAllocSize() { return 65536L; }
        @ModelFeatureFlag(owners = {"bjorncs"}) default int documentV1QueueSize() { return -1; }
        @ModelFeatureFlag(owners = {"vekterli"}) default int maxContentNodeMaintenanceOpConcurrency() { return -1; }
    }

    /** Warning: As elsewhere in this package, do not make backwards incompatible changes that will break old config models! */
    interface Properties {

        FeatureFlags featureFlags();
        boolean multitenant();
        ApplicationId applicationId();
        List<ConfigServerSpec> configServerSpecs();
        HostName loadBalancerName();
        URI ztsUrl();
        AthenzDomain tenantSecretDomain();
        String athenzDnsSuffix();
        boolean hostedVespa();
        Zone zone();
        Set<ContainerEndpoint> endpoints();
        boolean isBootstrap();
        boolean isFirstTimeDeployment();

        default Optional<EndpointCertificateSecrets> endpointCertificateSecrets() { return Optional.empty(); }

        default Optional<AthenzDomain> athenzDomain() { return Optional.empty(); }

        default Quota quota() { return Quota.unlimited(); }

        default List<TenantVault> tenantVaults() { return List.of(); }

        default List<TenantSecretStore> tenantSecretStores() { return List.of(); }

        // Default setting for the gc-options attribute if not specified explicit by application
        default String jvmGCOptions() { return jvmGCOptions(Optional.empty()); }

        // Default setting for the gc-options attribute if not specified explicit by application
        String jvmGCOptions(Optional<ClusterSpec.Type> clusterType);

        // Note: Used in unit tests (set to false in TestProperties) to avoid needing to deal with implicitly created node for logserver
        default boolean useDedicatedNodeForLogserver() { return true; }

        // Allow disabling mTLS for now, harden later
        default boolean allowDisableMtls() { return true; }

        default List<X509Certificate> operatorCertificates() { return List.of(); }

        default List<String> tlsCiphersOverride() { return List.of(); }

        List<String> environmentVariables();

        default Optional<CloudAccount> cloudAccount() { return Optional.empty(); }

        default boolean allowUserFilters() { return true; }

        default Duration endpointConnectionTtl() { return Duration.ZERO; }

        default List<DataplaneToken> dataplaneTokens() { return List.of(); }

        default List<String> requestPrefixForLoggingContent() { return List.of(); }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ModelFeatureFlag {
        String[] owners();
        String removeAfter() default ""; // On the form "7.100.10"
        String comment() default "";
    }

}
