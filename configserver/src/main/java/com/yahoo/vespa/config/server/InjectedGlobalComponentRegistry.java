// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.tenant.TenantListener;

/**
 * Registry containing all the "static"/"global" components in a config server in one place.
 *
 * @author Ulf Lilleengen
 */
public class InjectedGlobalComponentRegistry implements GlobalComponentRegistry {

    private final ModelFactoryRegistry modelFactoryRegistry;
    private final RpcServer rpcServer;
    private final ConfigDefinitionRepo staticConfigDefinitionRepo;

    @SuppressWarnings("WeakerAccess")
    @Inject
    public InjectedGlobalComponentRegistry(ModelFactoryRegistry modelFactoryRegistry,
                                           RpcServer rpcServer,
                                           ConfigDefinitionRepo staticConfigDefinitionRepo) {
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.rpcServer = rpcServer;
        this.staticConfigDefinitionRepo = staticConfigDefinitionRepo;
    }

    @Override
    public TenantListener getTenantListener() { return rpcServer; }
    @Override
    public ReloadListener getReloadListener() { return rpcServer; }
    @Override
    public ConfigDefinitionRepo getStaticConfigDefinitionRepo() { return staticConfigDefinitionRepo; }
    @Override
    public ModelFactoryRegistry getModelFactoryRegistry() { return modelFactoryRegistry; }

}
