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
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.routing.GlobalRouting;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicyId;
import com.yahoo.vespa.hosted.controller.routing.ZoneRoutingPolicy;
import com.yahoo.yolean.Exceptions;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/**
 * This implements the /routing/v1 API, which provides operator with global routing control at both zone- and
 * deployment-level.
 *
 * @author mpolden
 */
// TODO(mpolden): Add support for zones/deployments using rotations.
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
        var status = in ? GlobalRouting.Status.in : GlobalRouting.Status.out;
        controller.applications().routingPolicies().setGlobalRoutingStatus(zone, status);
        return new MessageResponse("Set global routing status for deployments in " + zone + " to '" +
                                   (in ? "in" : "out") + "'");
    }

    private HttpResponse zoneStatus(Path path) {
        var zone = zoneFrom(path);
        var slime = new Slime();
        var root = slime.setObject();
        var zonePolicy = controller.applications().routingPolicies().get(zone);
        zoneStatusToSlime(root, zonePolicy);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse setDeploymentStatus(Path path, boolean in) {
        var deployment = deploymentFrom(path);
        routingPoliciesOf(deployment);
        var status = in ? GlobalRouting.Status.in : GlobalRouting.Status.out;
        var agent = GlobalRouting.Agent.operator; // Always operator as this is an operator API
        controller.applications().routingPolicies().setGlobalRoutingStatus(deployment, status, agent);
        return new MessageResponse("Set global routing status for " + deployment + " to '" + (in ? "in" : "out") + "'");
    }

    private HttpResponse deploymentStatus(Path path) {
        var deployment = deploymentFrom(path);
        var slime = new Slime();
        var deploymentsObject = slime.setObject().setArray("deployments");
        var routingPolicies = routingPoliciesOf(deployment);
        for (var policy : routingPolicies.values()) {
            deploymentStatusToSlime(deploymentsObject.addObject(), policy);
        }
        return new SlimeJsonResponse(slime);
    }

    private Map<RoutingPolicyId, RoutingPolicy> routingPoliciesOf(DeploymentId deployment) {
        var routingPolicies = controller.applications().routingPolicies().get(deployment);
        if (routingPolicies.isEmpty()) {
            throw new IllegalArgumentException("No such deployment: " + deployment);
        }
        return routingPolicies;
    }

    private static void zoneStatusToSlime(Cursor object, ZoneRoutingPolicy policy) {
        object.setString("environment", policy.zone().environment().value());
        object.setString("region", policy.zone().region().value());
        object.setString("status", asString(policy.globalRouting().status()));
        object.setString("agent", asString(policy.globalRouting().agent()));
        object.setLong("changedAt", policy.globalRouting().changedAt().toEpochMilli());
    }

    private static void deploymentStatusToSlime(Cursor object, RoutingPolicy policy) {
        object.setString("instance", policy.id().owner().serializedForm());
        object.setString("cluster", policy.id().cluster().value());
        object.setString("environment", policy.id().zone().environment().value());
        object.setString("region", policy.id().zone().region().value());
        object.setString("status", asString(policy.status().globalRouting().status()));
        object.setString("agent", asString(policy.status().globalRouting().agent()));
        object.setLong("changedAt", policy.status().globalRouting().changedAt().toEpochMilli());
    }

    private DeploymentId deploymentFrom(Path path) {
        return new DeploymentId(ApplicationId.from(path.get("tenant"), path.get("application"), path.get("instance")),
                                zoneFrom(path));
    }

    private ZoneId zoneFrom(Path path) {
        var zone = ZoneId.from(path.get("environment"), path.get("region"));
        if (!controller.zoneRegistry().zones().all().ids().contains(zone)) {
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

    private static String asString(GlobalRouting.Agent status) {
        switch (status) {
            case operator: return "operator";
            case system: return "system";
            case tenant: return "tenant";
            default: return "unknown";
        }
    }

}
