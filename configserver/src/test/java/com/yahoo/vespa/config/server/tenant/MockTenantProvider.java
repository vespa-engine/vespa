// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.rpc.MockRequestHandler;

/**
 * @author Ulf Lilleengen
 */
public class MockTenantProvider implements TenantHandlerProvider {

    private final MockRequestHandler requestHandler;

    public MockTenantProvider(ApplicationId applicationId) {
        this.requestHandler = new MockRequestHandler(applicationId);
    }

    @Override
    public RequestHandler getRequestHandler() { return requestHandler; }

}
