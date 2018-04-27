// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import java.util.Optional;
import java.util.Set;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.http.HttpConfigRequest;
import com.yahoo.vespa.config.server.http.HttpHandler;

/**
 * Handler for a list named configs operation. Lists all configs in model for a given application and tenant, config name and optionally config id.
 *
 * @author vegardh
 * @since 5.3
 */
public class HttpListNamedConfigsHandler extends HttpHandler {

    private final TenantRepository tenantRepository;
    private final Zone zone;
    
    @Inject
    public HttpListNamedConfigsHandler(HttpHandler.Context ctx,
                                       TenantRepository tenantRepository, Zone zone)
    {
        super(ctx);
        this.tenantRepository = tenantRepository;
        this.zone = zone;
    }
    
    @Override
    public HttpResponse handleGET(HttpRequest req) {
        HttpListConfigsRequest listReq = HttpListConfigsRequest.createFromNamedListRequest(req);
        RequestHandler requestHandler = HttpConfigRequests.getRequestHandler(tenantRepository, listReq);
        ApplicationId appId = listReq.getApplicationId();
        HttpConfigRequest.validateRequestKey(listReq.getKey(), requestHandler, appId);
        Set<ConfigKey<?>> configs = requestHandler.listNamedConfigs(appId, Optional.empty(), listReq.getKey(), listReq.isRecursive());
        String urlBase = HttpListConfigsHandler.getUrlBase(req, listReq, appId, zone);
        Set<ConfigKey<?>> allConfigs = requestHandler.allConfigsProduced(appId, Optional.empty());
        return new HttpListConfigsHandler.ListConfigsResponse(configs, allConfigs, urlBase, listReq.isRecursive());
    }
}
