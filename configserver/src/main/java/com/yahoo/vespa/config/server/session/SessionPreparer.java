// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.ValidationProcessor;
import com.yahoo.config.application.XmlPreProcessor;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.Tags;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.net.HostName;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.ConfigServerSpec;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.deploy.ZooKeeperDeployer;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionFactory;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.modelfactory.AllocatedHostsFromAllModels;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.modelfactory.PreparedModelsBuilder;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.tenant.ContainerEndpointsCache;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadataStore;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateRetriever;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.model.application.validation.BundleValidator;
import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

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
    private final ConfigserverConfig configserverConfig;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final Curator curator;
    private final Zone zone;
    private final SecretStore secretStore;
    private final FlagSource flagSource;
    private final ExecutorService executor;

    public SessionPreparer(ModelFactoryRegistry modelFactoryRegistry,
                           FileDistributionFactory fileDistributionFactory,
                           ExecutorService executor,
                           HostProvisionerProvider hostProvisionerProvider,
                           ConfigserverConfig configserverConfig,
                           ConfigDefinitionRepo configDefinitionRepo,
                           Curator curator,
                           Zone zone,
                           FlagSource flagSource,
                           SecretStore secretStore) {
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.fileDistributionFactory = fileDistributionFactory;
        this.hostProvisionerProvider = hostProvisionerProvider;
        this.configserverConfig = configserverConfig;
        this.configDefinitionRepo = configDefinitionRepo;
        this.curator = curator;
        this.zone = zone;
        this.secretStore = secretStore;
        this.flagSource = flagSource;
        this.executor = executor;
    }

    ExecutorService getExecutor() { return executor; }

    /**
     * Prepares a session (validates, builds model, writes to zookeeper and distributes files)
     *
     * @param hostValidator               host validator
     * @param logger                      for storing logs returned in response to client.
     * @param params                      parameters controlling behaviour of prepare.
     * @param activeApplicationSet        set of currently active applications.
     * @return the config change actions that must be done to handle the activation of the models prepared.
     */
    public PrepareResult prepare(HostValidator hostValidator, DeployLogger logger, PrepareParams params,
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
                FileReference fileReference = preparation.startDistributionOfApplicationPackage();
                preparation.writeStateZK(fileReference);
                preparation.writeEndpointCertificateMetadataZK();
                preparation.writeContainerEndpointsZK();
            }
            log.log(Level.FINE, () -> "time used " + params.getTimeoutBudget().timesUsed() + " : " + applicationId);
            return preparation.result();
        }
        catch (IllegalArgumentException e) {
            if (e instanceof InvalidApplicationException)
                throw e;
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
        private final EndpointCertificateMetadataStore endpointCertificateMetadataStore;
        private final Optional<EndpointCertificateMetadata> endpointCertificateMetadata;
        private final Optional<AthenzDomain> athenzDomain;
        private final ApplicationPackage applicationPackage;
        private final SessionZooKeeperClient sessionZooKeeperClient;

        private ApplicationPackage preprocessedApplicationPackage;
        private List<PreparedModelsBuilder.PreparedModelResult> modelResultList;
        private PrepareResult prepareResult;

        private final PreparedModelsBuilder preparedModelsBuilder;
        private final FileRegistry fileRegistry;

        Preparation(HostValidator hostValidator, DeployLogger logger, PrepareParams params,
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
            this.fileRegistry = fileDistributionFactory.createFileRegistry(serverDbSessionDir);
            this.preparedModelsBuilder = new PreparedModelsBuilder(modelFactoryRegistry,
                                                                   flagSource,
                                                                   secretStore,
                                                                   containerEndpoints,
                                                                   endpointCertificateSecrets,
                                                                   configDefinitionRepo,
                                                                   fileRegistry,
                                                                   executor,
                                                                   hostProvisionerProvider,
                                                                   curator,
                                                                   hostValidator,
                                                                   logger,
                                                                   params,
                                                                   currentActiveApplicationSet,
                                                                   configserverConfig,
                                                                   zone);
        }

        void checkTimeout(String step) {
            TimeoutBudget timeoutBudget = params.getTimeoutBudget();
            if (! timeoutBudget.hasTimeLeft(step)) {
                String used = timeoutBudget.timesUsed();
                throw new UncheckedTimeoutException("prepare timed out " + used + " after " + step +
                                                    " step (timeout " + timeoutBudget.timeout() + "): " + applicationId);
            }
        }

        FileReference startDistributionOfApplicationPackage() {
            FileReference fileReference = fileRegistry.addApplicationPackage();
            FileDistribution fileDistribution = fileDistributionFactory.createFileDistribution();
            log.log(Level.FINE, () -> "Ask other config servers to download application package for " +
                    applicationId + " (" + fileReference + ")");
            ConfigServerSpec.fromConfig(configserverConfig)
                      .stream()
                      .filter(spec -> !spec.getHostName().equals(HostName.getLocalhost()))
                      .forEach(spec -> fileDistribution.startDownload(spec.getHostName(), spec.getConfigServerPort(), Set.of(fileReference)));

            checkTimeout("startDistributionOfApplicationPackage");
            return fileReference;
        }

        void preprocess() {
            try {
                validateXmlFeatures(applicationPackage, logger);
                this.preprocessedApplicationPackage = applicationPackage.preprocess(zone, logger);
            } catch (IOException | RuntimeException e) {
                throw new IllegalArgumentException("Error preprocessing application package for " + applicationId +
                                                   ", session " + sessionZooKeeperClient.sessionId(), e);
            }
            checkTimeout("preprocess");
        }

        /**
         * Warn on use of deprecated XML features
         */
        private void validateXmlFeatures(ApplicationPackage applicationPackage, DeployLogger logger) {
            // TODO: Validate no use of XInclude, datatype definitions or external entities
            //       in any xml file we parse, such as services.xml, deployment.xml, hosts.xml,
            //       validation-overrides.xml and any pom.xml files in OSGi bundles
            //       services.xml and hosts.xml will need to be preprocessed by our own processors first

            File applicationPackageDir = applicationPackage.getFileReference(Path.fromString("."));
            File servicesXml = applicationPackage.getFileReference(Path.fromString("services.xml"));
            File hostsXml = applicationPackage.getFileReference(Path.fromString("hosts.xml"));

            // Validate after doing our own preprocessing on these two files
            ApplicationMetaData meta = applicationPackage.getMetaData();
            InstanceName instance = meta.getApplicationId().instance();
            Tags tags = applicationPackage.getDeploymentSpec().instance(instance)
                                          .map(DeploymentInstanceSpec::tags)
                                          .orElse(Tags.empty());
            if (servicesXml.exists()) {
                vespaPreprocess(applicationPackageDir.getAbsoluteFile(), servicesXml, meta, tags);
            }
            if (hostsXml.exists()) {
                vespaPreprocess(applicationPackageDir.getAbsoluteFile(), hostsXml, meta, tags);
            }

            // Validate pom.xml files in OSGi bundles
            try (var paths = Files.find(applicationPackageDir.getAbsoluteFile().toPath(), Integer.MAX_VALUE,
                    (path, attr) -> attr.isRegularFile() && path.getFileName().toString().matches(".*\\.[Jj][Aa][Rr]"))) {
                        paths.forEach(jarPath -> {
                            try {
                                new BundleValidator().getPomXmlContent(logger, new JarFile(jarPath.toFile())).ifPresent(pom -> {
                                    try {
                                        new ValidationProcessor().process(pom);
                                    }
                                    catch (IOException | TransformerException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                            } catch (ZipException e) {
                                // ignore for tests
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void vespaPreprocess(File appDir, File inputXml, ApplicationMetaData metaData, Tags tags) {
            try {
                InstanceName instance = metaData.getApplicationId().instance();
                new XmlPreProcessor(appDir,
                                    inputXml,
                                    instance,
                                    zone.environment(),
                                    zone.region(),
                                    tags)
                        .run();
            } catch (ParserConfigurationException | IOException | SAXException | TransformerException e) {
                throw new RuntimeException(e);
            }
        }

        AllocatedHosts buildModels(Instant now) {
            var allocatedHosts = new AllocatedHostsFromAllModels();
            this.modelResultList = preparedModelsBuilder.buildModels(applicationId, dockerImageRepository, vespaVersion,
                                                                     preprocessedApplicationPackage, allocatedHosts, now);
            checkTimeout("build models");
            return allocatedHosts.toAllocatedHosts();
        }

        void makeResult(AllocatedHosts allocatedHosts) {
            this.prepareResult = new PrepareResult(allocatedHosts, modelResultList);
            checkTimeout("making result from models");
        }

        void writeStateZK(FileReference filereference) {
            log.log(Level.FINE, "Writing application package state to zookeeper");
            writeStateToZooKeeper(sessionZooKeeperClient,
                                  preprocessedApplicationPackage,
                                  applicationId,
                                  filereference,
                                  dockerImageRepository,
                                  vespaVersion,
                                  logger,
                                  prepareResult.getFileRegistries(),
                                  prepareResult.allocatedHosts(),
                                  athenzDomain,
                                  params.quota(),
                                  params.tenantSecretStores(),
                                  params.operatorCertificates(),
                                  params.cloudAccount());
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
                                       FileReference fileReference,
                                       Optional<DockerImage> dockerImageRepository,
                                       Version vespaVersion,
                                       DeployLogger deployLogger,
                                       Map<Version, FileRegistry> fileRegistryMap,
                                       AllocatedHosts allocatedHosts,
                                       Optional<AthenzDomain> athenzDomain,
                                       Optional<Quota> quota,
                                       List<TenantSecretStore> tenantSecretStores,
                                       List<X509Certificate> operatorCertificates,
                                       Optional<CloudAccount> cloudAccount) {
        ZooKeeperDeployer zkDeployer = zooKeeperClient.createDeployer(deployLogger);
        try {
            zkDeployer.deploy(applicationPackage, fileRegistryMap, allocatedHosts);
            // Note: When changing the below you need to also change similar calls in SessionRepository.createSessionFromExisting()
            zooKeeperClient.writeApplicationId(applicationId);
            zooKeeperClient.writeApplicationPackageReference(Optional.of(fileReference));
            zooKeeperClient.writeVespaVersion(vespaVersion);
            zooKeeperClient.writeDockerImageRepository(dockerImageRepository);
            zooKeeperClient.writeAthenzDomain(athenzDomain);
            zooKeeperClient.writeQuota(quota);
            zooKeeperClient.writeTenantSecretStores(tenantSecretStores);
            zooKeeperClient.writeOperatorCertificates(operatorCertificates);
            zooKeeperClient.writeCloudAccount(cloudAccount);
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
                            (prepareResult -> prepareResult.fileRegistry)));
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
                                                           .toList());
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
