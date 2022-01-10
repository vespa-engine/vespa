// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.rpc.security.DefaultRpcAuthorizerProvider;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.util.Optional;

/**
 * This is a hack to avoid a cyclic dependency involving {@link RpcServer}, {@link DefaultRpcAuthorizerProvider} and {@link TenantRepository}.
 *
 * @author bjorncs
 */
public class RpcRequestHandlerProvider implements RequestHandlerProvider {

    private volatile RpcServer instance;

    @Inject
    public RpcRequestHandlerProvider() {}

    @Override
    public Optional<RequestHandler> getRequestHandler(TenantName tenantName) {
        if (instance == null) {
            throw new IllegalStateException("RpcServer instance has not been registered");
        }
        return instance.getRequestHandler(tenantName);
    }

    void setInstance(RpcServer instance) {
        this.instance = instance;
    }
}
