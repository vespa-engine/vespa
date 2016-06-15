// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

/**
 * Represents something that can provide request and reload handlers of a tenant.
 *
 * @author lulf
 * @since 5.3
 */
public interface TenantHandlerProvider {

    RequestHandler getRequestHandler();
    ReloadHandler getReloadHandler();

}
