// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationCuratorDatabase;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.SessionZooKeeperClient;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import com.yahoo.vespa.config.server.tenant.ApplicationRolesStore;
import com.yahoo.vespa.config.server.tenant.ContainerEndpointsCache;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadataStore;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateRetriever;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;

import java.util.Map;
import java.util.Optional;
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
    private final PermanentApplicationPackage permanentApplicationPackage;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final Metrics metrics;
    private final Curator curator;
    private final FlagSource flagSource;
    private final SecretStore secretStore;

    public ActivatedModelsBuilder(TenantName tenant,
                                  long applicationGeneration,
                                  SessionZooKeeperClient zkClient,
                                  GlobalComponentRegistry globalComponentRegistry) {
        super(globalComponentRegistry.getModelFactoryRegistry(),
              globalComponentRegistry.getConfigserverConfig(),
              globalComponentRegistry.getZone(),
              HostProvisionerProvider.from(globalComponentRegistry.getHostProvisioner()));
        this.tenant = tenant;
        this.applicationGeneration = applicationGeneration;
        this.zkClient = zkClient;
        this.permanentApplicationPackage = globalComponentRegistry.getPermanentApplicationPackage();
        this.configDefinitionRepo = globalComponentRegistry.getStaticConfigDefinitionRepo();
        this.metrics = globalComponentRegistry.getMetrics();
        this.curator = globalComponentRegistry.getCurator();
        this.flagSource = globalComponentRegistry.getFlagSource();
        this.secretStore = globalComponentRegistry.getSecretStore();
    }

    @Override
    protected Application buildModelVersion(ModelFactory modelFactory,
                                            ApplicationPackage applicationPackage,
                                            ApplicationId applicationId,
                                            Optional<DockerImage> wantedDockerImageRepository,
                                            Version wantedNodeVespaVersion,
                                            Optional<AllocatedHosts> ignored // Ignored since we have this in the app package for activated models
    ) {
        log.log(Level.FINE, String.format("Loading model version %s for session %s application %s",
                                          modelFactory.version(), applicationGeneration, applicationId));
        ModelContext.Properties modelContextProperties = createModelContextProperties(applicationId);
        Provisioned provisioned = new Provisioned();
        ModelContext modelContext = new ModelContextImpl(
                applicationPackage,
                Optional.empty(),
                permanentApplicationPackage.applicationPackage(),
                new SilentDeployLogger(),
                configDefinitionRepo,
                getForVersionOrLatest(applicationPackage.getFileRegistries(), modelFactory.version()).orElse(new MockFileRegistry()),
                new ApplicationCuratorDatabase(tenant, curator).readReindexingStatus(applicationId),
                createStaticProvisioner(applicationPackage.getAllocatedHosts(),
                                        modelContextProperties.applicationId(),
                                        provisioned),
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
                               applicationPackage.getMetaData().isInternalRedeploy(),
                               modelFactory.version(),
                               applicationMetricUpdater,
                               applicationId);
    }

    private static <T> Optional<T> getForVersionOrLatest(Map<Version, T> map, Version version) {
        if (map.isEmpty()) {
            return Optional.empty();
        }
        T value = map.get(version);
        if (value == null) {
            value = map.get(map.keySet().stream().max((a, b) -> a.compareTo(b)).get());
        }
        return Optional.of(value);
    }

    private ModelContext.Properties createModelContextProperties(ApplicationId applicationId) {
        return new ModelContextImpl.Properties(applicationId,
                                               configserverConfig,
                                               zone(),
                                               ImmutableSet.copyOf(new ContainerEndpointsCache(TenantRepository.getTenantPath(tenant), curator).read(applicationId)),
                                               false, // We may be bootstrapping, but we only know and care during prepare
                                               false, // Always false, assume no one uses it when activating
                                               flagSource,
                                               new EndpointCertificateMetadataStore(curator, TenantRepository.getTenantPath(tenant))
                                                       .readEndpointCertificateMetadata(applicationId)
                                                       .flatMap(new EndpointCertificateRetriever(secretStore)::readEndpointCertificateSecrets),
                                               zkClient.readAthenzDomain(),
                                               new ApplicationRolesStore(curator, TenantRepository.getTenantPath(tenant))
                                                       .readApplicationRoles(applicationId),
                                               zkClient.readQuota());
    }

}
