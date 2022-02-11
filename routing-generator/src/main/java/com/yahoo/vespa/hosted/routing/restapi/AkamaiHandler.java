// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.restapi;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.routing.RoutingTable;
import com.yahoo.vespa.hosted.routing.status.HealthStatus;
import com.yahoo.vespa.hosted.routing.status.RoutingStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * This handler implements the /akamai health check.
 *
 * The global routing service polls /akamai to determine if a deployment should receive requests via its global
 * endpoint.
 *
 * @author oyving
 * @author mpolden
 * @author Torbjorn Smorgrav
 * @author Wacek Kusnierczyk
 */
public class AkamaiHandler extends ThreadedHttpRequestHandler {

    public static final String
            ROTATION_UNKNOWN_MESSAGE = "Rotation not found",
            ROTATION_UNAVAILABLE_MESSAGE = "Rotation set unavailable",
            ROTATION_UNHEALTHY_MESSAGE = "Rotation unhealthy",
            ROTATION_INACTIVE_MESSAGE = "Rotation not available",
            ROTATION_OK_MESSAGE = "Rotation OK";

    private final RoutingStatus routingStatus;
    private final HealthStatus healthStatus;
    private final Supplier<Optional<RoutingTable>> tableSupplier;

    @Inject
    public AkamaiHandler(Context parentCtx,
                         RoutingGenerator routingGenerator,
                         RoutingStatus routingStatus,
                         HealthStatus healthStatus) {
        this(parentCtx, routingGenerator::routingTable, routingStatus, healthStatus);
    }

    AkamaiHandler(Context parentCtx,
                  Supplier<Optional<RoutingTable>> tableSupplier,
                  RoutingStatus routingStatus,
                  HealthStatus healthStatus) {
        super(parentCtx);
        this.routingStatus = Objects.requireNonNull(routingStatus);
        this.healthStatus = Objects.requireNonNull(healthStatus);
        this.tableSupplier = Objects.requireNonNull(tableSupplier);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/akamai/v1/status")) {
            return status(request);
        }
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse status(HttpRequest request) {
        String hostHeader = request.getHeader("host");
        String hostname = withoutPort(hostHeader);
        Optional<RoutingTable.Target> target = tableSupplier.get().flatMap(table -> table.targetOf(hostname, RoutingMethod.sharedLayer4));

        if (target.isEmpty())
            return response(404, hostHeader, "", ROTATION_UNKNOWN_MESSAGE);

        if (!target.get().active())
            return response(404, hostHeader, "", ROTATION_INACTIVE_MESSAGE);

        String upstreamName = target.get().id();

        if (!routingStatus.isActive(upstreamName))
            return response(404, hostHeader, upstreamName, ROTATION_UNAVAILABLE_MESSAGE);

        if (!healthStatus.servers().isHealthy(upstreamName))
            return response(502, hostHeader, upstreamName, ROTATION_UNHEALTHY_MESSAGE);

        return response(200, hostHeader, upstreamName, ROTATION_OK_MESSAGE);
    }

    private static HttpResponse response(int status, String hostname, String name, String message) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("hostname", hostname);
        root.setString("upstream", name);
        root.setString("message", message);
        return new SlimeJsonResponse(status, slime);
    }

    private static String withoutPort(String hostHeader) {
        return hostHeader.replaceFirst("(:[\\d]+)?$", "");
    }

}
