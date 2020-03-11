// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.ConfigServerSpec;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.SessionZooKeeperClient;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import com.yahoo.vespa.config.server.tenant.ContainerEndpointsCache;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateRetriever;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadataStore;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Builds activated versions of the right model versions
 *
 * @author bratseth
 */
public class ActivatedModelsBuilder extends ModelsBuilder<Application> {

    private static final Logger log = Logger.getLogger(ActivatedModelsBuilder.class.getName());

    private final TenantName tenant;
    private final long appGeneration;
    private final SessionZooKeeperClient zkClient;
    private final PermanentApplicationPackage permanentApplicationPackage;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final Metrics metrics;
    private final Curator curator;
    private final DeployLogger logger;
    private final FlagSource flagSource;
    private final SecretStore secretStore;

    public ActivatedModelsBuilder(TenantName tenant,
                                  long appGeneration,
                                  SessionZooKeeperClient zkClient,
                                  GlobalComponentRegistry globalComponentRegistry) {
        super(globalComponentRegistry.getModelFactoryRegistry(),
              globalComponentRegistry.getConfigserverConfig(),
              globalComponentRegistry.getZone(),
              HostProvisionerProvider.from(globalComponentRegistry.getHostProvisioner()));
        this.tenant = tenant;
        this.appGeneration = appGeneration;
        this.zkClient = zkClient;
        this.permanentApplicationPackage = globalComponentRegistry.getPermanentApplicationPackage();
        this.configDefinitionRepo = globalComponentRegistry.getStaticConfigDefinitionRepo();
        this.metrics = globalComponentRegistry.getMetrics();
        this.curator = globalComponentRegistry.getCurator();
        this.logger = new SilentDeployLogger();
        this.flagSource = globalComponentRegistry.getFlagSource();
        this.secretStore = globalComponentRegistry.getSecretStore();
    }

    @Override
    protected Application buildModelVersion(ModelFactory modelFactory,
                                            ApplicationPackage applicationPackage,
                                            ApplicationId applicationId,
                                            Optional<String> wantedDockerImageRepository,
                                            Version wantedNodeVespaVersion,
                                            Optional<AllocatedHosts> ignored, // Ignored since we have this in the app package for activated models
                                            Instant now) {
        log.log(LogLevel.DEBUG, String.format("Loading model version %s for session %s application %s",
                                              modelFactory.version(), appGeneration, applicationId));
        ModelContext.Properties modelContextProperties = createModelContextProperties(applicationId);
        ModelContext modelContext = new ModelContextImpl(
                applicationPackage,
                Optional.empty(),
                permanentApplicationPackage.applicationPackage(),
                logger,
                configDefinitionRepo,
                getForVersionOrLatest(applicationPackage.getFileRegistries(), modelFactory.version()).orElse(new MockFileRegistry()),
                createStaticProvisioner(applicationPackage.getAllocatedHosts(), modelContextProperties),
                modelContextProperties,
                Optional.empty(),
                wantedDockerImageRepository,
                modelFactory.version(),
                wantedNodeVespaVersion);
        MetricUpdater applicationMetricUpdater = metrics.getOrCreateMetricUpdater(Metrics.createDimensions(applicationId));
        ServerCache serverCache = new ServerCache(configDefinitionRepo, zkClient.getUserConfigDefinitions());
        return new Application(modelFactory.createModel(modelContext),
                               serverCache,
                               appGeneration,
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
                                               configserverConfig.multitenant(),
                                               ConfigServerSpec.fromConfig(configserverConfig),
                                               HostName.from(configserverConfig.loadBalancerAddress()),
                                               configserverConfig.ztsUrl() != null ? URI.create(configserverConfig.ztsUrl()) : null,
                                               configserverConfig.athenzDnsSuffix(),
                                               configserverConfig.hostedVespa(),
                                               zone(),
                                               ImmutableSet.copyOf(new ContainerEndpointsCache(TenantRepository.getTenantPath(tenant), curator).read(applicationId)),
                                               false, // We may be bootstrapping, but we only know and care during prepare
                                               false, // Always false, assume no one uses it when activating
                                               flagSource,
                                               new EndpointCertificateMetadataStore(curator, TenantRepository.getTenantPath(tenant))
                                                       .readEndpointCertificateMetadata(applicationId)
                                                       .flatMap(new EndpointCertificateRetriever(secretStore)::readEndpointCertificateSecrets));

    }

}
