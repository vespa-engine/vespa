// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.config.server.http.HttpConfigRequest;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.http.HttpHandler;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * HTTP handler for a getConfig operation
 *
 * @author lulf
 * @since 5.1
 */
public class HttpGetConfigHandler extends HttpHandler {

    private final Tenants tenants;

    @Inject
    public HttpGetConfigHandler(Executor executor, AccessLog accesslog, Tenants tenants) {
        super(executor, accesslog);
        this.tenants = tenants;
    }
    
    @Override
    public HttpResponse handleGET(HttpRequest req) {
        HttpConfigRequest request = HttpConfigRequest.createFromRequestV2(req);       
        RequestHandler requestHandler = HttpConfigRequests.getRequestHandler(tenants, request);
        HttpConfigRequest.validateRequestKey(request.getConfigKey(), requestHandler, request.getApplicationId());
        return HttpConfigResponse.createFromConfig(resolveConfig(request, requestHandler));
    }

    private ConfigResponse resolveConfig(HttpConfigRequest request, RequestHandler requestHandler) {
        log.log(LogLevel.DEBUG, "nocache=" + request.noCache());
        ConfigResponse config = requestHandler.resolveConfig(request.getApplicationId(), request, Optional.empty());
        if (config == null) HttpConfigRequest.throwModelNotReady();
        return config;
    }
}
