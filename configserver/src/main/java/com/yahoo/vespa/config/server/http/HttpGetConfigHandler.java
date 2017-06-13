// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.config.provision.ApplicationId;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * HTTP handler for a v2 getConfig operation
 *
 * @author lulf
 * @since 5.1
 */
public class HttpGetConfigHandler extends HttpHandler {
    private final RequestHandler requestHandler;

    public HttpGetConfigHandler(Executor executor, RequestHandler requestHandler, AccessLog accessLog) {
        super(executor, accessLog);
        this.requestHandler = requestHandler;
    }

    @Inject
    public HttpGetConfigHandler(Executor executor, Tenants tenants, AccessLog accesslog) {
        this(executor, tenants.defaultTenant().getRequestHandler(), accesslog);
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
