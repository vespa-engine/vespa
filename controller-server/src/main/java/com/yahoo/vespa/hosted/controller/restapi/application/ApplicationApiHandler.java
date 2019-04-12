// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.restapi.Path;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.client.zms.ZmsClientException;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.AlreadyExistsException;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.NotExistsException;
import com.yahoo.vespa.hosted.controller.api.ActivateResult;
import com.yahoo.vespa.hosted.controller.api.application.v4.ApplicationResource;
import com.yahoo.vespa.hosted.controller.api.application.v4.EnvironmentResource;
import com.yahoo.vespa.hosted.controller.api.application.v4.TenantResource;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.RefeedAction;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.RestartAction;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ServiceInfo;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Logs;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterCost;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentCost;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.RotationStatus;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.MessageResponse;
import com.yahoo.vespa.hosted.controller.restapi.ResourceResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.StringResponse;
import com.yahoo.vespa.hosted.controller.security.AccessControlRequests;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.yolean.Exceptions;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotAuthorizedException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.StringJoiner;
import java.util.logging.Level;

import static java.util.stream.Collectors.joining;

/**
 * This implements the application/v4 API which is used to deploy and manage applications
 * on hosted Vespa.
 *
 * @author bratseth
 * @author mpolden
 */
@SuppressWarnings("unused") // created by injection
public class ApplicationApiHandler extends LoggingRequestHandler {

    private static final String OPTIONAL_PREFIX = "/api";

    private final Controller controller;
    private final AccessControlRequests accessControlRequests;

    @Inject
    public ApplicationApiHandler(LoggingRequestHandler.Context parentCtx,
                                 Controller controller,
                                 AccessControlRequests accessControlRequests) {
        super(parentCtx);
        this.controller = controller;
        this.accessControlRequests = accessControlRequests;
    }

    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(20); // deploys may take a long time;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            Path path = new Path(request.getUri(), OPTIONAL_PREFIX);
            switch (request.getMethod()) {
                case GET: return handleGET(path, request);
                case PUT: return handlePUT(path, request);
                case POST: return handlePOST(path, request);
                case PATCH: return handlePATCH(path, request);
                case DELETE: return handleDELETE(path, request);
                case OPTIONS: return handleOPTIONS();
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        }
        catch (ForbiddenException e) {
            return ErrorResponse.forbidden(Exceptions.toMessageString(e));
        }
        catch (NotAuthorizedException e) {
            return ErrorResponse.unauthorized(Exceptions.toMessageString(e));
        }
        catch (NotExistsException e) {
            return ErrorResponse.notFoundError(Exceptions.toMessageString(e));
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (ConfigServerException e) {
            return ErrorResponse.from(e);
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handleGET(Path path, HttpRequest request) {
        if (path.matches("/application/v4/")) return root(request);
        if (path.matches("/application/v4/user")) return authenticatedUser(request);
        if (path.matches("/application/v4/tenant")) return tenants(request);
        if (path.matches("/application/v4/tenant/{tenant}")) return tenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application")) return applications(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}")) return application(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying")) return deploying(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying/pin")) return deploying(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/nodes")) return nodes(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/logs")) return logs(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request.propertyMap());
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job")) return JobControllerApiHandlerHelper.jobTypeResponse(controller, appIdFromPath(path), request.getUri());
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}")) return JobControllerApiHandlerHelper.runResponse(controller.jobController().runs(appIdFromPath(path), jobTypeFromPath(path)), request.getUri());
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}/run/{number}")) return JobControllerApiHandlerHelper.runDetailsResponse(controller.jobController(), runIdFromPath(path), request.getProperty("after"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}")) return deployment(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/suspended")) return suspended(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/service")) return services(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/service/{service}/{*}")) return service(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), path.get("service"), path.getRest(), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation")) return rotationStatus(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation/override")) return getGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handlePUT(Path path, HttpRequest request) {
        if (path.matches("/application/v4/user")) return createUser(request);
        if (path.matches("/application/v4/tenant/{tenant}")) return updateTenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation/override"))
            return setGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), false, request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handlePOST(Path path, HttpRequest request) {
        if (path.matches("/application/v4/tenant/{tenant}")) return createTenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}")) return createApplication(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/promote")) return promoteApplication(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying/platform")) return deployPlatform(path.get("tenant"), path.get("application"), false, request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying/pin")) return deployPlatform(path.get("tenant"), path.get("application"), true, request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying/application")) return deployApplication(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/jobreport")) return notifyJobCompletion(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/submit")) return submit(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}")) return trigger(appIdFromPath(path), jobTypeFromPath(path), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}/pause")) return pause(appIdFromPath(path), jobTypeFromPath(path));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}")) return deploy(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/deploy")) return deploy(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request); // legacy synonym of the above
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/restart")) return restart(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/promote")) return promoteApplicationDeployment(path.get("tenant"), path.get("application"), path.get("environment"), path.get("region"), path.get("instance"), request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handlePATCH(Path path, HttpRequest request) {
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}"))
            return patchApplication(path.get("tenant"), path.get("application"), request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handleDELETE(Path path, HttpRequest request) {
        if (path.matches("/application/v4/tenant/{tenant}")) return deleteTenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}")) return deleteApplication(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying")) return cancelDeploy(path.get("tenant"), path.get("application"), "all");
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying/{choice}")) return cancelDeploy(path.get("tenant"), path.get("application"), path.get("choice"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/submit")) return JobControllerApiHandlerHelper.unregisterResponse(controller.jobController(), path.get("tenant"), path.get("application"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}")) return JobControllerApiHandlerHelper.abortJobResponse(controller.jobController(), appIdFromPath(path), jobTypeFromPath(path));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}")) return deactivate(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation/override"))
            return setGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), true, request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handleOPTIONS() {
        // We implement this to avoid redirect loops on OPTIONS requests from browsers, but do not really bother
        // spelling out the methods supported at each path, which we should
        EmptyJsonResponse response = new EmptyJsonResponse();
        response.headers().put("Allow", "GET,PUT,POST,PATCH,DELETE,OPTIONS");
        return response;
    }

    private HttpResponse recursiveRoot(HttpRequest request) {
        Slime slime = new Slime();
        Cursor tenantArray = slime.setArray();
        for (Tenant tenant : controller.tenants().asList())
            toSlime(tenantArray.addObject(), tenant, request);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse root(HttpRequest request) {
        return recurseOverTenants(request)
                ? recursiveRoot(request)
                : new ResourceResponse(request, "user", "tenant");
    }

    private HttpResponse authenticatedUser(HttpRequest request) {
        Principal user = requireUserPrincipal(request);
        if (user == null)
            throw new NotAuthorizedException("You must be authenticated.");

        String userName = user instanceof AthenzPrincipal ? ((AthenzPrincipal) user).getIdentity().getName() : user.getName();
        TenantName tenantName = TenantName.from(UserTenant.normalizeUser(userName));
        List<Tenant> tenants = controller.tenants().asList(new Credentials(user));

        Slime slime = new Slime();
        Cursor response = slime.setObject();
        response.setString("user", userName);
        Cursor tenantsArray = response.setArray("tenants");
        for (Tenant tenant : tenants)
            tenantInTenantsListToSlime(tenant, request.getUri(), tenantsArray.addObject());
        response.setBool("tenantExists", tenants.stream().anyMatch(tenant -> tenant.name().equals(tenantName)));
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse tenants(HttpRequest request) {
        Slime slime = new Slime();
        Cursor response = slime.setArray();
        for (Tenant tenant : controller.tenants().asList())
            tenantInTenantsListToSlime(tenant, request.getUri(), response.addObject());
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse tenant(String tenantName, HttpRequest request) {
        return controller.tenants().get(TenantName.from(tenantName))
                         .map(tenant -> tenant(tenant, request))
                         .orElseGet(() -> ErrorResponse.notFoundError("Tenant '" + tenantName + "' does not exist"));
    }

    private HttpResponse tenant(Tenant tenant, HttpRequest request) {
        Slime slime = new Slime();
        toSlime(slime.setObject(), tenant, request);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse applications(String tenantName, HttpRequest request) {
        TenantName tenant = TenantName.from(tenantName);
        Slime slime = new Slime();
        Cursor array = slime.setArray();
        for (Application application : controller.applications().asList(tenant))
            toSlime(application, array.addObject(), request);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse application(String tenantName, String applicationName, HttpRequest request) {
        Slime slime = new Slime();
        toSlime(slime.setObject(), getApplication(tenantName, applicationName), request);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse patchApplication(String tenantName, String applicationName, HttpRequest request) {
        Inspector requestObject = toSlime(request.getData()).get();
        StringJoiner messageBuilder = new StringJoiner("\n").setEmptyValue("No applicable changes.");
        controller.applications().lockOrThrow(ApplicationId.from(tenantName, applicationName, "default"), application -> {
            Inspector majorVersionField = requestObject.field("majorVersion");
            if (majorVersionField.valid()) {
                Integer majorVersion = majorVersionField.asLong() == 0 ? null : (int) majorVersionField.asLong();
                application = application.withMajorVersion(majorVersion);
                messageBuilder.add("Set major version to " + (majorVersion == null ? "empty" : majorVersion));
            }

            Inspector pemDeployKeyField = requestObject.field("pemDeployKey");
            if (pemDeployKeyField.valid()) {
                String pemDeployKey = pemDeployKeyField.type() == Type.NIX ? null : pemDeployKeyField.asString();
                application = application.withPemDeployKey(pemDeployKey);
                messageBuilder.add("Set pem deploy key to " + (pemDeployKey == null ? "empty" : pemDeployKey));
            }

            controller.applications().store(application);
        });
        return new MessageResponse(messageBuilder.toString());
    }

    private Application getApplication(String tenantName, String applicationName) {
        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, "default");
        return controller.applications().get(applicationId)
                          .orElseThrow(() -> new NotExistsException(applicationId + " not found"));
    }

    private HttpResponse nodes(String tenantName, String applicationName, String instanceName, String environment, String region) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = ZoneId.from(environment, region);
        List<Node> nodes = controller.configServer().nodeRepository().list(zone, id);

        Slime slime = new Slime();
        Cursor nodesArray = slime.setObject().setArray("nodes");
        for (Node node : nodes) {
            Cursor nodeObject = nodesArray.addObject();
            nodeObject.setString("hostname", node.hostname().value());
            nodeObject.setString("state", valueOf(node.state()));
            nodeObject.setString("orchestration", valueOf(node.serviceState()));
            nodeObject.setString("version", node.currentVersion().toString());
            nodeObject.setString("flavor", node.canonicalFlavor());
            nodeObject.setString("clusterId", node.clusterId());
            nodeObject.setString("clusterType", valueOf(node.clusterType()));
        }
        return new SlimeJsonResponse(slime);
    }

    private static String valueOf(Node.State state) {
        switch (state) {
            case failed: return "failed";
            case parked: return "parked";
            case dirty: return "dirty";
            case ready: return "ready";
            case active: return "active";
            case inactive: return "inactive";
            case reserved: return "reserved";
            case provisioned: return "provisioned";
            default: throw new IllegalArgumentException("Unexpected node state '" + state + "'.");
        }
    }

    private static String valueOf(Node.ServiceState state) {
        switch (state) {
            case expectedUp: return "expectedUp";
            case allowedDown: return "allowedDown";
            case unorchestrated: return "unorchestrated";
            default: throw new IllegalArgumentException("Unexpected node state '" + state + "'.");
        }
    }

    private static String valueOf(Node.ClusterType type) {
        switch (type) {
            case admin: return "admin";
            case content: return "content";
            case container: return "container";
            default: throw new IllegalArgumentException("Unexpected node cluster type '" + type + "'.");
        }
    }

    private HttpResponse logs(String tenantName, String applicationName, String instanceName, String environment, String region, Map<String, String> queryParameters) {
        ApplicationId application = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = ZoneId.from(environment, region);
        DeploymentId deployment = new DeploymentId(application, zone);

        if (queryParameters.containsKey("streaming")) {
            InputStream logStream = controller.configServer().getLogStream(deployment, queryParameters);
            return new HttpResponse(200) {
                @Override
                public void render(OutputStream outputStream) throws IOException {
                    logStream.transferTo(outputStream);
                }
            };
        }

        Optional<Logs> response = controller.configServer().getLogs(deployment, queryParameters);
        Slime slime = new Slime();
        Cursor object = slime.setObject();
        if (response.isPresent()) {
            response.get().logs().entrySet().stream().forEach(entry -> object.setString(entry.getKey(), entry.getValue()));
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse trigger(ApplicationId id, JobType type, HttpRequest request) {
            String triggered = controller.applications().deploymentTrigger()
                                         .forceTrigger(id, type, request.getJDiscRequest().getUserPrincipal().getName())
                                         .stream().map(JobType::jobName).collect(joining(", "));
            return new MessageResponse(triggered.isEmpty() ? "Job " + type.jobName() + " for " + id + " not triggered"
                                                           : "Triggered " + triggered + " for " + id);
    }

    private HttpResponse pause(ApplicationId id, JobType type) {
        Instant until = controller.clock().instant().plus(DeploymentTrigger.maxPause);
        controller.applications().deploymentTrigger().pauseJob(id, type, until);
        return new MessageResponse(type.jobName() + " for " + id + " paused for " + DeploymentTrigger.maxPause);
    }

    private void toSlime(Cursor object, Application application, HttpRequest request) {
        object.setString("tenant", application.id().tenant().value());
        object.setString("application", application.id().application().value());
        object.setString("instance", application.id().instance().value());
        object.setString("deployments", withPath("/application/v4" +
                                                 "/tenant/" + application.id().tenant().value() +
                                                 "/application/" + application.id().application().value() +
                                                 "/instance/" + application.id().instance().value() + "/job/",
                                                 request.getUri()).toString());

        application.deploymentJobs().statusOf(JobType.component)
                   .flatMap(JobStatus::lastSuccess)
                   .map(run -> run.application().source())
                   .ifPresent(source -> sourceRevisionToSlime(source, object.setObject("source")));

        application.deploymentJobs().projectId()
                   .ifPresent(id -> object.setLong("projectId", id));

        // Currently deploying change
        if ( ! application.change().isEmpty()) {
            toSlime(object.setObject("deploying"), application.change());
        }

        // Outstanding change
        if ( ! application.outstandingChange().isEmpty()) {
            toSlime(object.setObject("outstandingChange"), application.outstandingChange());
        }

        // Jobs sorted according to deployment spec
        List<JobStatus> jobStatus = controller.applications().deploymentTrigger()
                .steps(application.deploymentSpec())
                .sortedJobs(application.deploymentJobs().jobStatus().values());

        object.setBool("deployedInternally", application.deploymentJobs().deployedInternally());
        Cursor deploymentsArray = object.setArray("deploymentJobs");
        for (JobStatus job : jobStatus) {
            Cursor jobObject = deploymentsArray.addObject();
            jobObject.setString("type", job.type().jobName());
            jobObject.setBool("success", job.isSuccess());

            job.lastTriggered().ifPresent(jobRun -> toSlime(jobRun, jobObject.setObject("lastTriggered")));
            job.lastCompleted().ifPresent(jobRun -> toSlime(jobRun, jobObject.setObject("lastCompleted")));
            job.firstFailing().ifPresent(jobRun -> toSlime(jobRun, jobObject.setObject("firstFailing")));
            job.lastSuccess().ifPresent(jobRun -> toSlime(jobRun, jobObject.setObject("lastSuccess")));
        }

        // Change blockers
        Cursor changeBlockers = object.setArray("changeBlockers");
        application.deploymentSpec().changeBlocker().forEach(changeBlocker -> {
            Cursor changeBlockerObject = changeBlockers.addObject();
            changeBlockerObject.setBool("versions", changeBlocker.blocksVersions());
            changeBlockerObject.setBool("revisions", changeBlocker.blocksRevisions());
            changeBlockerObject.setString("timeZone", changeBlocker.window().zone().getId());
            Cursor days = changeBlockerObject.setArray("days");
            changeBlocker.window().days().stream().map(DayOfWeek::getValue).forEach(days::addLong);
            Cursor hours = changeBlockerObject.setArray("hours");
            changeBlocker.window().hours().forEach(hours::addLong);
        });

        // Compile version. The version that should be used when building an application
        object.setString("compileVersion", controller.applications().oldestInstalledPlatform(application.id()).toFullString());

        application.majorVersion().ifPresent(majorVersion -> object.setLong("majorVersion", majorVersion));

        // Rotation
        Cursor globalRotationsArray = object.setArray("globalRotations");
        application.endpointsIn(controller.system())
                   .scope(Endpoint.Scope.global)
                   .legacy(false) // Hide legacy names
                   .asList().stream()
                   .map(Endpoint::url)
                   .map(URI::toString)
                   .forEach(globalRotationsArray::addString);

        application.rotation().ifPresent(rotation -> object.setString("rotationId", rotation.asString()));

        // Per-cluster rotations
        Set<RoutingPolicy> routingPolicies = controller.applications().routingPolicies(application.id());
        for (RoutingPolicy policy : routingPolicies) {
            policy.endpointsIn(controller.system()).asList().stream()
                  .map(Endpoint::url)
                  .map(URI::toString)
                  .forEach(globalRotationsArray::addString);
        }

        // Deployments sorted according to deployment spec
        List<Deployment> deployments = controller.applications().deploymentTrigger()
                .steps(application.deploymentSpec())
                .sortedDeployments(application.deployments().values());
        Cursor instancesArray = object.setArray("instances");
        for (Deployment deployment : deployments) {
            Cursor deploymentObject = instancesArray.addObject();

            if (application.rotation().isPresent() && deployment.zone().environment() == Environment.prod) {
                toSlime(application.rotationStatus(deployment), deploymentObject);
            }

            if (recurseOverDeployments(request)) // List full deployment information when recursive.
                toSlime(deploymentObject, new DeploymentId(application.id(), deployment.zone()), deployment, request);
            else {
                deploymentObject.setString("environment", deployment.zone().environment().value());
                deploymentObject.setString("region", deployment.zone().region().value());
                deploymentObject.setString("instance", application.id().instance().value()); // pointless
                deploymentObject.setString("url", withPath(request.getUri().getPath() +
                                                           "/environment/" + deployment.zone().environment().value() +
                                                           "/region/" + deployment.zone().region().value() +
                                                           "/instance/" + application.id().instance().value(),
                                                           request.getUri()).toString());
            }
        }

        application.pemDeployKey().ifPresent(key -> object.setString("pemDeploymentKey", key));

        // Metrics
        Cursor metricsObject = object.setObject("metrics");
        metricsObject.setDouble("queryServiceQuality", application.metrics().queryServiceQuality());
        metricsObject.setDouble("writeServiceQuality", application.metrics().writeServiceQuality());

        // Activity
        Cursor activity = object.setObject("activity");
        application.activity().lastQueried().ifPresent(instant -> activity.setLong("lastQueried", instant.toEpochMilli()));
        application.activity().lastWritten().ifPresent(instant -> activity.setLong("lastWritten", instant.toEpochMilli()));
        application.activity().lastQueriesPerSecond().ifPresent(value -> activity.setDouble("lastQueriesPerSecond", value));
        application.activity().lastWritesPerSecond().ifPresent(value -> activity.setDouble("lastWritesPerSecond", value));

        application.ownershipIssueId().ifPresent(issueId -> object.setString("ownershipIssueId", issueId.value()));
        application.owner().ifPresent(owner -> object.setString("owner", owner.username()));
        application.deploymentJobs().issueId().ifPresent(issueId -> object.setString("deploymentIssueId", issueId.value()));
    }

    private HttpResponse deployment(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        Application application = controller.applications().get(id)
                .orElseThrow(() -> new NotExistsException(id + " not found"));

        DeploymentId deploymentId = new DeploymentId(application.id(),
                                                     ZoneId.from(environment, region));

        Deployment deployment = application.deployments().get(deploymentId.zoneId());
        if (deployment == null)
            throw new NotExistsException(application + " is not deployed in " + deploymentId.zoneId());

        Slime slime = new Slime();
        toSlime(slime.setObject(), deploymentId, deployment, request);
        return new SlimeJsonResponse(slime);
    }

    private void toSlime(Cursor object, Change change) {
        change.platform().ifPresent(version -> object.setString("version", version.toString()));
        change.application()
              .filter(version -> !version.isUnknown())
              .ifPresent(version -> toSlime(version, object.setObject("revision")));
    }

    private void toSlime(Cursor response, DeploymentId deploymentId, Deployment deployment, HttpRequest request) {

        response.setString("tenant", deploymentId.applicationId().tenant().value());
        response.setString("application", deploymentId.applicationId().application().value());
        response.setString("instance", deploymentId.applicationId().instance().value()); // pointless
        response.setString("environment", deploymentId.zoneId().environment().value());
        response.setString("region", deploymentId.zoneId().region().value());

        // serviceUrls contains zone/cluster-specific endpoints for this deployment. The name of these endpoints may
        // contain  the cluster name (if non-default) and since the controller has no knowledge of clusters, we have to
        // ask the routing layer here
        Cursor serviceUrlArray = response.setArray("serviceUrls");
        controller.applications().getDeploymentEndpoints(deploymentId)
                  .ifPresent(endpoints -> endpoints.forEach(endpoint -> serviceUrlArray.addString(endpoint.toString())));

        response.setString("nodes", withPath("/zone/v2/" + deploymentId.zoneId().environment() + "/" + deploymentId.zoneId().region() + "/nodes/v2/node/?&recursive=true&application=" + deploymentId.applicationId().tenant() + "." + deploymentId.applicationId().application() + "." + deploymentId.applicationId().instance(), request.getUri()).toString());
        response.setString("yamasUrl", monitoringSystemUri(deploymentId).toString());
        response.setString("version", deployment.version().toFullString());
        response.setString("revision", deployment.applicationVersion().id());
        response.setLong("deployTimeEpochMs", deployment.at().toEpochMilli());
        controller.zoneRegistry().getDeploymentTimeToLive(deploymentId.zoneId())
                .ifPresent(deploymentTimeToLive -> response.setLong("expiryTimeEpochMs", deployment.at().plus(deploymentTimeToLive).toEpochMilli()));

        controller.applications().require(deploymentId.applicationId()).deploymentJobs().projectId()
                  .ifPresent(i -> response.setString("screwdriverId", String.valueOf(i)));
        sourceRevisionToSlime(deployment.applicationVersion().source(), response);

        Cursor activity = response.setObject("activity");
        deployment.activity().lastQueried().ifPresent(instant -> activity.setLong("lastQueried",
                                                                                  instant.toEpochMilli()));
        deployment.activity().lastWritten().ifPresent(instant -> activity.setLong("lastWritten",
                                                                                  instant.toEpochMilli()));
        deployment.activity().lastQueriesPerSecond().ifPresent(value -> activity.setDouble("lastQueriesPerSecond", value));
        deployment.activity().lastWritesPerSecond().ifPresent(value -> activity.setDouble("lastWritesPerSecond", value));

        // Cost
        DeploymentCost appCost = deployment.calculateCost();
        Cursor costObject = response.setObject("cost");
        toSlime(appCost, costObject);

        // Metrics
        DeploymentMetrics metrics = deployment.metrics();
        Cursor metricsObject = response.setObject("metrics");
        metricsObject.setDouble("queriesPerSecond", metrics.queriesPerSecond());
        metricsObject.setDouble("writesPerSecond", metrics.writesPerSecond());
        metricsObject.setDouble("documentCount", metrics.documentCount());
        metricsObject.setDouble("queryLatencyMillis", metrics.queryLatencyMillis());
        metricsObject.setDouble("writeLatencyMillis", metrics.writeLatencyMillis());
        metrics.instant().ifPresent(instant -> metricsObject.setLong("lastUpdated", instant.toEpochMilli()));
    }

    private void toSlime(ApplicationVersion applicationVersion, Cursor object) {
        if (!applicationVersion.isUnknown()) {
            object.setString("hash", applicationVersion.id());
            sourceRevisionToSlime(applicationVersion.source(), object.setObject("source"));
        }
    }

    private void sourceRevisionToSlime(Optional<SourceRevision> revision, Cursor object) {
        if ( ! revision.isPresent()) return;
        object.setString("gitRepository", revision.get().repository());
        object.setString("gitBranch", revision.get().branch());
        object.setString("gitCommit", revision.get().commit());
    }

    private void toSlime(RotationStatus status, Cursor object) {
        Cursor bcpStatus = object.setObject("bcpStatus");
        bcpStatus.setString("rotationStatus", status.name().toUpperCase());
    }

    private URI monitoringSystemUri(DeploymentId deploymentId) {
        return controller.zoneRegistry().getMonitoringSystemUri(deploymentId);
    }

    private HttpResponse setGlobalRotationOverride(String tenantName, String applicationName, String instanceName, String environment, String region, boolean inService, HttpRequest request) {
        Application application = controller.applications().require(ApplicationId.from(tenantName, applicationName, instanceName));
        ZoneId zone = ZoneId.from(environment, region);
        Deployment deployment = application.deployments().get(zone);
        if (deployment == null) {
            throw new NotExistsException(application + " has no deployment in " + zone);
        }

        Inspector requestData = toSlime(request.getData()).get();
        String reason = mandatory("reason", requestData).asString();
        String agent = requireUserPrincipal(request).getName();
        long timestamp = controller.clock().instant().getEpochSecond();
        EndpointStatus.Status status = inService ? EndpointStatus.Status.in : EndpointStatus.Status.out;
        EndpointStatus endpointStatus = new EndpointStatus(status, reason, agent, timestamp);
        controller.applications().setGlobalRotationStatus(new DeploymentId(application.id(), deployment.zone()),
                                                          endpointStatus);
        return new MessageResponse(String.format("Successfully set %s in %s.%s %s service",
                                                 application.id().toShortString(),
                                                 deployment.zone().environment().value(),
                                                 deployment.zone().region().value(),
                                                 inService ? "in" : "out of"));
    }

    private HttpResponse getGlobalRotationOverride(String tenantName, String applicationName, String instanceName, String environment, String region) {

        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                                     ZoneId.from(environment, region));

        Slime slime = new Slime();
        Cursor array = slime.setObject().setArray("globalrotationoverride");
        Map<RoutingEndpoint, EndpointStatus> status = controller.applications().globalRotationStatus(deploymentId);
        for (RoutingEndpoint endpoint : status.keySet()) {
            EndpointStatus currentStatus = status.get(endpoint);
            array.addString(endpoint.upstreamName());
            Cursor statusObject = array.addObject();
            statusObject.setString("status", currentStatus.getStatus().name());
            statusObject.setString("reason", currentStatus.getReason() == null ? "" : currentStatus.getReason());
            statusObject.setString("agent", currentStatus.getAgent() == null ? "" : currentStatus.getAgent());
            statusObject.setLong("timestamp", currentStatus.getEpoch());
        }

        return new SlimeJsonResponse(slime);
    }

    private HttpResponse rotationStatus(String tenantName, String applicationName, String instanceName, String environment, String region) {
        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, instanceName);
        Application application = controller.applications().require(applicationId);
        ZoneId zone = ZoneId.from(environment, region);
        if (!application.rotation().isPresent()) {
            throw new NotExistsException("global rotation does not exist for " + application);
        }
        Deployment deployment = application.deployments().get(zone);
        if (deployment == null) {
            throw new NotExistsException(application + " has no deployment in " + zone);
        }

        Slime slime = new Slime();
        Cursor response = slime.setObject();
        toSlime(application.rotationStatus(deployment), response);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse deploying(String tenant, String application, HttpRequest request) {
        Application app = controller.applications().require(ApplicationId.from(tenant, application, "default"));
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        if (!app.change().isEmpty()) {
            app.change().platform().ifPresent(version -> root.setString("platform", version.toString()));
            app.change().application().ifPresent(applicationVersion -> root.setString("application", applicationVersion.id()));
            root.setBool("pinned", app.change().isPinned());
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse suspended(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                                     ZoneId.from(environment, region));
        boolean suspended = controller.applications().isSuspended(deploymentId);
        Slime slime = new Slime();
        Cursor response = slime.setObject();
        response.setBool("suspended", suspended);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse services(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        ApplicationView applicationView = controller.getApplicationView(tenantName, applicationName, instanceName, environment, region);
        ServiceApiResponse response = new ServiceApiResponse(ZoneId.from(environment, region),
                                                             new ApplicationId.Builder().tenant(tenantName).applicationName(applicationName).instanceName(instanceName).build(),
                                                             controller.zoneRegistry().getConfigServerApiUris(ZoneId.from(environment, region)),
                                                             request.getUri());
        response.setResponse(applicationView);
        return response;
    }

    private HttpResponse service(String tenantName, String applicationName, String instanceName, String environment, String region, String serviceName, String restPath, HttpRequest request) {
        Map<?,?> result = controller.getServiceApiResponse(tenantName, applicationName, instanceName, environment, region, serviceName, restPath);
        ServiceApiResponse response = new ServiceApiResponse(ZoneId.from(environment, region),
                                                             new ApplicationId.Builder().tenant(tenantName).applicationName(applicationName).instanceName(instanceName).build(),
                                                             controller.zoneRegistry().getConfigServerApiUris(ZoneId.from(environment, region)),
                                                             request.getUri());
        response.setResponse(result, serviceName, restPath);
        return response;
    }

    private HttpResponse createUser(HttpRequest request) {
        String user = Optional.of(requireUserPrincipal(request))
                              .filter(AthenzPrincipal.class::isInstance)
                              .map(AthenzPrincipal.class::cast)
                              .map(AthenzPrincipal::getIdentity)
                              .filter(AthenzUser.class::isInstance)
                              .map(AthenzIdentity::getName)
                              .map(UserTenant::normalizeUser)
                              .orElseThrow(() -> new ForbiddenException("Not authenticated or not a user."));

        UserTenant tenant = UserTenant.create(user);
        try {
            controller.tenants().createUser(tenant);
            return new MessageResponse("Created user '" + user + "'");
        } catch (AlreadyExistsException e) {
            // Ok
            return new MessageResponse("User '" + user + "' already exists");
        }
    }

    private HttpResponse updateTenant(String tenantName, HttpRequest request) {
        getTenantOrThrow(tenantName);
        TenantName tenant = TenantName.from(tenantName);
        Inspector requestObject = toSlime(request.getData()).get();
        controller.tenants().update(accessControlRequests.specification(tenant, requestObject),
                                    accessControlRequests.credentials(tenant, requestObject, request.getJDiscRequest()));
        return tenant(controller.tenants().require(TenantName.from(tenantName)), request);
    }

    private HttpResponse createTenant(String tenantName, HttpRequest request) {
        TenantName tenant = TenantName.from(tenantName);
        Inspector requestObject = toSlime(request.getData()).get();
        controller.tenants().create(accessControlRequests.specification(tenant, requestObject),
                                    accessControlRequests.credentials(tenant, requestObject, request.getJDiscRequest()));
        return tenant(controller.tenants().require(TenantName.from(tenantName)), request);
    }

    private HttpResponse createApplication(String tenantName, String applicationName, HttpRequest request) {
        Inspector requestObject = toSlime(request.getData()).get();
        ApplicationId id = ApplicationId.from(tenantName, applicationName, "default");
        try {
            Optional<Credentials> credentials = controller.tenants().require(id.tenant()).type() == Tenant.Type.user
                    ? Optional.empty()
                    : Optional.of(accessControlRequests.credentials(id.tenant(), requestObject, request.getJDiscRequest()));
            Application application = controller.applications().createApplication(id, credentials);

            Slime slime = new Slime();
            toSlime(application, slime.setObject(), request);
            return new SlimeJsonResponse(slime);
        }
        catch (ZmsClientException e) { // TODO: Push conversion down
            if (e.getErrorCode() == com.yahoo.jdisc.Response.Status.FORBIDDEN)
                throw new ForbiddenException("Not authorized to create application", e);
            else
                throw e;
        }
    }

    /** Trigger deployment of the given Vespa version if a valid one is given, e.g., "7.8.9". */
    private HttpResponse deployPlatform(String tenantName, String applicationName, boolean pin, HttpRequest request) {
        request = controller.auditLogger().log(request);
        String versionString = readToString(request.getData());
        ApplicationId id = ApplicationId.from(tenantName, applicationName, "default");
        StringBuilder response = new StringBuilder();
        controller.applications().lockOrThrow(id, application -> {
            Version version = Version.fromString(versionString);
            if (version.equals(Version.emptyVersion))
                version = controller.systemVersion();
            if ( ! systemHasVersion(version))
                throw new IllegalArgumentException("Cannot trigger deployment of version '" + version + "': " +
                                                       "Version is not active in this system. " +
                                                       "Active versions: " + controller.versionStatus().versions()
                                                                                       .stream()
                                                                                       .map(VespaVersion::versionNumber)
                                                                                       .map(Version::toString)
                                                                                       .collect(joining(", ")));
            Change change = Change.of(version);
            if (pin)
                change = change.withPin();

            controller.applications().deploymentTrigger().forceChange(id, change);
            response.append("Triggered " + change + " for " + id);
        });
        return new MessageResponse(response.toString());
    }

    /** Trigger deployment to the last known application package for the given application. */
    private HttpResponse deployApplication(String tenantName, String applicationName, HttpRequest request) {
        controller.auditLogger().log(request);
        ApplicationId id = ApplicationId.from(tenantName, applicationName, "default");
        StringBuilder response = new StringBuilder();
        controller.applications().lockOrThrow(id, application -> {
            Change change = Change.of(application.get().deploymentJobs().statusOf(JobType.component).get().lastSuccess().get().application());
            controller.applications().deploymentTrigger().forceChange(id, change);
            response.append("Triggered " + change + " for " + id);
        });
        return new MessageResponse(response.toString());
    }

    /** Cancel ongoing change for given application, e.g., everything with {"cancel":"all"} */
    private HttpResponse cancelDeploy(String tenantName, String applicationName, String choice) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, "default");
        StringBuilder response = new StringBuilder();
        controller.applications().lockOrThrow(id, application -> {
            Change change = application.get().change();
            if (change.isEmpty()) {
                response.append("No deployment in progress for " + application + " at this time");
                return;
            }

            ChangesToCancel cancel = ChangesToCancel.valueOf(choice.toUpperCase());
            controller.applications().deploymentTrigger().cancelChange(id, cancel);
            response.append("Changed deployment from '" + change + "' to '" +
                            controller.applications().require(id).change() + "' for " + application);
        });

        return new MessageResponse(response.toString());
    }

    /** Schedule restart of deployment, or specific host in a deployment */
    private HttpResponse restart(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                                     ZoneId.from(environment, region));

        // TODO: Propagate all filters
        Optional<Hostname> hostname = Optional.ofNullable(request.getProperty("hostname")).map(Hostname::new);
        controller.applications().restart(deploymentId, hostname);

        // TODO: Change to return JSON
        return new StringResponse("Requested restart of " + path(TenantResource.API_PATH, tenantName,
                                                                 ApplicationResource.API_PATH, applicationName,
                                                                 EnvironmentResource.API_PATH, environment,
                                                                 "region", region,
                                                                 "instance", instanceName));
    }

    private HttpResponse deploy(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = ZoneId.from(environment, region);

        // Get deployOptions
        Map<String, byte[]> dataParts = new MultipartParser().parse(request);
        if ( ! dataParts.containsKey("deployOptions"))
            return ErrorResponse.badRequest("Missing required form part 'deployOptions'");
        Inspector deployOptions = SlimeUtils.jsonToSlime(dataParts.get("deployOptions")).get();

        /*
         * Special handling of the zone application (the only system application with an application package)
         * Setting any other deployOptions here is not supported for now (e.g. specifying version), but
         * this might be handy later to handle emergency downgrades.
         */
        boolean isZoneApplication = SystemApplication.zone.id().equals(applicationId);
        if (isZoneApplication) { // TODO jvenstad: Separate out.
            // Make it explicit that version is not yet supported here
            String versionStr = deployOptions.field("vespaVersion").asString();
            boolean versionPresent = !versionStr.isEmpty() && !versionStr.equals("null");
            if (versionPresent) {
                throw new RuntimeException("Version not supported for system applications");
            }
            // To avoid second guessing the orchestrated upgrades of system applications
            // we don't allow to deploy these during an system upgrade (i.e when new vespa is being rolled out)
            if (controller.versionStatus().isUpgrading()) {
                throw new IllegalArgumentException("Deployment of system applications during a system upgrade is not allowed");
            }
            Optional<VespaVersion> systemVersion = controller.versionStatus().systemVersion();
            if (systemVersion.isEmpty()) {
                throw new IllegalArgumentException("Deployment of system applications is not permitted until system version is determined");
            }
            ActivateResult result = controller.applications()
                    .deploySystemApplicationPackage(SystemApplication.zone, zone, systemVersion.get().versionNumber());
            return new SlimeJsonResponse(toSlime(result));
        }

        /*
         * Normal applications from here
         */

        Optional<ApplicationPackage> applicationPackage = Optional.ofNullable(dataParts.get("applicationZip"))
                                                                  .map(ApplicationPackage::new);

        Inspector sourceRevision = deployOptions.field("sourceRevision");
        Inspector buildNumber = deployOptions.field("buildNumber");
        if (sourceRevision.valid() != buildNumber.valid())
            throw new IllegalArgumentException("Source revision and build number must both be provided, or not");

        Optional<ApplicationVersion> applicationVersion = Optional.empty();
        if (sourceRevision.valid()) {
            if (applicationPackage.isPresent())
                throw new IllegalArgumentException("Application version and application package can't both be provided.");

            applicationVersion = Optional.of(ApplicationVersion.from(toSourceRevision(sourceRevision),
                                                                     buildNumber.asLong()));
            applicationPackage = Optional.of(controller.applications().getApplicationPackage(controller.applications().require(applicationId), applicationVersion.get()));
        }

        boolean deployDirectly = deployOptions.field("deployDirectly").asBool();
        Optional<Version> vespaVersion = optional("vespaVersion", deployOptions).map(Version::new);

        /*
         * Deploy direct is when we want to redeploy the current application - retrieve version
         * info from the application package before deploying
         */
        if(deployDirectly && !applicationPackage.isPresent() && !applicationVersion.isPresent() && !vespaVersion.isPresent()) {

            // Redeploy the existing deployment with the same versions.
            Optional<Deployment> deployment = controller.applications().get(applicationId)
                    .map(Application::deployments)
                    .flatMap(deployments -> Optional.ofNullable(deployments.get(zone)));

            if(!deployment.isPresent())
                throw new IllegalArgumentException("Can't redeploy application, no deployment currently exist");

            ApplicationVersion version = deployment.get().applicationVersion();
            if(version.isUnknown())
                throw new IllegalArgumentException("Can't redeploy application, application version is unknown");

            applicationVersion = Optional.of(version);
            vespaVersion = Optional.of(deployment.get().version());
            applicationPackage = Optional.of(controller.applications().getApplicationPackage(controller.applications().require(applicationId), applicationVersion.get()));
        }

        // TODO: get rid of the json object
        DeployOptions deployOptionsJsonClass = new DeployOptions(deployDirectly,
                                                                 vespaVersion,
                                                                 deployOptions.field("ignoreValidationErrors").asBool(),
                                                                 deployOptions.field("deployCurrentVersion").asBool());

        ActivateResult result = controller.applications().deploy(applicationId,
                                                                 zone,
                                                                 applicationPackage,
                                                                 applicationVersion,
                                                                 deployOptionsJsonClass,
                                                                 Optional.of(requireUserPrincipal(request)));

        return new SlimeJsonResponse(toSlime(result));
    }

    private HttpResponse deleteTenant(String tenantName, HttpRequest request) {
        Optional<Tenant> tenant = controller.tenants().get(tenantName);
        if ( ! tenant.isPresent())
            return ErrorResponse.notFoundError("Could not delete tenant '" + tenantName + "': Tenant not found");

        if (tenant.get().type() == Tenant.Type.user)
            controller.tenants().deleteUser((UserTenant) tenant.get());
        else
            controller.tenants().delete(tenant.get().name(),
                                        accessControlRequests.credentials(tenant.get().name(),
                                                                          toSlime(request.getData()).get(),
                                                                          request.getJDiscRequest()));

        // TODO: Change to a message response saying the tenant was deleted
        return tenant(tenant.get(), request);
    }

    private HttpResponse deleteApplication(String tenantName, String applicationName, HttpRequest request) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, "default");
        Optional<Credentials> credentials = controller.tenants().require(id.tenant()).type() == Tenant.Type.user
                ? Optional.empty()
                : Optional.of(accessControlRequests.credentials(id.tenant(), toSlime(request.getData()).get(), request.getJDiscRequest()));
        controller.applications().deleteApplication(id, credentials);
        return new EmptyJsonResponse(); // TODO: Replicates current behavior but should return a message response instead
    }

    private HttpResponse deactivate(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        Application application = controller.applications().require(ApplicationId.from(tenantName, applicationName, instanceName));

        // Attempt to deactivate application even if the deployment is not known by the controller
        controller.applications().deactivate(application.id(), ZoneId.from(environment, region));

        // TODO: Change to return JSON
        return new StringResponse("Deactivated " + path(TenantResource.API_PATH, tenantName,
                                                        ApplicationResource.API_PATH, applicationName,
                                                        EnvironmentResource.API_PATH, environment,
                                                        "region", region,
                                                        "instance", instanceName));
    }

    /**
     * Promote application Chef environments. To be used by component jobs only
     */
    private HttpResponse promoteApplication(String tenantName, String applicationName, HttpRequest request) {
        try{
            ApplicationChefEnvironment chefEnvironment = new ApplicationChefEnvironment(controller.system());
            String sourceEnvironment = chefEnvironment.systemChefEnvironment();
            String targetEnvironment = chefEnvironment.applicationSourceEnvironment(TenantName.from(tenantName), ApplicationName.from(applicationName));
            controller.chefClient().copyChefEnvironment(sourceEnvironment, targetEnvironment);
            return new MessageResponse(String.format("Successfully copied environment %s to %s", sourceEnvironment, targetEnvironment));
        } catch (Exception e) {
            log.log(LogLevel.ERROR, String.format("Error during Chef copy environment. (%s.%s)", tenantName, applicationName), e);
            return ErrorResponse.internalServerError("Unable to promote Chef environments for application");
        }
    }

    /**
     * Promote application Chef environments for jobs that deploy applications
     */
    private HttpResponse promoteApplicationDeployment(String tenantName, String applicationName, String environmentName, String regionName, String instanceName, HttpRequest request) {
        try {
            ApplicationChefEnvironment chefEnvironment = new ApplicationChefEnvironment(controller.system());
            String sourceEnvironment = chefEnvironment.applicationSourceEnvironment(TenantName.from(tenantName), ApplicationName.from(applicationName));
            String targetEnvironment = chefEnvironment.applicationTargetEnvironment(TenantName.from(tenantName), ApplicationName.from(applicationName), Environment.from(environmentName), RegionName.from(regionName));
            controller.chefClient().copyChefEnvironment(sourceEnvironment, targetEnvironment);
            return new MessageResponse(String.format("Successfully copied environment %s to %s", sourceEnvironment, targetEnvironment));
        } catch (Exception e) {
            log.log(LogLevel.ERROR, String.format("Error during Chef copy environment. (%s.%s %s.%s)", tenantName, applicationName, environmentName, regionName), e);
            return ErrorResponse.internalServerError("Unable to promote Chef environments for application");
        }
    }

    private HttpResponse notifyJobCompletion(String tenant, String application, HttpRequest request) {
        try {
            DeploymentJobs.JobReport report = toJobReport(tenant, application, toSlime(request.getData()).get());
            if (   report.jobType() == JobType.component
                && controller.applications().require(report.applicationId()).deploymentJobs().deployedInternally())
                throw new IllegalArgumentException(report.applicationId() + " is set up to be deployed from internally, and no " +
                                                   "longer accepts submissions from Screwdriver v3 jobs. If you need to revert " +
                                                   "to the old pipeline, please file a ticket at yo/vespa-support and request this.");

            controller.applications().deploymentTrigger().notifyOfCompletion(report);
            return new MessageResponse("ok");
        } catch (IllegalStateException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
    }

    private static DeploymentJobs.JobReport toJobReport(String tenantName, String applicationName, Inspector report) {
        Optional<DeploymentJobs.JobError> jobError = Optional.empty();
        if (report.field("jobError").valid()) {
            jobError = Optional.of(DeploymentJobs.JobError.valueOf(report.field("jobError").asString()));
        }
        ApplicationId id = ApplicationId.from(tenantName, applicationName, report.field("instance").asString());
        JobType type = JobType.fromJobName(report.field("jobName").asString());
        long buildNumber = report.field("buildNumber").asLong();
        if (type == JobType.component)
            return DeploymentJobs.JobReport.ofComponent(id,
                                                        report.field("projectId").asLong(),
                                                        buildNumber,
                                                        jobError,
                                                        toSourceRevision(report.field("sourceRevision")));
        else
            return DeploymentJobs.JobReport.ofJob(id, type, buildNumber, jobError);
    }

    private static SourceRevision toSourceRevision(Inspector object) {
        if (!object.field("repository").valid() ||
                !object.field("branch").valid() ||
                !object.field("commit").valid()) {
            throw new IllegalArgumentException("Must specify \"repository\", \"branch\", and \"commit\".");
        }
        return new SourceRevision(object.field("repository").asString(),
                                  object.field("branch").asString(),
                                  object.field("commit").asString());
    }

    private Tenant getTenantOrThrow(String tenantName) {
        return controller.tenants().get(tenantName)
                         .orElseThrow(() -> new NotExistsException(new TenantId(tenantName)));
    }

    private void toSlime(Cursor object, Tenant tenant, HttpRequest request) {
        object.setString("tenant", tenant.name().value());
        object.setString("type", tentantType(tenant));
        switch (tenant.type()) {
            case athenz:
                AthenzTenant athenzTenant = (AthenzTenant) tenant;
                object.setString("athensDomain", athenzTenant.domain().getName());
                object.setString("property", athenzTenant.property().id());
                athenzTenant.propertyId().ifPresent(id -> object.setString("propertyId", id.toString()));
                athenzTenant.contact().ifPresent(c -> {
                    object.setString("propertyUrl", c.propertyUrl().toString());
                    object.setString("contactsUrl", c.url().toString());
                    object.setString("issueCreationUrl", c.issueTrackerUrl().toString());
                    Cursor contactsArray = object.setArray("contacts");
                    c.persons().forEach(persons -> {
                        Cursor personArray = contactsArray.addArray();
                        persons.forEach(personArray::addString);
                    });
                });
                break;
            case user: break;
            case cloud: break;
            default: throw new IllegalArgumentException("Unexpected tenant type '" + tenant.type() + "'.");
        }
        Cursor applicationArray = object.setArray("applications");
        for (Application application : controller.applications().asList(tenant.name())) {
            if (application.id().instance().isDefault()) {// TODO: Skip non-default applications until supported properly
                if (recurseOverApplications(request))
                    toSlime(applicationArray.addObject(), application, request);
                else
                    toSlime(application, applicationArray.addObject(), request);
            }
        }
    }

    // A tenant has different content when in a list ... antipattern, but not solvable before application/v5
    private void tenantInTenantsListToSlime(Tenant tenant, URI requestURI, Cursor object) {
        object.setString("tenant", tenant.name().value());
        Cursor metaData = object.setObject("metaData");
        metaData.setString("type", tentantType(tenant));
        switch (tenant.type()) {
            case athenz:
                AthenzTenant athenzTenant = (AthenzTenant) tenant;
                metaData.setString("athensDomain", athenzTenant.domain().getName());
                metaData.setString("property", athenzTenant.property().id());
                break;
            case user: break;
            case cloud: break;
            default: throw new IllegalArgumentException("Unexpected tenant type '" + tenant.type() + "'.");
        }
        object.setString("url", withPath("/application/v4/tenant/" + tenant.name().value(), requestURI).toString());
    }

    /** Returns a copy of the given URI with the host and port from the given URI and the path set to the given path */
    private URI withPath(String newPath, URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), newPath, null, null);
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Will not happen", e);
        }
    }

    private long asLong(String valueOrNull, long defaultWhenNull) {
        if (valueOrNull == null) return defaultWhenNull;
        try {
            return Long.parseLong(valueOrNull);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected an integer but got '" + valueOrNull + "'");
        }
    }

    private void toSlime(JobStatus.JobRun jobRun, Cursor object) {
        object.setLong("id", jobRun.id());
        object.setString("version", jobRun.platform().toFullString());
        if (!jobRun.application().isUnknown())
            toSlime(jobRun.application(), object.setObject("revision"));
        object.setString("reason", jobRun.reason());
        object.setLong("at", jobRun.at().toEpochMilli());
    }

    private Slime toSlime(InputStream jsonStream) {
        try {
            byte[] jsonBytes = IOUtils.readBytes(jsonStream, 1000 * 1000);
            return SlimeUtils.jsonToSlime(jsonBytes);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private static Principal requireUserPrincipal(HttpRequest request) {
        Principal principal = request.getJDiscRequest().getUserPrincipal();
        if (principal == null) throw new InternalServerErrorException("Expected a user principal");
        return principal;
    }

    private Inspector mandatory(String key, Inspector object) {
        if ( ! object.field(key).valid())
            throw new IllegalArgumentException("'" + key + "' is missing");
        return object.field(key);
    }

    private Optional<String> optional(String key, Inspector object) {
        return SlimeUtils.optionalString(object.field(key));
    }

    private static String path(Object... elements) {
        return Joiner.on("/").join(elements);
    }

    private void toSlime(Application application, Cursor object, HttpRequest request) {
        object.setString("tenant", application.id().tenant().value());
        object.setString("application", application.id().application().value());
        object.setString("instance", application.id().instance().value());
        object.setString("url", withPath("/application/v4/tenant/" + application.id().tenant().value() +
                                         "/application/" + application.id().application().value(), request.getUri()).toString());
    }

    private Slime toSlime(ActivateResult result) {
        Slime slime = new Slime();
        Cursor object = slime.setObject();
        object.setString("revisionId", result.revisionId().id());
        object.setLong("applicationZipSize", result.applicationZipSizeBytes());
        Cursor logArray = object.setArray("prepareMessages");
        if (result.prepareResponse().log != null) {
            for (Log logMessage : result.prepareResponse().log) {
                Cursor logObject = logArray.addObject();
                logObject.setLong("time", logMessage.time);
                logObject.setString("level", logMessage.level);
                logObject.setString("message", logMessage.message);
            }
        }

        Cursor changeObject = object.setObject("configChangeActions");

        Cursor restartActionsArray = changeObject.setArray("restart");
        for (RestartAction restartAction : result.prepareResponse().configChangeActions.restartActions) {
            Cursor restartActionObject = restartActionsArray.addObject();
            restartActionObject.setString("clusterName", restartAction.clusterName);
            restartActionObject.setString("clusterType", restartAction.clusterType);
            restartActionObject.setString("serviceType", restartAction.serviceType);
            serviceInfosToSlime(restartAction.services, restartActionObject.setArray("services"));
            stringsToSlime(restartAction.messages, restartActionObject.setArray("messages"));
        }

        Cursor refeedActionsArray = changeObject.setArray("refeed");
        for (RefeedAction refeedAction : result.prepareResponse().configChangeActions.refeedActions) {
            Cursor refeedActionObject = refeedActionsArray.addObject();
            refeedActionObject.setString("name", refeedAction.name);
            refeedActionObject.setBool("allowed", refeedAction.allowed);
            refeedActionObject.setString("documentType", refeedAction.documentType);
            refeedActionObject.setString("clusterName", refeedAction.clusterName);
            serviceInfosToSlime(refeedAction.services, refeedActionObject.setArray("services"));
            stringsToSlime(refeedAction.messages, refeedActionObject.setArray("messages"));
        }
        return slime;
    }

    private void serviceInfosToSlime(List<ServiceInfo> serviceInfoList, Cursor array) {
        for (ServiceInfo serviceInfo : serviceInfoList) {
            Cursor serviceInfoObject = array.addObject();
            serviceInfoObject.setString("serviceName", serviceInfo.serviceName);
            serviceInfoObject.setString("serviceType", serviceInfo.serviceType);
            serviceInfoObject.setString("configId", serviceInfo.configId);
            serviceInfoObject.setString("hostName", serviceInfo.hostName);
        }
    }

    private void stringsToSlime(List<String> strings, Cursor array) {
        for (String string : strings)
            array.addString(string);
    }

    private String readToString(InputStream stream) {
        Scanner scanner = new Scanner(stream).useDelimiter("\\A");
        if ( ! scanner.hasNext()) return null;
        return scanner.next();
    }

    private boolean systemHasVersion(Version version) {
        return controller.versionStatus().versions().stream().anyMatch(v -> v.versionNumber().equals(version));
    }

    public static void toSlime(DeploymentCost deploymentCost, Cursor object) {
        object.setLong("tco", (long)deploymentCost.getTco());
        object.setLong("waste", (long)deploymentCost.getWaste());
        object.setDouble("utilization", deploymentCost.getUtilization());
        Cursor clustersObject = object.setObject("cluster");
        for (Map.Entry<String, ClusterCost> clusterEntry : deploymentCost.getCluster().entrySet())
            toSlime(clusterEntry.getValue(), clustersObject.setObject(clusterEntry.getKey()));
    }

    private static void toSlime(ClusterCost clusterCost, Cursor object) {
        object.setLong("count", clusterCost.getClusterInfo().getHostnames().size());
        object.setString("resource", getResourceName(clusterCost.getResultUtilization()));
        object.setDouble("utilization", clusterCost.getResultUtilization().getMaxUtilization());
        object.setLong("tco", (int)clusterCost.getTco());
        object.setLong("waste", (int)clusterCost.getWaste());
        object.setString("flavor", clusterCost.getClusterInfo().getFlavor());
        object.setDouble("flavorCost", clusterCost.getClusterInfo().getFlavorCost());
        object.setDouble("flavorCpu", clusterCost.getClusterInfo().getFlavorCPU());
        object.setDouble("flavorMem", clusterCost.getClusterInfo().getFlavorMem());
        object.setDouble("flavorDisk", clusterCost.getClusterInfo().getFlavorDisk());
        object.setString("type", clusterCost.getClusterInfo().getClusterType().name());
        Cursor utilObject = object.setObject("util");
        utilObject.setDouble("cpu", clusterCost.getResultUtilization().getCpu());
        utilObject.setDouble("mem", clusterCost.getResultUtilization().getMemory());
        utilObject.setDouble("disk", clusterCost.getResultUtilization().getDisk());
        utilObject.setDouble("diskBusy", clusterCost.getResultUtilization().getDiskBusy());
        Cursor usageObject = object.setObject("usage");
        usageObject.setDouble("cpu", clusterCost.getSystemUtilization().getCpu());
        usageObject.setDouble("mem", clusterCost.getSystemUtilization().getMemory());
        usageObject.setDouble("disk", clusterCost.getSystemUtilization().getDisk());
        usageObject.setDouble("diskBusy", clusterCost.getSystemUtilization().getDiskBusy());
        Cursor hostnamesArray = object.setArray("hostnames");
        for (String hostname : clusterCost.getClusterInfo().getHostnames())
            hostnamesArray.addString(hostname);
    }

    private static String getResourceName(ClusterUtilization utilization) {
        String name = "cpu";
        double max = utilization.getMaxUtilization();

        if (utilization.getMemory() == max) {
            name = "mem";
        } else if (utilization.getDisk() == max) {
            name = "disk";
        } else if (utilization.getDiskBusy() == max) {
            name = "diskbusy";
        }

        return name;
    }

    private static boolean recurseOverTenants(HttpRequest request) {
        return recurseOverApplications(request) || "tenant".equals(request.getProperty("recursive"));
    }

    private static boolean recurseOverApplications(HttpRequest request) {
        return recurseOverDeployments(request) || "application".equals(request.getProperty("recursive"));
    }

    private static boolean recurseOverDeployments(HttpRequest request) {
        return ImmutableSet.of("all", "true", "deployment").contains(request.getProperty("recursive"));
    }

    private static String tentantType(Tenant tenant) {
        switch (tenant.type()) {
            case user: return "USER";
            case athenz: return "ATHENS";
            case cloud: return "CLOUD";
            default: throw new IllegalArgumentException("Unknown tenant type: " + tenant.getClass().getSimpleName());
        }
    }

    private static ApplicationId appIdFromPath(Path path) {
        return ApplicationId.from(path.get("tenant"), path.get("application"), path.get("instance"));
    }

    private static JobType jobTypeFromPath(Path path) {
        return JobType.fromJobName(path.get("jobtype"));
    }

    private static RunId runIdFromPath(Path path) {
        long number = Long.parseLong(path.get("number"));
        return new RunId(appIdFromPath(path), jobTypeFromPath(path), number);
    }

    private HttpResponse submit(String tenant, String application, HttpRequest request) {
        Map<String, byte[]> dataParts = new MultipartParser().parse(request);
        Inspector submitOptions = SlimeUtils.jsonToSlime(dataParts.get(EnvironmentResource.SUBMIT_OPTIONS)).get();
        SourceRevision sourceRevision = toSourceRevision(submitOptions);
        String authorEmail = submitOptions.field("authorEmail").asString();
        long projectId = Math.max(1, submitOptions.field("projectId").asLong());

        ApplicationPackage applicationPackage = new ApplicationPackage(dataParts.get(EnvironmentResource.APPLICATION_ZIP));
        controller.applications().verifyApplicationIdentityConfiguration(TenantName.from(tenant),
                                                                         applicationPackage,
                                                                         Optional.of(requireUserPrincipal(request)));

        return JobControllerApiHandlerHelper.submitResponse(controller.jobController(),
                                                            tenant,
                                                            application,
                                                            sourceRevision,
                                                            authorEmail,
                                                            projectId,
                                                            applicationPackage,
                                                            dataParts.get(EnvironmentResource.APPLICATION_TEST_ZIP));
    }

}
