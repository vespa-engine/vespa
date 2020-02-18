// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.routing;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.ResourceResponse;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.routing.GlobalRouting;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.yolean.Exceptions;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
                case GET: return get(path, request);
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

    private HttpResponse get(Path path, HttpRequest request) {
        if (path.matches("/routing/v1/")) return status(request.getUri());
        if (path.matches("/routing/v1/status/tenant/{tenant}")) return tenant(path, request);
        if (path.matches("/routing/v1/status/tenant/{tenant}/application/{application}")) return application(path, request);
        if (path.matches("/routing/v1/status/tenant/{tenant}/application/{application}/instance/{instance}")) return instance(path, request);
        if (path.matches("/routing/v1/status/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}")) return deployment(path);
        if (path.matches("/routing/v1/status/environment")) return environment(request);
        if (path.matches("/routing/v1/status/environment/{environment}/region/{region}")) return zone(path);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse environment(HttpRequest request) {
        var zones = controller.zoneRegistry().zones().all().ids();
        if (isRecursive(request)) {
            var slime = new Slime();
            var root = slime.setObject();
            var zonesArray = root.setArray("zones");
            for (var zone : zones) {
                toSlime(zone, zonesArray.addObject());
            }
            return new SlimeJsonResponse(slime);
        }
        var resources = controller.zoneRegistry().zones().all().ids().stream()
                                  .map(zone -> zone.environment().value() +
                                               "/region/" + zone.region().value())
                                  .sorted()
                                  .collect(Collectors.toList());
        return new ResourceResponse(request.getUri(), resources);
    }

    private HttpResponse status(URI requestUrl) {
        return new ResourceResponse(requestUrl, "status/tenant", "status/environment");
    }

    private HttpResponse tenant(Path path, HttpRequest request) {
        var tenantName = tenantFrom(path);
        if (isRecursive(request)) {
            var slime = new Slime();
            var root = slime.setObject();
            toSlime(controller.applications().asList(tenantName), null, null, root);
            return new SlimeJsonResponse(slime);
        }
        var resources = controller.applications().asList(tenantName).stream()
                                  .map(Application::id)
                                  .map(TenantAndApplicationId::application)
                                  .map(ApplicationName::value)
                                  .map(application -> "application/" + application)
                                  .sorted()
                                  .collect(Collectors.toList());
        return new ResourceResponse(request.getUri(), resources);
    }

    private HttpResponse application(Path path, HttpRequest request) {
        var tenantAndApplicationId = tenantAndApplicationIdFrom(path);
        if (isRecursive(request)) {
            var slime = new Slime();
            var root = slime.setObject();
            toSlime(List.of(controller.applications().requireApplication(tenantAndApplicationId)), null,
                    null, root);
            return new SlimeJsonResponse(slime);
        }
        var resources = controller.applications().requireApplication(tenantAndApplicationId).instances().keySet().stream()
                                  .map(InstanceName::value)
                                  .map(instance -> "instance/" + instance)
                                  .sorted()
                                  .collect(Collectors.toList());
        return new ResourceResponse(request.getUri(), resources);
    }

    private HttpResponse instance(Path path, HttpRequest request) {
        var instanceId = instanceFrom(path);
        if (isRecursive(request)) {
            var slime = new Slime();
            var root = slime.setObject();
            toSlime(List.of(controller.applications().requireApplication(TenantAndApplicationId.from(instanceId))),
                    instanceId, null, root);
            return new SlimeJsonResponse(slime);
        }
        var resources = controller.applications().requireInstance(instanceId).deployments().keySet().stream()
                                  .map(zone -> "environment/" + zone.environment().value() +
                                               "/region/" + zone.region().value())
                                  .sorted()
                                  .collect(Collectors.toList());
        return new ResourceResponse(request.getUri(), resources);
    }

    private HttpResponse setZoneStatus(Path path, boolean in) {
        var zone = zoneFrom(path);
        if (controller.zoneRegistry().zones().directlyRouted().ids().contains(zone)) {
            var status = in ? GlobalRouting.Status.in : GlobalRouting.Status.out;
            controller.routingController().policies().setGlobalRoutingStatus(zone, status);
        } else {
            controller.serviceRegistry().configServer().setGlobalRotationStatus(zone, in);
        }
        return new MessageResponse("Set global routing status for deployments in " + zone + " to " +
                                   (in ? "IN" : "OUT"));
    }

    private HttpResponse zone(Path path) {
        var zone = zoneFrom(path);
        var slime = new Slime();
        var root = slime.setObject();
        toSlime(zone, root);
        return new SlimeJsonResponse(slime);
    }

    private void toSlime(ZoneId zone, Cursor zoneObject) {
        if (controller.zoneRegistry().zones().directlyRouted().ids().contains(zone)) {
            var zonePolicy = controller.routingController().policies().get(zone);
            zoneStatusToSlime(zoneObject, zonePolicy.zone(), zonePolicy.globalRouting(), RoutingMethod.exclusive);
        } else {
            // Rotation status per zone only exposes in/out status, no agent or time of change.
            var in = controller.serviceRegistry().configServer().getGlobalRotationStatus(zone);
            var globalRouting = new GlobalRouting(in ? GlobalRouting.Status.in : GlobalRouting.Status.out,
                                                  GlobalRouting.Agent.operator, Instant.EPOCH);
            zoneStatusToSlime(zoneObject, zone, globalRouting, RoutingMethod.shared);
        }
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
            controller.routingController().setGlobalRotationStatus(deployment, endpointStatus);
        }

        // Set policy status
        controller.routingController().policies().setGlobalRoutingStatus(deployment, status, agent);
        return new MessageResponse("Set global routing status for " + deployment + " to " + (in ? "IN" : "OUT"));
    }

    private HttpResponse deployment(Path path) {
        var slime = new Slime();
        var root = slime.setObject();
        var deploymentId = deploymentFrom(path);
        var application = controller.applications().requireApplication(TenantAndApplicationId.from(deploymentId.applicationId()));
        toSlime(List.of(application), deploymentId.applicationId(), deploymentId.zoneId(), root);
        return new SlimeJsonResponse(slime);
    }

    private void toSlime(List<Application> applications, ApplicationId instanceId, ZoneId zoneId, Cursor root) {
        var deploymentsArray = root.setArray("deployments");
        for (var application : applications) {
            var instances = instanceId == null
                    ? application.instances().values()
                    : List.of(application.instances().get(instanceId.instance()));
            for (var instance : instances) {
                var zones = zoneId == null
                        ? instance.deployments().keySet().stream().sorted(Comparator.comparing(ZoneId::value))
                                  .collect(Collectors.toList())
                        : List.of(zoneId);
                for (var zone : zones) {
                    var deploymentId = new DeploymentId(instance.id(), zone);
                    // Include status from rotation
                    if (rotationCanRouteTo(zone, instance)) {
                        var rotationStatus = controller.routingController().globalRotationStatus(deploymentId);
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
                            deploymentStatusToSlime(deploymentsArray.addObject(), deploymentId,
                                                    new GlobalRouting(status, agent, changedAt),
                                                    RoutingMethod.shared);
                        }
                    }

                    // Include status from routing policies
                    var routingPolicies = controller.routingController().policies().get(deploymentId);
                    for (var policy : routingPolicies.values()) {
                        deploymentStatusToSlime(deploymentsArray.addObject(), policy);
                    }
                }
            }
        }

    }

    /** Returns whether instance has an assigned rotation and a deployment in given zone */
    private static boolean rotationCanRouteTo(ZoneId zone, Instance instance) {
        return !instance.rotations().isEmpty() && instance.deployments().containsKey(zone);
    }

    private static void zoneStatusToSlime(Cursor object, ZoneId zone, GlobalRouting globalRouting, RoutingMethod method) {
        object.setString("routingMethod", asString(method));
        object.setString("environment", zone.environment().value());
        object.setString("region", zone.region().value());
        object.setString("status", asString(globalRouting.status()));
        object.setString("agent", asString(globalRouting.agent()));
        object.setLong("changedAt", globalRouting.changedAt().toEpochMilli());
    }

    private static void deploymentStatusToSlime(Cursor object, DeploymentId deployment, GlobalRouting globalRouting, RoutingMethod method) {
        object.setString("routingMethod", asString(method));
        object.setString("instance", deployment.applicationId().serializedForm());
        object.setString("environment", deployment.zoneId().environment().value());
        object.setString("region", deployment.zoneId().region().value());
        object.setString("status", asString(globalRouting.status()));
        object.setString("agent", asString(globalRouting.agent()));
        object.setLong("changedAt", globalRouting.changedAt().toEpochMilli());
    }

    private static void deploymentStatusToSlime(Cursor object, RoutingPolicy policy) {
        deploymentStatusToSlime(object, new DeploymentId(policy.id().owner(), policy.id().zone()),
                                policy.status().globalRouting(), RoutingMethod.exclusive);
    }

    private TenantName tenantFrom(Path path) {
        return TenantName.from(path.get("tenant"));
    }

    private ApplicationName applicationFrom(Path path) {
        return ApplicationName.from(path.get("application"));
    }

    private TenantAndApplicationId tenantAndApplicationIdFrom(Path path) {
       return TenantAndApplicationId.from(tenantFrom(path), applicationFrom(path));
    }

    private ApplicationId instanceFrom(Path path) {
        return ApplicationId.from(tenantFrom(path), applicationFrom(path), InstanceName.from(path.get("instance")));
    }

    private DeploymentId deploymentFrom(Path path) {
        return new DeploymentId(instanceFrom(path), zoneFrom(path));
    }

    private ZoneId zoneFrom(Path path) {
        var zone = ZoneId.from(path.get("environment"), path.get("region"));
        if (!controller.zoneRegistry().hasZone(zone)) {
            throw new IllegalArgumentException("No such zone: " + zone);
        }
        return zone;
    }

    private static boolean isRecursive(HttpRequest request) {
        return "true".equals(request.getProperty("recursive"));
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

    private static String asString(RoutingMethod method) {
        switch (method) {
            case shared: return "shared";
            case exclusive: return "exclusive";
            default: return "unknonwn";
        }
    }

}
