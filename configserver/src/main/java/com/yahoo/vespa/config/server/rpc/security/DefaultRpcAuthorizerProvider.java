// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.security.NodeIdentifier;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.security.tls.TransportSecurityUtils;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.rpc.RequestHandlerProvider;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;

/**
 * A provider for {@link RpcAuthorizer}. The instance provided is dependent on the configuration of the configserver.
 *
 * @author bjorncs
 */
public class DefaultRpcAuthorizerProvider implements Provider<RpcAuthorizer> {

    private final RpcAuthorizer rpcAuthorizer;

    @Inject
    public DefaultRpcAuthorizerProvider(ConfigserverConfig config,
                                        NodeIdentifier nodeIdentifier,
                                        HostRegistries hostRegistries,
                                        RequestHandlerProvider handlerProvider) {
        boolean useMultiTenantAuthorizer =
                TransportSecurityUtils.isTransportSecurityEnabled() && config.multitenant() && config.hostedVespa();
        this.rpcAuthorizer =
                useMultiTenantAuthorizer
                        ? new MultiTenantRpcAuthorizer(nodeIdentifier, hostRegistries, handlerProvider, getThreadPoolSize(config))
                        : new NoopRpcAuthorizer();
    }

    private static int getThreadPoolSize(ConfigserverConfig config) {
        return config.numRpcThreads() != 0 ? config.numRpcThreads() : 8;
    }

    @Override
    public RpcAuthorizer get() {
        return rpcAuthorizer;
    }

    @Override
    public void deconstruct() {}
}
