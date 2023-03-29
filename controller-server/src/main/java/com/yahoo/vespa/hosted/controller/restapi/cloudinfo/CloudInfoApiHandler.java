// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.cloudinfo;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.ResourceResponse;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.yolean.Exceptions;

/**
 * Read-only REST API that provides information about cloud resources (e.g. zones and flavors).
 *
 * @author freva
 */
@SuppressWarnings("unused")
public class CloudInfoApiHandler extends ThreadedHttpRequestHandler {

    private final ZoneRegistry zoneRegistry;

    public CloudInfoApiHandler(Context parentCtx, ServiceRegistry serviceRegistry) {
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
        if (path.matches("/cloudinfo/v1")) return new ResourceResponse(request, "zones");
        if (path.matches("/cloudinfo/v1/zones")) return zones(request);
        return notFound(path);
    }

    private HttpResponse zones(HttpRequest request) {
        Slime slime = new Slime();
        Cursor zones = slime.setObject().setArray("zones");
        zoneRegistry.zones().publiclyVisible().all().zones().forEach(zone -> {
            Cursor object = zones.addObject();
            object.setString("environment", zone.getEnvironment().value());
            object.setString("region", zone.getRegionName().value());
            object.setString("cloud", zone.getCloudName().value());
            object.setString("availabilityZone", zone.getCloudNativeAvailabilityZone());
        });
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse notFound(Path path) {
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

}
