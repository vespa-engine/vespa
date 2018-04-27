// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.util.Optional;

/**
 * HTTP handler for a v2 getConfig operation
 *
 * @author Ulf Lilleengen
 */
// TODO: Make this API discoverable
public class HttpGetConfigHandler extends HttpHandler {

    private final RequestHandler requestHandler;

    public HttpGetConfigHandler(HttpHandler.Context ctx, RequestHandler requestHandler) {
        super(ctx);
        this.requestHandler = requestHandler;
    }

    @SuppressWarnings("unused") // injected
    @Inject
    public HttpGetConfigHandler(HttpHandler.Context ctx, TenantRepository tenantRepository) {
        this(ctx, tenantRepository.defaultTenant().getRequestHandler());
    }

    @Override
    public HttpResponse handleGET(HttpRequest req) {
        HttpConfigRequest request = HttpConfigRequest.createFromRequestV1(req);
        HttpConfigRequest.validateRequestKey(request.getConfigKey(), requestHandler, ApplicationId.defaultId());
        return HttpConfigResponse.createFromConfig(resolveConfig(request));
    }

    private ConfigResponse resolveConfig(HttpConfigRequest request) {
        log.log(LogLevel.DEBUG, "nocache=" + request.noCache());
        ConfigResponse config = requestHandler.resolveConfig(ApplicationId.defaultId(), request, Optional.empty());
        if (config == null) HttpConfigRequest.throwModelNotReady();
        return config;
    }
}
