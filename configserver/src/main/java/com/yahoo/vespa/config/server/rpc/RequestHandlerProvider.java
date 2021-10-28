// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.util.Optional;

/**
 * A provider of {@link RequestHandler} instances. A very simplified interface of {@link TenantRepository}.
 *
 * @author bjorncs
 */
public interface RequestHandlerProvider {

    Optional<RequestHandler> getRequestHandler(TenantName tenantName);

}
