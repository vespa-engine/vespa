// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.vespa.config.server.MockReloadHandler;
import com.yahoo.vespa.config.server.rpc.MockRequestHandler;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.vespa.config.server.RequestHandler;

/**
 * @author Ulf Lilleengen
 */
public class MockTenantProvider implements TenantHandlerProvider {

    final MockRequestHandler requestHandler;
    final MockReloadHandler reloadHandler;

    public MockTenantProvider() {
        this(false);
    }

    public MockTenantProvider(boolean pretendToHaveLoadedAnyApplication) {
        this.requestHandler = new MockRequestHandler(pretendToHaveLoadedAnyApplication);
        this.reloadHandler = new MockReloadHandler();
    }

    @Override
    public RequestHandler getRequestHandler() { return requestHandler; }

    @Override
    public ReloadHandler getReloadHandler() {
        return reloadHandler;
    }

}
