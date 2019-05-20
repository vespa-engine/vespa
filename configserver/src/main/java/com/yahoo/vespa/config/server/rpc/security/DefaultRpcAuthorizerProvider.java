// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.security.NodeIdentifier;
import com.yahoo.security.tls.TransportSecurityUtils;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

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
                                        TenantRepository tenantRepository) {
        this.rpcAuthorizer =
                TransportSecurityUtils.isTransportSecurityEnabled() && config.multitenant()
                        ? new MultiTenantRpcAuthorizer(nodeIdentifier, hostRegistries, tenantRepository)
                        : new NoopRpcAuthorizer();
    }

    @Override
    public RpcAuthorizer get() {
        return rpcAuthorizer;
    }
}
