// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ApplicationRoles;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.lang.SettableOptional;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationCuratorDatabase;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.config.server.deploy.ZooKeeperDeployer;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionFactory;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionProvider;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.modelfactory.PreparedModelsBuilder;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.tenant.ApplicationRolesStore;
import com.yahoo.vespa.config.server.tenant.ContainerEndpointsCache;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadataStore;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateRetriever;
import com.yahoo.vespa.config.server.tenant.SecretStoreExternalIdRetriever;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A SessionPreparer is responsible for preparing a session given an application package.
 *
 * @author Ulf Lilleengen
 */
public class SessionPreparer {

    private static final Logger log = Logger.getLogger(SessionPreparer.class.getName());

    private final ModelFactoryRegistry modelFactoryRegistry;
    private final FileDistributionFactory fileDistributionFactory;
    private final HostProvisionerProvider hostProvisionerProvider;
    private final PermanentApplicationPackage permanentApplicationPackage;
    private final ConfigserverConfig configserverConfig;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final Curator curator;
    private final Zone zone;
    private final SecretStore secretStore;
    private final FlagSource flagSource;

    public SessionPreparer(ModelFactoryRegistry modelFactoryRegistry,
                           FileDistributionFactory fileDistributionFactory,
                           HostProvisionerProvider hostProvisionerProvider,
                           PermanentApplicationPackage permanentApplicationPackage,
                           ConfigserverConfig configserverConfig,
                           ConfigDefinitionRepo configDefinitionRepo,
                           Curator curator,
                           Zone zone,
                           FlagSource flagSource,
                           SecretStore secretStore) {
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.fileDistributionFactory = fileDistributionFactory;
        this.hostProvisionerProvider = hostProvisionerProvider;
        this.permanentApplicationPackage = permanentApplicationPackage;
        this.configserverConfig = configserverConfig;
        this.configDefinitionRepo = configDefinitionRepo;
        this.curator = curator;
        this.zone = zone;
        this.secretStore = secretStore;
        this.flagSource = flagSource;
    }

    /**
     * Prepares a session (validates, builds model, writes to zookeeper and distributes files)
     *
     * @param hostValidator               host validator
     * @param logger                      for storing logs returned in response to client.
     * @param params                      parameters controlling behaviour of prepare.
     * @param activeApplicationSet        set of currently active applications.
     * @return the config change actions that must be done to handle the activation of the models prepared.
     */
    public PrepareResult prepare(HostValidator<ApplicationId> hostValidator, DeployLogger logger, PrepareParams params,
                                 Optional<ApplicationSet> activeApplicationSet, Instant now, File serverDbSessionDir,
                                 ApplicationPackage applicationPackage, SessionZooKeeperClient sessionZooKeeperClient) {
        ApplicationId applicationId = params.getApplicationId();
        Preparation preparation = new Preparation(hostValidator, logger, params, activeApplicationSet,
                                                  TenantRepository.getTenantPath(applicationId.tenant()),
                                                  serverDbSessionDir, applicationPackage, sessionZooKeeperClient);
        preparation.preprocess();
        try {
            AllocatedHosts allocatedHosts = preparation.buildModels(now);
            preparation.makeResult(allocatedHosts);
            if ( ! params.isDryRun()) {
                preparation.writeStateZK();
                preparation.writeEndpointCertificateMetadataZK();
                preparation.writeContainerEndpointsZK();
                preparation.writeApplicationRoles();
                preparation.distribute();
            }
            log.log(Level.FINE, () -> "time used " + params.getTimeoutBudget().timesUsed() + " : " + applicationId);
            return preparation.result();
        }
        catch (IllegalArgumentException e) {
            throw new InvalidApplicationException("Invalid application package", e);
        }
    }

    private class Preparation {

        final DeployLogger logger;
        final PrepareParams params;

        final ApplicationId applicationId;

        /** The repository part of docker image to be used for this deployment */
        final Optional<DockerImage> dockerImageRepository;

        /** The version of Vespa the application to be prepared specifies for its nodes */
        final Version vespaVersion;

        final ContainerEndpointsCache containerEndpointsCache;
        final List<ContainerEndpoint> containerEndpoints;
        final ModelContext.Properties properties;
        private final EndpointCertificateMetadataStore endpointCertificateMetadataStore;
        private final Optional<EndpointCertificateMetadata> endpointCertificateMetadata;
        private final Optional<AthenzDomain> athenzDomain;
        private final ApplicationRolesStore applicationRolesStore;
        private final Optional<ApplicationRoles> applicationRoles;
        private final ApplicationPackage applicationPackage;
        private final SessionZooKeeperClient sessionZooKeeperClient;

        private ApplicationPackage preprocessedApplicationPackage;
        private List<PreparedModelsBuilder.PreparedModelResult> modelResultList;
        private PrepareResult prepareResult;

        private final PreparedModelsBuilder preparedModelsBuilder;
        private final FileDistributionProvider fileDistributionProvider;

        Preparation(HostValidator<ApplicationId> hostValidator, DeployLogger logger, PrepareParams params,
                    Optional<ApplicationSet> currentActiveApplicationSet, Path tenantPath,
                    File serverDbSessionDir, ApplicationPackage applicationPackage,
                    SessionZooKeeperClient sessionZooKeeperClient) {
            this.logger = logger;
            this.params = params;
            this.applicationPackage = applicationPackage;
            this.sessionZooKeeperClient = sessionZooKeeperClient;
            this.applicationId = params.getApplicationId();
            this.dockerImageRepository = params.dockerImageRepository();
            this.vespaVersion = params.vespaVersion().orElse(Vtag.currentVersion);
            this.containerEndpointsCache = new ContainerEndpointsCache(tenantPath, curator);
            this.endpointCertificateMetadataStore = new EndpointCertificateMetadataStore(curator, tenantPath);
            EndpointCertificateRetriever endpointCertificateRetriever = new EndpointCertificateRetriever(secretStore);
            this.endpointCertificateMetadata = params.endpointCertificateMetadata();
            Optional<EndpointCertificateSecrets> endpointCertificateSecrets = endpointCertificateMetadata
                    .or(() -> endpointCertificateMetadataStore.readEndpointCertificateMetadata(applicationId))
                    .flatMap(endpointCertificateRetriever::readEndpointCertificateSecrets);
            this.containerEndpoints = readEndpointsIfNull(params.containerEndpoints());
            this.athenzDomain = params.athenzDomain();
            this.applicationRolesStore = new ApplicationRolesStore(curator, tenantPath);
            this.applicationRoles = params.applicationRoles()
                    .or(() -> applicationRolesStore.readApplicationRoles(applicationId));
            this.properties = new ModelContextImpl.Properties(params.getApplicationId(),
                                                              configserverConfig,
                                                              zone,
                                                              Set.copyOf(containerEndpoints),
                                                              params.isBootstrap(),
                                                              currentActiveApplicationSet.isEmpty(),
                                                              flagSource,
                                                              endpointCertificateSecrets,
                                                              athenzDomain,
                                                              applicationRoles,
                                                              params.quota(),
                                                              params.tenantSecretStores(),
                                                              secretStore);
            this.fileDistributionProvider = fileDistributionFactory.createProvider(serverDbSessionDir);
            this.preparedModelsBuilder = new PreparedModelsBuilder(modelFactoryRegistry,
                                                                   permanentApplicationPackage,
                                                                   configDefinitionRepo,
                                                                   fileDistributionProvider,
                                                                   hostProvisionerProvider,
                                                                   curator,
                                                                   hostValidator,
                                                                   logger,
                                                                   params,
                                                                   currentActiveApplicationSet,
                                                                   properties,
                                                                   configserverConfig);
        }

        void checkTimeout(String step) {
            TimeoutBudget timeoutBudget = params.getTimeoutBudget();
            if (! timeoutBudget.hasTimeLeft(step)) {
                String used = timeoutBudget.timesUsed();
                throw new UncheckedTimeoutException("prepare timed out " + used + " after " + step +
                        " step (timeout " + timeoutBudget.timeout() + "): " + applicationId);
            }
        }

        Optional<FileReference> distributedApplicationPackage() {
            FileRegistry fileRegistry = fileDistributionProvider.getFileRegistry();
            FileReference fileReference = fileRegistry.addApplicationPackage();
            FileDistribution fileDistribution = fileDistributionProvider.getFileDistribution();
            log.log(Level.FINE, () -> "Distribute application package for " + applicationId + " ("  + fileReference + ") to other config servers");
            properties.configServerSpecs().stream()
                    .filter(spec -> ! spec.getHostName().equals(fileRegistry.fileSourceHost()))
                    .forEach(spec -> fileDistribution.startDownload(spec.getHostName(), spec.getConfigServerPort(), Set.of(fileReference)));

            checkTimeout("distributeApplicationPackage");
            return Optional.of(fileReference);
        }

        void preprocess() {
            try {
                this.preprocessedApplicationPackage = applicationPackage.preprocess(properties.zone(), logger);
            } catch (IOException | RuntimeException e) {
                throw new IllegalArgumentException("Error preprocessing application package for " + applicationId, e);
            }
            checkTimeout("preprocess");
        }

        AllocatedHosts buildModels(Instant now) {
            SettableOptional<AllocatedHosts> allocatedHosts = new SettableOptional<>();
            this.modelResultList = preparedModelsBuilder.buildModels(applicationId, dockerImageRepository, vespaVersion,
                                                                     preprocessedApplicationPackage, allocatedHosts, now);
            checkTimeout("build models");
            return allocatedHosts.get();
        }

        void makeResult(AllocatedHosts allocatedHosts) {
            this.prepareResult = new PrepareResult(allocatedHosts, modelResultList);
            checkTimeout("making result from models");
        }

        void writeStateZK() {
            log.log(Level.FINE, "Writing application package state to zookeeper");
            writeStateToZooKeeper(sessionZooKeeperClient,
                                  preprocessedApplicationPackage,
                                  applicationId,
                                  distributedApplicationPackage(),
                                  dockerImageRepository,
                                  vespaVersion,
                                  logger,
                                  prepareResult.getFileRegistries(),
                                  prepareResult.allocatedHosts(),
                                  athenzDomain,
                                  params.quota(),
                                  params.tenantSecretStores());
            checkTimeout("write state to zookeeper");
        }

        void writeEndpointCertificateMetadataZK() {
            endpointCertificateMetadata.ifPresent(metadata ->
                    endpointCertificateMetadataStore.writeEndpointCertificateMetadata(applicationId, metadata));
            checkTimeout("write endpoint certificate metadata to zookeeper");
        }

        void writeContainerEndpointsZK() {
            containerEndpointsCache.write(applicationId, containerEndpoints);
            checkTimeout("write container endpoints to zookeeper");
        }

        void writeApplicationRoles() {
            applicationRoles.ifPresent(roles -> applicationRolesStore.writeApplicationRoles(applicationId, roles));
            checkTimeout("write application roles to zookeeper");
        }

        void distribute() {
            prepareResult.asList().forEach(modelResult -> modelResult.model
                                           .distributeFiles(modelResult.fileDistributionProvider.getFileDistribution()));
            checkTimeout("distribute files");
        }

        PrepareResult result() {
            return prepareResult;
        }

        private List<ContainerEndpoint> readEndpointsIfNull(List<ContainerEndpoint> endpoints) {
            if (endpoints == null) { // endpoints are only set when prepared via HTTP
                endpoints = this.containerEndpointsCache.read(applicationId);
            }
            return List.copyOf(endpoints);
        }

    }

    private void writeStateToZooKeeper(SessionZooKeeperClient zooKeeperClient,
                                       ApplicationPackage applicationPackage,
                                       ApplicationId applicationId,
                                       Optional<FileReference> distributedApplicationPackage,
                                       Optional<DockerImage> dockerImageRepository,
                                       Version vespaVersion,
                                       DeployLogger deployLogger,
                                       Map<Version, FileRegistry> fileRegistryMap,
                                       AllocatedHosts allocatedHosts,
                                       Optional<AthenzDomain> athenzDomain,
                                       Optional<Quota> quota,
                                       List<TenantSecretStore> tenantSecretStores) {
        ZooKeeperDeployer zkDeployer = zooKeeperClient.createDeployer(deployLogger);
        try {
            zkDeployer.deploy(applicationPackage, fileRegistryMap, allocatedHosts);
            // Note: When changing the below you need to also change similar calls in SessionRepository.createSessionFromExisting()
            zooKeeperClient.writeApplicationId(applicationId);
            zooKeeperClient.writeApplicationPackageReference(distributedApplicationPackage);
            zooKeeperClient.writeVespaVersion(vespaVersion);
            zooKeeperClient.writeDockerImageRepository(dockerImageRepository);
            zooKeeperClient.writeAthenzDomain(athenzDomain);
            zooKeeperClient.writeQuota(quota);
            zooKeeperClient.writeTenantSecretStores(tenantSecretStores);
        } catch (RuntimeException | IOException e) {
            zkDeployer.cleanup();
            throw new RuntimeException("Error preparing session", e);
        }
    }

    /** The result of preparation over all model versions */
    static class PrepareResult {

        private final AllocatedHosts allocatedHosts;
        private final List<PreparedModelsBuilder.PreparedModelResult> results;
        
        public PrepareResult(AllocatedHosts allocatedHosts, List<PreparedModelsBuilder.PreparedModelResult> results) {
            this.allocatedHosts = allocatedHosts;
            this.results = List.copyOf(results);
        }

        /** Returns the results for each model as an immutable list */
        public List<PreparedModelsBuilder.PreparedModelResult> asList() { return results; }

        /** Returns the host allocations resulting from this preparation. */
        public AllocatedHosts allocatedHosts() { return allocatedHosts; }

        public Map<Version, FileRegistry> getFileRegistries() {
            return results.stream()
                    .collect(Collectors.toMap((prepareResult -> prepareResult.version),
                            (prepareResult -> prepareResult.fileDistributionProvider.getFileRegistry())));
        }

        /**
         * Collects the config change actions from all model factory creations and returns the aggregated union of these actions.
         * A system in the process of upgrading Vespa will have hosts running both version X and Y, and this will change
         * during the upgrade process. Trying to be smart about which actions to perform on which hosts depending
         * on the version running will be a nightmare to maintain. A pragmatic approach is therefore to just use the
         * union of all actions as this will give the correct end result at the cost of perhaps restarting nodes twice
         * (once for the upgrading case and once for a potential restart action).
         */
         public ConfigChangeActions getConfigChangeActions() {
            return new ConfigChangeActions(results.stream().map(result -> result.actions)
                                                           .flatMap(Collection::stream)
                                                           .collect(Collectors.toList()));
         }

    }

    /**
     * During model building each model version will request nodes allocated (from the node allocator)
     * for each cluster specified by that model. As allocations are stable this should usually
     * result in the same allocations for the same clusters across all model versions,
     * otherwise we should fail this preparation as such inconsistencies lead to undefined behavior,
     * and there is really just one true allocation (for a given cluster) to be activated in the node repository.
     * 
     * However, these disagreements between allocations in each model version are allowed:
     * - A node may be retired in some model version but not another. This allows model versions to change cluster sizes,
     *   and is ok because the system will converge on the latest version's opinion
     * - Clusters may be present on some version but not on another. This does not lead to inconsistency
     *   and allows new model versions to introduce new clusters.
     *   
     * For each cluster, the newest model version which has that cluster decides the correct retirement status of nodes
     * (and all model versions having the cluster must have the same nodes).
     * 
     * This class ensures these constraints and returns a reconciliated set of nodes which should be activated,
     * given a set of model activation results.
     */
    @SuppressWarnings("unused")
    private static final class ReconciliatedHostAllocations {
        
        public ReconciliatedHostAllocations(List<PreparedModelsBuilder.PreparedModelResult> results) {
            
        }

    }
    
}
