// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.routing;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.routing.GlobalRouting;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.yolean.Exceptions;

import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;

/**
 * This implements the /routing/v1 API, which provides operator with global routing control at both zone- and
 * deployment-level.
 *
 * @author mpolden
 */
public class RoutingApiHandler extends AuditLoggingRequestHandler {

    private final Controller controller;

    public RoutingApiHandler(Context ctx, Controller controller) {
        super(ctx, controller.auditLogger());
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
    }

    @Override
    public HttpResponse auditAndHandle(HttpRequest request) {
        try {
            var path = new Path(request.getUri());
            switch (request.getMethod()) {
                case GET: return get(path);
                case POST: return post(path);
                case DELETE: return delete(path);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse delete(Path path) {
        if (path.matches("/routing/v1/inactive/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}")) return setDeploymentStatus(path, true);
        if (path.matches("/routing/v1/inactive/environment/{environment}/region/{region}")) return setZoneStatus(path, true);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse post(Path path) {
        if (path.matches("/routing/v1/inactive/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}")) return setDeploymentStatus(path, false);
        if (path.matches("/routing/v1/inactive/environment/{environment}/region/{region}")) return setZoneStatus(path, false);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse get(Path path) {
        if (path.matches("/routing/v1/status/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}")) return deploymentStatus(path);
        if (path.matches("/routing/v1/status/environment/{environment}/region/{region}")) return zoneStatus(path);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse setZoneStatus(Path path, boolean in) {
        var zone = zoneFrom(path);
        if (controller.zoneRegistry().zones().directlyRouted().ids().contains(zone)) {
            var status = in ? GlobalRouting.Status.in : GlobalRouting.Status.out;
            controller.applications().routingPolicies().setGlobalRoutingStatus(zone, status);
        } else {
            controller.serviceRegistry().configServer().setGlobalRotationStatus(zone, in);
        }
        return new MessageResponse("Set global routing status for deployments in " + zone + " to " +
                                   (in ? "IN" : "OUT"));
    }

    private HttpResponse zoneStatus(Path path) {
        var zone = zoneFrom(path);
        var slime = new Slime();
        var root = slime.setObject();
        if (controller.zoneRegistry().zones().directlyRouted().ids().contains(zone)) {
            var zonePolicy = controller.applications().routingPolicies().get(zone);
            zoneStatusToSlime(root, zonePolicy.zone(), zonePolicy.globalRouting(), RoutingType.policy);
        } else {
            // Rotation status per zone only exposes in/out status, no agent or time of change.
            var in = controller.serviceRegistry().configServer().getGlobalRotationStatus(zone);
            var globalRouting = new GlobalRouting(in ? GlobalRouting.Status.in : GlobalRouting.Status.out,
                                                  GlobalRouting.Agent.operator, Instant.EPOCH);
            zoneStatusToSlime(root, zone, globalRouting, RoutingType.rotation);
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse setDeploymentStatus(Path path, boolean in) {
        var deployment = deploymentFrom(path);
        var instance = controller.applications().requireInstance(deployment.applicationId());
        var status = in ? GlobalRouting.Status.in : GlobalRouting.Status.out;
        var agent = GlobalRouting.Agent.operator; // Always operator as this is an operator API

        // Set rotation status, if any assigned
        if (rotationCanRouteTo(deployment.zoneId(), instance)) {
            var endpointStatus = new EndpointStatus(in ? EndpointStatus.Status.in : EndpointStatus.Status.out, "",
                                                    agent.name(),
                                                    controller.clock().instant().getEpochSecond());
            controller.applications().setGlobalRotationStatus(deployment, endpointStatus);
        }

        // Set policy status
        controller.applications().routingPolicies().setGlobalRoutingStatus(deployment, status, agent);
        return new MessageResponse("Set global routing status for " + deployment + " to " + (in ? "IN" : "OUT"));
    }

    private HttpResponse deploymentStatus(Path path) {
        var deployment = deploymentFrom(path);
        var instance = controller.applications().requireInstance(deployment.applicationId());
        var slime = new Slime();
        var deploymentsObject = slime.setObject().setArray("deployments");

        // Include status from rotation
        if (rotationCanRouteTo(deployment.zoneId(), instance)) {
            var rotationStatus = controller.applications().globalRotationStatus(deployment);
            // Status is equal across all global endpoints, as the status is per deployment, not per endpoint.
            var endpointStatus = rotationStatus.values().stream().findFirst();
            if (endpointStatus.isPresent()) {
                var changedAt = Instant.ofEpochSecond(endpointStatus.get().getEpoch());
                GlobalRouting.Agent agent;
                try {
                    agent = GlobalRouting.Agent.valueOf(endpointStatus.get().getAgent());
                } catch (IllegalArgumentException e) {
                    agent = GlobalRouting.Agent.unknown;
                }
                var status = endpointStatus.get().getStatus() == EndpointStatus.Status.in
                        ? GlobalRouting.Status.in
                        : GlobalRouting.Status.out;
                deploymentStatusToSlime(deploymentsObject.addObject(), deployment,
                                        new GlobalRouting(status, agent, changedAt),
                                        RoutingType.rotation);
            }
        }

        // Include status from routing policies
        var routingPolicies = controller.applications().routingPolicies().get(deployment);
        for (var policy : routingPolicies.values()) {
            deploymentStatusToSlime(deploymentsObject.addObject(), policy);
        }

        return new SlimeJsonResponse(slime);
    }

    /** Returns whether instance has an assigned rotation and a deployment in given zone */
    private static boolean rotationCanRouteTo(ZoneId zone, Instance instance) {
        return !instance.rotations().isEmpty() && instance.deployments().containsKey(zone);
    }

    private static void zoneStatusToSlime(Cursor object, ZoneId zone, GlobalRouting globalRouting, RoutingType routingType) {
        object.setString("routingType", routingType.name());
        object.setString("environment", zone.environment().value());
        object.setString("region", zone.region().value());
        object.setString("status", asString(globalRouting.status()));
        object.setString("agent", asString(globalRouting.agent()));
        object.setLong("changedAt", globalRouting.changedAt().toEpochMilli());
    }

    private static void deploymentStatusToSlime(Cursor object, DeploymentId deployment, GlobalRouting globalRouting, RoutingType routingType) {
        object.setString("routingType", routingType.name());
        object.setString("instance", deployment.applicationId().serializedForm());
        object.setString("environment", deployment.zoneId().environment().value());
        object.setString("region", deployment.zoneId().region().value());
        object.setString("status", asString(globalRouting.status()));
        object.setString("agent", asString(globalRouting.agent()));
        object.setLong("changedAt", globalRouting.changedAt().toEpochMilli());
    }

    private static void deploymentStatusToSlime(Cursor object, RoutingPolicy policy) {
        deploymentStatusToSlime(object, new DeploymentId(policy.id().owner(), policy.id().zone()),
                                policy.status().globalRouting(), RoutingType.policy);
    }

    private DeploymentId deploymentFrom(Path path) {
        return new DeploymentId(ApplicationId.from(path.get("tenant"), path.get("application"), path.get("instance")),
                                zoneFrom(path));
    }

    private ZoneId zoneFrom(Path path) {
        var zone = ZoneId.from(path.get("environment"), path.get("region"));
        if (!controller.zoneRegistry().hasZone(zone)) {
            throw new IllegalArgumentException("No such zone: " + zone);
        }
        return zone;
    }

    private static String asString(GlobalRouting.Status status) {
        switch (status) {
            case in: return "in";
            case out: return "out";
            default: return "unknown";
        }
    }

    private static String asString(GlobalRouting.Agent agent) {
        switch (agent) {
            case operator: return "operator";
            case system: return "system";
            case tenant: return "tenant";
            default: return "unknown";
        }
    }

    private enum RoutingType {
        /** Global routing is configured by use of an {@link com.yahoo.vespa.hosted.controller.application.AssignedRotation} */
        rotation,

        /** Global routing is configured by a {@link com.yahoo.vespa.hosted.controller.routing.RoutingPolicy} */
        policy,
    }

}
