// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.vespa.config.server.RequestHandler;

/**
 * Represents something that can provide a request handler for a tenant.
 *
 * @author Ulf Lilleengen
 */
public interface TenantHandlerProvider {

    RequestHandler getRequestHandler();

}
