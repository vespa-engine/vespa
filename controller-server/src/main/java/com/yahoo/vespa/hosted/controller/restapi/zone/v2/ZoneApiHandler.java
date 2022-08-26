// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.zone.v2;

import ai.vespa.http.HttpURL;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.config.provision.zone.ZoneList;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.proxy.ConfigServerRestExecutor;
import com.yahoo.vespa.hosted.controller.proxy.ProxyRequest;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.yolean.Exceptions;

/**
 * REST API for proxying requests to config servers in a given zone (version 2).
 *
 * This API does something completely different from /zone/v1, but such is the world.
 *
 * @author mpolden
 */
@SuppressWarnings("unused")
public class ZoneApiHandler extends AuditLoggingRequestHandler {

    private final ZoneRegistry zoneRegistry;
    private final ConfigServerRestExecutor proxy;

    public ZoneApiHandler(ThreadedHttpRequestHandler.Context parentCtx, ServiceRegistry serviceRegistry,
                          ConfigServerRestExecutor proxy, Controller controller) {
        super(parentCtx, controller.auditLogger());
        this.zoneRegistry = serviceRegistry.zoneRegistry();
        this.proxy = proxy;
    }

    @Override
    public HttpResponse auditAndHandle(HttpRequest request) {
        try {
            return switch (request.getMethod()) {
                case GET -> get(request);
                case POST, PUT, DELETE, PATCH -> proxy(request);
                default -> ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
            };
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/zone/v2")) {
            return root(request);
        }
        return proxy(request);
    }

    private HttpResponse proxy(HttpRequest request) {
        Path path = new Path(request.getUri());
        if ( ! path.matches("/zone/v2/{environment}/{region}/{*}")) {
            return notFound(path);
        }
        ZoneId zoneId = ZoneId.from(path.get("environment"), path.get("region"));
        if ( ! zoneRegistry.hasZone(zoneId)) {
            throw new IllegalArgumentException("No such zone: " + zoneId.value());
        }
        return proxy.handle(proxyRequest(zoneId, path.getRest(), request));
    }

    private HttpResponse root(HttpRequest request) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor uris = root.setArray("uris");
        ZoneList zoneList = zoneRegistry.zones().reachable();
        zoneList.zones().forEach(zone -> uris.addString(request.getUri()
                                                               .resolve("/zone/v2/")
                                                               .resolve(zone.getEnvironment().value() + "/")
                                                               .resolve(zone.getRegionName().value())
                                                               .toString()));
        Cursor zones = root.setArray("zones");
        zoneList.zones().forEach(zone -> {
            Cursor object = zones.addObject();
            object.setString("environment", zone.getEnvironment().value());
            object.setString("region", zone.getRegionName().value());
        });
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse notFound(Path path) {
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private ProxyRequest proxyRequest(ZoneId zoneId, HttpURL.Path path, HttpRequest request) {
        return ProxyRequest.tryOne(zoneRegistry.getConfigServerVipUri(zoneId), path, request);
    }

}
