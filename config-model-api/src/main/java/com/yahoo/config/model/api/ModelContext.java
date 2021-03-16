// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.Zone;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Model context containing state provided to model factories.
 *
 * @author Ulf Lilleengen
 */
public interface ModelContext {

    ApplicationPackage applicationPackage();
    Optional<Model> previousModel();
    Optional<ApplicationPackage> permanentApplicationPackage();
    HostProvisioner getHostProvisioner();
    Provisioned provisioned();
    DeployLogger deployLogger();
    ConfigDefinitionRepo configDefinitionRepo();
    FileRegistry getFileRegistry();
    default Optional<? extends Reindexing> reindexing() { return Optional.empty(); }
    Properties properties();
    default Optional<File> appDir() { return Optional.empty();}

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
        @ModelFeatureFlag(owners = {"jonmv"}) default Optional<NodeResources> dedicatedClusterControllerFlavor() { return Optional.empty(); }
        @ModelFeatureFlag(owners = {"baldersheim"}, comment = "Revisit in May or June 2021") default double defaultTermwiseLimit() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"vekterli"}) default boolean useThreePhaseUpdates() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"baldersheim"}, comment = "Select sequencer type use while feeding") default String feedSequencerType() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"baldersheim"}) default String responseSequencerType() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"baldersheim"}) default int defaultNumResponseThreads() { return 2; }
        @ModelFeatureFlag(owners = {"baldersheim"}) default int maxPendingMoveOps() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"baldersheim"}) default boolean skipCommunicationManagerThread() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"baldersheim"}) default boolean skipMbusRequestThread() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"baldersheim"}) default boolean skipMbusReplyThread() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"tokle"}) default boolean useAccessControlTlsHandshakeClientAuth() { return false; }
        @ModelFeatureFlag(owners = {"baldersheim"}) default boolean useAsyncMessageHandlingOnSchedule() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"baldersheim"}) default double feedConcurrency() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"baldersheim"}) default boolean useBucketExecutorForLidSpaceCompact() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"baldersheim"}) default boolean useBucketExecutorForBucketMove() { throw new UnsupportedOperationException("TODO specify default value"); }
        @ModelFeatureFlag(owners = {"geirst"}) default boolean enableFeedBlockInDistributor() { return true; }
        @ModelFeatureFlag(owners = {"baldersheim", "geirst", "toregge"}) default double maxDeadBytesRatio() { return 0.2; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int clusterControllerMaxHeapSizeInMb() { return 128; }
        @ModelFeatureFlag(owners = {"hmusum"}) default int metricsProxyMaxHeapSizeInMb(ClusterSpec.Type type) { return 256; }
        @ModelFeatureFlag(owners = {"bjorncs", "tokle"}) default List<String> allowedAthenzProxyIdentities() { return List.of(); }
        @ModelFeatureFlag(owners = {"tokle"}) default boolean tenantIamRole() { return false; }
        @ModelFeatureFlag(owners = {"vekterli"}) default int maxActivationInhibitedOutOfSyncGroups() { return 0; }
    }

    /** Warning: As elsewhere in this package, do not make backwards incompatible changes that will break old config models! */
    interface Properties {
        FeatureFlags featureFlags();
        boolean multitenant();
        ApplicationId applicationId();
        List<ConfigServerSpec> configServerSpecs();
        HostName loadBalancerName();
        URI ztsUrl();
        String athenzDnsSuffix();
        boolean hostedVespa();
        Zone zone();
        Set<ContainerEndpoint> endpoints();
        boolean isBootstrap();
        boolean isFirstTimeDeployment();

        default Optional<EndpointCertificateSecrets> endpointCertificateSecrets() { return Optional.empty(); }

        default Optional<AthenzDomain> athenzDomain() { return Optional.empty(); }

        Optional<ApplicationRoles> applicationRoles();

        default Quota quota() { return Quota.unlimited(); }

        default List<TenantSecretStore> tenantSecretStores() { return List.of(); }

        /// Default setting for the gc-options attribute if not specified explicit by application
        String jvmGCOptions();

        // Note: Used in unit tests (set to false in TestProperties) to avoid needing to deal with implicitly created node for logserver
        default boolean useDedicatedNodeForLogserver() { return true; }

        default boolean dedicatedClusterControllerCluster() { return hostedVespa(); }

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ModelFeatureFlag {
        String[] owners();
        String removeAfter() default ""; // On the form "7.100.10"
        String comment() default "";
    }

}
