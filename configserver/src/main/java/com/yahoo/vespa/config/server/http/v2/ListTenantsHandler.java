// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import java.util.concurrent.Executor;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.tenant.Tenants;

/**
 * Handler to list tenants in the configserver
 *
 * @author vegardh
 */
public class ListTenantsHandler extends HttpHandler {

    private final Tenants tenants;


    public ListTenantsHandler(Executor executor, AccessLog accessLog, Tenants tenants) {
        super(executor, accessLog);
        this.tenants = tenants;
    }


    @Override
    protected HttpResponse handleGET(HttpRequest request) {
        return new ListTenantsResponse(tenants.getAllTenantNames());
    }

}
