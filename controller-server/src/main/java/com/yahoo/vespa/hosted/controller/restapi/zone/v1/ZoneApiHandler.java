// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.zone.v1;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.yolean.Exceptions;

import java.util.Comparator;
import java.util.List;

/**
 * Read-only REST API that provides information about zones in hosted Vespa (version 1)
 *
 * @author mpolden
 */
@SuppressWarnings("unused")
public class ZoneApiHandler extends ThreadedHttpRequestHandler {

    private final ZoneRegistry zoneRegistry;

    public ZoneApiHandler(ThreadedHttpRequestHandler.Context parentCtx, ServiceRegistry serviceRegistry) {
        super(parentCtx);
        this.zoneRegistry = serviceRegistry.zoneRegistry();
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            return switch (request.getMethod()) {
                case GET -> get(request);
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
        if (path.matches("/zone/v1")) {
            return root(request);
        }
        if (path.matches("/zone/v1/environment/{environment}")) {
            return environment(request, Environment.from(path.get("environment")));
        }
        if (path.matches("/zone/v1/environment/{environment}/default")) {
            return defaultRegion(request, Environment.from(path.get("environment")));
        }
        return notFound(path);
    }

    private HttpResponse root(HttpRequest request) {
        List<Environment> environments = zoneRegistry.zones().publiclyVisible().zones().stream()
                                                     .map(ZoneApi::getEnvironment)
                                                     .distinct()
                                                     .sorted(Comparator.comparing(Environment::value))
                                                     .toList();
        Slime slime = new Slime();
        Cursor root = slime.setArray();
        environments.forEach(environment -> {
            Cursor object = root.addObject();
            object.setString("name", environment.value());
            object.setString("url", request.getUri()
                                           .resolve("/zone/v1/environment/")
                                           .resolve(environment.value())
                                           .toString());
        });
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse environment(HttpRequest request, Environment environment) {
        Slime slime = new Slime();
        Cursor root = slime.setArray();
        zoneRegistry.zones().publiclyVisible().all().in(environment).zones().forEach(zone -> {
            Cursor object = root.addObject();
            object.setString("name", zone.getRegionName().value());
            object.setString("url", request.getUri()
                                           .resolve("/zone/v2/")
                                           .resolve(environment.value() + "/")
                                           .resolve(zone.getRegionName().value())
                                           .toString());
        });
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse defaultRegion(HttpRequest request, Environment environment) {
        RegionName region = zoneRegistry.getDefaultRegion(environment)
                .orElseThrow(() -> new IllegalArgumentException("No default region for environment: " + environment));
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("name", region.value());
        root.setString("url", request.getUri()
                                     .resolve("/zone/v2/")
                                     .resolve(environment.value() + "/")
                                     .resolve(region.value())
                                     .toString());
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse notFound(Path path) {
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

}
