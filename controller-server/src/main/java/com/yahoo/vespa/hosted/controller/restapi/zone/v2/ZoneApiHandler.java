// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.zone.v2;

import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneList;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.proxy.ConfigServerRestExecutor;
import com.yahoo.vespa.hosted.controller.proxy.ProxyException;
import com.yahoo.vespa.hosted.controller.proxy.ProxyRequest;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.Path;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.util.logging.Level;

/**
 * REST API for proxying requests to config servers in a given zone (version 2).
 *
 * This API does something completely different from /zone/v1, but such is the world.
 *
 * @author mpolden
 */
@SuppressWarnings("unused")
public class ZoneApiHandler extends LoggingRequestHandler {

    private final ZoneRegistry zoneRegistry;
    private final ConfigServerRestExecutor proxy;

    public ZoneApiHandler(LoggingRequestHandler.Context parentCtx, ZoneRegistry zoneRegistry,
                          ConfigServerRestExecutor proxy) {
        super(parentCtx);
        this.zoneRegistry = zoneRegistry;
        this.proxy = proxy;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET:
                    return get(request);
                case POST:
                case PUT:
                case DELETE:
                case PATCH:
                    return proxy(request);
                default:
                    return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "', "
                                   + Exceptions.toMessageString(e));
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/zone/v2")) {
            return root(request);
        }
        return proxy(request);
    }

    private HttpResponse proxy(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if ( ! path.matches("/zone/v2/{environment}/{region}/{*}")) {
            return notFound(path);
        }
        ZoneId zoneId = ZoneId.from(path.get("environment"), path.get("region"));
        if ( ! zoneRegistry.hasZone(zoneId)) {
            throw new IllegalArgumentException("No such zone: " + zoneId.value());
        }
        try {
            return proxy.handle(new ProxyRequest(request, "/zone/v2/"));
        } catch (ProxyException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpResponse root(HttpRequest request) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor uris = root.setArray("uris");
        ZoneList zoneList = zoneRegistry.zones().reachable();
        zoneList.ids().forEach(zoneId -> uris.addString(request.getUri()
                                                               .resolve("/zone/v2/")
                                                               .resolve(zoneId.environment().value() + "/")
                                                               .resolve(zoneId.region().value())
                                                               .toString()));
        Cursor zones = root.setArray("zones");
        zoneList.ids().forEach(zoneId -> {
            Cursor object = zones.addObject();
            object.setString("environment", zoneId.environment().value());
            object.setString("region", zoneId.region().value());
        });
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse notFound(Path path) {
        return ErrorResponse.notFoundError("Nothing at " + path);
    }
}
