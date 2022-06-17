// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.google.common.collect.ImmutableSet;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationCuratorDatabase;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.SessionZooKeeperClient;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import com.yahoo.vespa.config.server.tenant.ContainerEndpointsCache;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadataStore;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateRetriever;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds activated versions of the right model versions
 *
 * @author bratseth
 */
public class ActivatedModelsBuilder extends ModelsBuilder<Application> {

    private static final Logger log = Logger.getLogger(ActivatedModelsBuilder.class.getName());

    private final TenantName tenant;
    private final long applicationGeneration;
    private final SessionZooKeeperClient zkClient;
    private final Optional<ApplicationSet> currentActiveApplicationSet;
    private final PermanentApplicationPackage permanentApplicationPackage;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final Metrics metrics;
    private final Curator curator;
    private final FlagSource flagSource;
    private final SecretStore secretStore;
    private final ExecutorService executor;

    public ActivatedModelsBuilder(TenantName tenant,
                                  long applicationGeneration,
                                  SessionZooKeeperClient zkClient,
                                  Optional<ApplicationSet> currentActiveApplicationSet,
                                  ExecutorService executor,
                                  Curator curator,
                                  Metrics metrics,
                                  PermanentApplicationPackage permanentApplicationPackage,
                                  FlagSource flagSource,
                                  SecretStore secretStore,
                                  HostProvisionerProvider hostProvisionerProvider,
                                  ConfigserverConfig configserverConfig,
                                  Zone zone,
                                  ModelFactoryRegistry modelFactoryRegistry,
                                  ConfigDefinitionRepo configDefinitionRepo) {
        super(modelFactoryRegistry, configserverConfig, zone, hostProvisionerProvider, new SilentDeployLogger());
        this.tenant = tenant;
        this.applicationGeneration = applicationGeneration;
        this.zkClient = zkClient;
        this.currentActiveApplicationSet = currentActiveApplicationSet;
        this.permanentApplicationPackage = permanentApplicationPackage;
        this.configDefinitionRepo = configDefinitionRepo;
        this.metrics = metrics;
        this.curator = curator;
        this.flagSource = flagSource;
        this.secretStore = secretStore;
        this.executor = executor;
    }

    @Override
    protected Application buildModelVersion(ModelFactory modelFactory,
                                            ApplicationPackage applicationPackage,
                                            ApplicationId applicationId,
                                            Optional<DockerImage> wantedDockerImageRepository,
                                            Version wantedNodeVespaVersion) {
        log.log(Level.FINE, () -> String.format("Loading model version %s for session %s application %s",
                                                modelFactory.version(), applicationGeneration, applicationId));
        ModelContext.Properties modelContextProperties = createModelContextProperties(applicationId, modelFactory.version(), applicationPackage);
        Provisioned provisioned = new Provisioned();
        ModelContext modelContext = new ModelContextImpl(
                applicationPackage,
                modelOf(modelFactory.version()),
                permanentApplicationPackage.applicationPackage(),
                new SilentDeployLogger(),
                configDefinitionRepo,
                getForVersionOrLatest(applicationPackage.getFileRegistries(), modelFactory.version()).orElse(new MockFileRegistry()),
                executor,
                new ApplicationCuratorDatabase(tenant, curator).readReindexingStatus(applicationId),
                createStaticProvisioner(applicationPackage, modelContextProperties.applicationId(), provisioned),
                provisioned,
                modelContextProperties,
                Optional.empty(),
                wantedDockerImageRepository,
                modelFactory.version(),
                wantedNodeVespaVersion);
        MetricUpdater applicationMetricUpdater = metrics.getOrCreateMetricUpdater(Metrics.createDimensions(applicationId));
        ServerCache serverCache = new ServerCache(configDefinitionRepo, zkClient.getUserConfigDefinitions());
        return new Application(modelFactory.createModel(modelContext),
                               serverCache,
                               applicationGeneration,
                               modelFactory.version(),
                               applicationMetricUpdater,
                               applicationId);
    }

    private Optional<Model> modelOf(Version version) {
        if (currentActiveApplicationSet.isEmpty()) return Optional.empty();
        return currentActiveApplicationSet.get().get(version).map(Application::getModel);
    }

    private static <T> Optional<T> getForVersionOrLatest(Map<Version, T> map, Version version) {
        if (map.isEmpty()) {
            return Optional.empty();
        }
        T value = map.get(version);
        if (value == null) {
            value = map.get(map.keySet().stream().max(Comparator.naturalOrder()).get());
        }
        return Optional.of(value);
    }

    private ModelContext.Properties createModelContextProperties(ApplicationId applicationId,
                                                                 Version modelVersion,
                                                                 ApplicationPackage applicationPackage) {
        return new ModelContextImpl.Properties(applicationId,
                                               modelVersion,
                                               configserverConfig,
                                               zone(),
                                               ImmutableSet.copyOf(new ContainerEndpointsCache(TenantRepository.getTenantPath(tenant), curator).read(applicationId)),
                                               false, // We may be bootstrapping, but we only know and care during prepare
                                               false, // Always false, assume no one uses it when activating
                                               LegacyFlags.from(applicationPackage, flagSource),
                                               new EndpointCertificateMetadataStore(curator, TenantRepository.getTenantPath(tenant))
                                                       .readEndpointCertificateMetadata(applicationId)
                                                       .flatMap(new EndpointCertificateRetriever(secretStore)::readEndpointCertificateSecrets),
                                               zkClient.readAthenzDomain(),
                                               zkClient.readQuota(),
                                               zkClient.readTenantSecretStores(),
                                               secretStore,
                                               zkClient.readOperatorCertificates(),
                                               zkClient.readCloudAccount());
    }

}
