// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.tenant.TenantListener;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.flags.FlagSource;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Registry containing all the "static"/"global" components in a config server in one place.
 *
 * @author Ulf Lilleengen
 */
public class InjectedGlobalComponentRegistry implements GlobalComponentRegistry {

    private final ModelFactoryRegistry modelFactoryRegistry;
    private final RpcServer rpcServer;
    private final ConfigserverConfig configserverConfig;
    private final ConfigDefinitionRepo staticConfigDefinitionRepo;
    private final Optional<Provisioner> hostProvisioner;
    private final Zone zone;
    private final ConfigServerDB configServerDB;
    private final FlagSource flagSource;
    private final SecretStore secretStore;
    private final ExecutorService zkCacheExecutor;

    @SuppressWarnings("WeakerAccess")
    @Inject
    public InjectedGlobalComponentRegistry(ModelFactoryRegistry modelFactoryRegistry,
                                           RpcServer rpcServer,
                                           ConfigserverConfig configserverConfig,
                                           ConfigDefinitionRepo staticConfigDefinitionRepo,
                                           HostProvisionerProvider hostProvisionerProvider,
                                           Zone zone,
                                           ConfigServerDB configServerDB,
                                           FlagSource flagSource,
                                           SecretStore secretStore) {
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.rpcServer = rpcServer;
        this.configserverConfig = configserverConfig;
        this.staticConfigDefinitionRepo = staticConfigDefinitionRepo;
        this.hostProvisioner = hostProvisionerProvider.getHostProvisioner();
        this.zone = zone;
        this.configServerDB = configServerDB;
        this.flagSource = flagSource;
        this.secretStore = secretStore;
        this.zkCacheExecutor = Executors.newFixedThreadPool(1, ThreadFactoryFactory.getThreadFactory(TenantRepository.class.getName()));
    }

    @Override
    public ConfigserverConfig getConfigserverConfig() { return configserverConfig; }
    @Override
    public TenantListener getTenantListener() { return rpcServer; }
    @Override
    public ReloadListener getReloadListener() { return rpcServer; }
    @Override
    public ConfigDefinitionRepo getStaticConfigDefinitionRepo() { return staticConfigDefinitionRepo; }
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

    @Override
    public ConfigServerDB getConfigServerDB() { return configServerDB; }

    @Override
    public FlagSource getFlagSource() { return flagSource; }

    @Override
    public ExecutorService getZkCacheExecutor() {
        return zkCacheExecutor;
    }

    @Override
    public SecretStore getSecretStore() {
        return secretStore;
    }

}
