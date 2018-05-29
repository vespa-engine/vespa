// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.session.SessionPreparer;
import com.yahoo.vespa.config.server.tenant.TenantListener;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;

import java.time.Clock;
import java.util.Optional;

/**
 * Registry containing all the "static"/"global" components in a config server in one place.
 *
 * @author Ulf Lilleengen
 */
public class InjectedGlobalComponentRegistry implements GlobalComponentRegistry {

    private final Curator curator;
    private final ConfigCurator configCurator;
    private final Metrics metrics;
    private final ModelFactoryRegistry modelFactoryRegistry;
    private final SessionPreparer sessionPreparer;
    private final RpcServer rpcServer;
    private final ConfigserverConfig configserverConfig;
    private final SuperModelGenerationCounter superModelGenerationCounter;
    private final ConfigDefinitionRepo defRepo;
    private final PermanentApplicationPackage permanentApplicationPackage;
    private final HostRegistries hostRegistries;
    private final Optional<Provisioner> hostProvisioner;
    private final Zone zone;

    @Inject
    public InjectedGlobalComponentRegistry(Curator curator,
                                           ConfigCurator configCurator,
                                           Metrics metrics,
                                           ModelFactoryRegistry modelFactoryRegistry,
                                           SessionPreparer sessionPreparer,
                                           RpcServer rpcServer,
                                           ConfigserverConfig configserverConfig,
                                           SuperModelGenerationCounter superModelGenerationCounter,
                                           ConfigDefinitionRepo defRepo,
                                           PermanentApplicationPackage permanentApplicationPackage,
                                           HostRegistries hostRegistries,
                                           HostProvisionerProvider hostProvisionerProvider,
                                           Zone zone) {
        this.curator = curator;
        this.configCurator = configCurator;
        this.metrics = metrics;
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.sessionPreparer = sessionPreparer;
        this.rpcServer = rpcServer;
        this.configserverConfig = configserverConfig;
        this.superModelGenerationCounter = superModelGenerationCounter;
        this.defRepo = defRepo;
        this.permanentApplicationPackage = permanentApplicationPackage;
        this.hostRegistries = hostRegistries;
        this.hostProvisioner = hostProvisionerProvider.getHostProvisioner();
        this.zone = zone;
    }

    @Override
    public Curator getCurator() { return curator; }
    @Override
    public ConfigCurator getConfigCurator() { return configCurator; }
    @Override
    public Metrics getMetrics() { return metrics; }
    @Override
    public SessionPreparer getSessionPreparer() { return sessionPreparer; }
    @Override
    public ConfigserverConfig getConfigserverConfig() { return configserverConfig; }
    @Override
    public TenantListener getTenantListener() { return rpcServer; }
    @Override
    public ReloadListener getReloadListener() { return rpcServer; }
    @Override
    public SuperModelGenerationCounter getSuperModelGenerationCounter() { return superModelGenerationCounter; }
    @Override
    public ConfigDefinitionRepo getConfigDefinitionRepo() { return defRepo; }
    @Override
    public PermanentApplicationPackage getPermanentApplicationPackage() { return permanentApplicationPackage; }
    @Override
    public HostRegistries getHostRegistries() { return hostRegistries; }
    @Override
    public ModelFactoryRegistry getModelFactoryRegistry() { return modelFactoryRegistry; }

    @Override
    public Optional<Provisioner> getHostProvisioner() {
        return hostProvisioner;
    }

    @Override
    public Zone getZone() {
        return zone;
    }

    @Override
    public Clock getClock() {return  Clock.systemUTC();}
}
