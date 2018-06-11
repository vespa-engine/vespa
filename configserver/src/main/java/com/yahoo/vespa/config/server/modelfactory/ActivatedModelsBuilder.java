// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.tenant.Rotations;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.session.SessionZooKeeperClient;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import com.yahoo.vespa.curator.Curator;

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
    private final Optional<PermanentApplicationPackage> permanentApplicationPackage;
    private final ConfigserverConfig configserverConfig;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final Metrics metrics;
    private final Curator curator;
    private final DeployLogger logger;

    public ActivatedModelsBuilder(TenantName tenant, long appGeneration, SessionZooKeeperClient zkClient, GlobalComponentRegistry globalComponentRegistry) {
        super(globalComponentRegistry.getModelFactoryRegistry(), 
              globalComponentRegistry.getHostProvisioner().isPresent(),
              globalComponentRegistry.getZone());
        this.tenant = tenant;
        this.appGeneration = appGeneration;
        this.zkClient = zkClient;
        this.permanentApplicationPackage = Optional.of(globalComponentRegistry.getPermanentApplicationPackage());
        this.configserverConfig = globalComponentRegistry.getConfigserverConfig();
        this.configDefinitionRepo = globalComponentRegistry.getConfigDefinitionRepo();
        this.metrics = globalComponentRegistry.getMetrics();
        this.curator = globalComponentRegistry.getCurator();
        this.logger = new SilentDeployLogger();
    }

    @Override
    protected Application buildModelVersion(ModelFactory modelFactory,
                                            ApplicationPackage applicationPackage,
                                            ApplicationId applicationId,
                                            com.yahoo.component.Version wantedNodeVespaVersion,
                                            Optional<AllocatedHosts> ignored, // Ignored since we have this in the app package for activated models
                                            Instant now) {
        log.log(LogLevel.DEBUG, String.format("Loading model version %s for session %s application %s",
                                              modelFactory.getVersion(), appGeneration, applicationId));
        ServerCache cache = zkClient.loadServerCache();
        ModelContext modelContext = new ModelContextImpl(
                applicationPackage,
                Optional.empty(),
                permanentApplicationPackage.get().applicationPackage(),
                logger,
                configDefinitionRepo,
                getForVersionOrLatest(applicationPackage.getFileRegistryMap(), modelFactory.getVersion()).orElse(new MockFileRegistry()),
                createStaticProvisioner(applicationPackage.getAllocatedHosts()),
                createModelContextProperties(applicationId),
                Optional.empty(),
                new com.yahoo.component.Version(modelFactory.getVersion().toString()),
                wantedNodeVespaVersion);
        MetricUpdater applicationMetricUpdater = metrics.getOrCreateMetricUpdater(Metrics.createDimensions(applicationId));
        Logger.getLogger("REDEPLOY").info("ApplicationModelsBuilder.buildModelVersion: " + applicationId + " generation " + appGeneration + " created with internalRedeploy=" + applicationPackage.getMetaData().isInternalRedeploy());
        return new Application(modelFactory.createModel(modelContext), cache, appGeneration,
                               applicationPackage.getMetaData().isInternalRedeploy(),
                               modelFactory.getVersion(),
                               applicationMetricUpdater, applicationId);
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
        return createModelContextProperties(
                applicationId,
                configserverConfig,
                zone(),
                new Rotations(curator, TenantRepository.getTenantPath(tenant)).readRotationsFromZooKeeper(applicationId));
    }

}
