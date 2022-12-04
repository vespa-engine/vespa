// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.RoleDefinition;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointList;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;
import com.yahoo.vespa.hosted.controller.routing.context.DeploymentRoutingContext;
import com.yahoo.vespa.hosted.controller.routing.context.RoutingContext;
import com.yahoo.yolean.Exceptions;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This implements the /routing/v1 API, which provides operators and tenants routing control at both zone- (operator
 * only) and deployment-level.
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
            return switch (request.getMethod()) {
                case GET -> get(path, request);
                case POST -> post(path, request);
                case DELETE -> delete(path, request);
                default -> ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            };
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse delete(Path path, HttpRequest request) {
        if (path.matches("/routing/v1/inactive/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}")) return setDeploymentStatus(path, true, request);
        if (path.matches("/routing/v1/inactive/environment/{environment}/region/{region}")) return setZoneStatus(path, true);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse post(Path path, HttpRequest request) {
        if (path.matches("/routing/v1/inactive/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}")) return setDeploymentStatus(path, false, request);
        if (path.matches("/routing/v1/inactive/environment/{environment}/region/{region}")) return setZoneStatus(path, false);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse get(Path path, HttpRequest request) {
        if (path.matches("/routing/v1/")) return status(request.getUri());
        if (path.matches("/routing/v1/status/tenant/{tenant}")) return tenant(path, request);
        if (path.matches("/routing/v1/status/tenant/{tenant}/application/{application}")) return application(path, request);
        if (path.matches("/routing/v1/status/tenant/{tenant}/application/{application}/instance/{instance}")) return instance(path, request);
        if (path.matches("/routing/v1/status/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}")) return deployment(path);
        if (path.matches("/routing/v1/status/tenant/{tenant}/application/{application}/instance/{instance}/endpoint")) return endpoints(path);
        if (path.matches("/routing/v1/status/environment")) return environment(request);
        if (path.matches("/routing/v1/status/environment/{environment}/region/{region}")) return zone(path);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse endpoints(Path path) {
        ApplicationId instanceId = instanceFrom(path);
        List<Endpoint> endpoints = controller.routing().readDeclaredEndpointsOf(instanceId)
                                             .sortedBy(Comparator.comparing(Endpoint::dnsName))
                                             .asList();

        List<DeploymentId> deployments = endpoints.stream()
                                                  .flatMap(e -> e.deployments().stream())
                                                  .distinct()
                                                  .toList();

        Map<DeploymentId, RoutingStatus> deploymentsStatus = deployments.stream()
                                                                        .collect(Collectors.toMap(
                                                                                deploymentId -> deploymentId,
                                                                                deploymentId -> controller.routing().of(deploymentId).routingStatus())
                                                                        );

        var slime = new Slime();
        var root = slime.setObject();
        var endpointsRoot = root.setArray("endpoints");
        endpoints.forEach(endpoint -> {
            var endpointRoot = endpointsRoot.addObject();
            endpointToSlime(endpointRoot, endpoint);
            var zonesRoot = endpointRoot.setArray("zones");
            endpoint.deployments().stream().sorted(Comparator.comparing(d -> d.zoneId().value()))
                    .forEach(deployment -> {
                        RoutingStatus status = deploymentsStatus.get(deployment);
                        deploymentStatusToSlime(zonesRoot.addObject(), deployment, status, endpoint.routingMethod());
                    });
        });

        return new SlimeJsonResponse(slime);
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
        ZoneId zone = zoneFrom(path);
        RoutingContext context = controller.routing().of(zone);
        RoutingStatus.Value newStatus = in ? RoutingStatus.Value.in : RoutingStatus.Value.out;
        context.setRoutingStatus(newStatus, RoutingStatus.Agent.operator);
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
        RoutingContext context = controller.routing().of(zone);
        zoneStatusToSlime(zoneObject, zone, context.routingStatus(), context.routingMethod());
    }

    private HttpResponse setDeploymentStatus(Path path, boolean in, HttpRequest request) {
        var deployment = deploymentFrom(path);
        var instance = controller.applications().requireInstance(deployment.applicationId());
        var status = in ? RoutingStatus.Value.in : RoutingStatus.Value.out;
        var agent = isOperator(request) ? RoutingStatus.Agent.operator : RoutingStatus.Agent.tenant;
        requireDeployment(deployment, instance);
        controller.routing().of(deployment).setRoutingStatus(status, agent);
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
            EndpointList declaredEndpoints = controller.routing().declaredEndpointsOf(application);
            for (var instance : instances) {
                var zones = zoneId == null
                        ? instance.deployments().keySet().stream().sorted(Comparator.comparing(ZoneId::value)).toList()
                        : List.of(zoneId);
                for (var zone : zones) {
                    DeploymentId deploymentId = requireDeployment(new DeploymentId(instance.id(), zone), instance);
                    DeploymentRoutingContext context = controller.routing().of(deploymentId);
                    if (declaredEndpoints.targets(deploymentId).isEmpty()) continue; // No declared endpoints point to this deployment
                    deploymentStatusToSlime(deploymentsArray.addObject(),
                                            deploymentId,
                                            context.routingStatus(),
                                            context.routingMethod());
                }
            }
        }

    }

    private static void zoneStatusToSlime(Cursor object, ZoneId zone, RoutingStatus routingStatus, RoutingMethod method) {
        object.setString("routingMethod", asString(method));
        object.setString("environment", zone.environment().value());
        object.setString("region", zone.region().value());
        object.setString("status", asString(routingStatus.value()));
        object.setString("agent", asString(routingStatus.agent()));
        object.setLong("changedAt", routingStatus.changedAt().toEpochMilli());
    }

    private static void deploymentStatusToSlime(Cursor object, DeploymentId deployment, RoutingStatus routingStatus, RoutingMethod method) {
        object.setString("routingMethod", asString(method));
        object.setString("instance", deployment.applicationId().serializedForm());
        object.setString("environment", deployment.zoneId().environment().value());
        object.setString("region", deployment.zoneId().region().value());
        object.setString("status", asString(routingStatus.value()));
        object.setString("agent", asString(routingStatus.agent()));
        object.setLong("changedAt", routingStatus.changedAt().toEpochMilli());
    }

    private static void endpointToSlime(Cursor object, Endpoint endpoint) {
        object.setString("name", endpoint.name());
        object.setString("dnsName", endpoint.dnsName());
        object.setString("routingMethod", endpoint.routingMethod().name());
        object.setString("cluster", endpoint.cluster().value());
        object.setString("scope", endpoint.scope().name());
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

    private static DeploymentId requireDeployment(DeploymentId deployment, Instance instance) {
        if (!instance.deployments().containsKey(deployment.zoneId())) {
            throw new IllegalArgumentException("No such deployment: " + deployment);
        }
        return deployment;
    }

    private static boolean isOperator(HttpRequest request) {
        SecurityContext securityContext = Optional.ofNullable(request.getJDiscRequest().context().get(SecurityContext.ATTRIBUTE_NAME))
                                                  .filter(SecurityContext.class::isInstance)
                                                  .map(SecurityContext.class::cast)
                                                  .orElseThrow(() -> new IllegalArgumentException("Attribute '" + SecurityContext.ATTRIBUTE_NAME + "' was not set on request"));
        return securityContext.roles().stream()
                              .map(Role::definition)
                              .anyMatch(definition -> definition == RoleDefinition.hostedOperator);
    }

    private static boolean isRecursive(HttpRequest request) {
        return "true".equals(request.getProperty("recursive"));
    }

    private static String asString(RoutingStatus.Value value) {
        return switch (value) {
            case in -> "in";
            case out -> "out";
        };
    }

    private static String asString(RoutingStatus.Agent agent) {
        return switch (agent) {
            case operator -> "operator";
            case system -> "system";
            case tenant -> "tenant";
            case unknown -> "unknown";
        };
    }

    private static String asString(RoutingMethod method) {
        return switch (method) {
            case exclusive -> "exclusive";
            case sharedLayer4 -> "sharedLayer4";
        };
    }

}
