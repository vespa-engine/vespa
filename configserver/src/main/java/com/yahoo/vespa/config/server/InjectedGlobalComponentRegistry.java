// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.tenant.TenantListener;

/**
 * Registry containing all the "static"/"global" components in a config server in one place.
 *
 * @author Ulf Lilleengen
 */
public class InjectedGlobalComponentRegistry implements GlobalComponentRegistry {

    private final RpcServer rpcServer;

    @SuppressWarnings("WeakerAccess")
    @Inject
    public InjectedGlobalComponentRegistry(RpcServer rpcServer) {
        this.rpcServer = rpcServer;
    }

    @Override
    public TenantListener getTenantListener() { return rpcServer; }

}
