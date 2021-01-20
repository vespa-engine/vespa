// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.tenant.TenantListener;

import java.time.Clock;

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
    private final Zone zone;
    private final ConfigServerDB configServerDB;

    @SuppressWarnings("WeakerAccess")
    @Inject
    public InjectedGlobalComponentRegistry(ModelFactoryRegistry modelFactoryRegistry,
                                           RpcServer rpcServer,
                                           ConfigserverConfig configserverConfig,
                                           ConfigDefinitionRepo staticConfigDefinitionRepo,
                                           Zone zone,
                                           ConfigServerDB configServerDB) {
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.rpcServer = rpcServer;
        this.configserverConfig = configserverConfig;
        this.staticConfigDefinitionRepo = staticConfigDefinitionRepo;
        this.zone = zone;
        this.configServerDB = configServerDB;
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
    public Zone getZone() {
        return zone;
    }

    @Override
    public Clock getClock() {return  Clock.systemUTC();}

    @Override
    public ConfigServerDB getConfigServerDB() { return configServerDB; }

}
