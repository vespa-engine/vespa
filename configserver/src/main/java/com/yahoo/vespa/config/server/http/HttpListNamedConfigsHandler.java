// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.google.inject.Inject;
import com.yahoo.collections.Tuple2;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.config.provision.ApplicationId;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Handler for a list configs of given name operation. Lists all configs in model for a given config name.
 *
 * @author vegardh
 * @since 5.1.11
 */
public class HttpListNamedConfigsHandler extends HttpHandler {
    private final RequestHandler requestHandler;

    public HttpListNamedConfigsHandler(Executor executor, RequestHandler requestHandler, AccessLog accessLog) {
        super(executor, accessLog);
        this.requestHandler = requestHandler;
    }

    @Inject
    public HttpListNamedConfigsHandler(Executor executor, Tenants tenants, AccessLog accessLog) {
        this(executor, tenants.defaultTenant().getRequestHandler(), accessLog);
    }
    
    @Override
    public HttpResponse handleGET(HttpRequest req) {
        boolean recursive = req.getBooleanProperty(HttpListConfigsHandler.RECURSIVE_QUERY_PROPERTY);
        ConfigKey<?> listKey = parseReqToKey(req);
        HttpConfigRequest.validateRequestKey(listKey, requestHandler, ApplicationId.defaultId());
        Set<ConfigKey<?>> configs = requestHandler.listNamedConfigs(ApplicationId.defaultId(), Optional.empty(), listKey, recursive);
        String urlBase = Utils.getUrlBase(req, "/config/v1/");
        return new HttpListConfigsHandler.ListConfigsResponse(configs, requestHandler.allConfigsProduced(ApplicationId.defaultId(), Optional.empty()), urlBase, recursive);
    }

    private ConfigKey<?> parseReqToKey(HttpRequest req) {
        BindingMatch<?> bm = Utils.getBindingMatch(req, "http://*/config/v1/*/*");
        String config = bm.group(2); // See jdisc-bindings.cfg. The port number is implicitly 1, it seems.
        Tuple2<String, String> nns = HttpConfigRequest.nameAndNamespace(config);
        String name = nns.first;
        String namespace = nns.second;        
        String idSegment = "";
        if (bm.groupCount() == 4) {
            idSegment = bm.group(3);
        }
        return new ConfigKey<>(name, idSegment, namespace);
    }
}
