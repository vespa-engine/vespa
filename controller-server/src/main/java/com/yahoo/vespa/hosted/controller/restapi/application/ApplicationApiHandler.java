// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.AlreadyExistsException;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.NotExistsException;
import com.yahoo.vespa.hosted.controller.api.ActivateResult;
import com.yahoo.vespa.hosted.controller.api.InstanceEndpoints;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.application.v4.ApplicationResource;
import com.yahoo.vespa.hosted.controller.api.application.v4.EnvironmentResource;
import com.yahoo.vespa.hosted.controller.api.application.v4.TenantResource;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.GitRevision;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ScrewdriverBuildJob;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.RefeedAction;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.RestartAction;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ServiceInfo;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitBranch;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitCommit;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitRepository;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserGroup;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ApplicationRevision;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterCost;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentCost;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.NToken;
import com.yahoo.vespa.hosted.controller.athenz.ZmsException;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.MessageResponse;
import com.yahoo.vespa.hosted.controller.restapi.Path;
import com.yahoo.vespa.hosted.controller.restapi.ResourceResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.StringResponse;
import com.yahoo.vespa.hosted.controller.restapi.filter.SetBouncerPassthruHeaderFilter;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.yolean.Exceptions;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * This implements the application/v4 API which is used to deploy and manage applications
 * on hosted Vespa.
 * 
 * @author bratseth
 * @author mpolden
 */
@SuppressWarnings("unused") // created by injection
public class ApplicationApiHandler extends LoggingRequestHandler {

    private final Controller controller;
    private final Authorizer authorizer;
    private final AthenzClientFactory athenzClientFactory;

    @Inject
    public ApplicationApiHandler(Executor executor, AccessLog accessLog, Controller controller, Authorizer authorizer,
                                 AthenzClientFactory athenzClientFactory) {
        super(executor, accessLog);
        this.controller = controller;
        this.authorizer = authorizer;
        this.athenzClientFactory = athenzClientFactory;
    }

    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(20); // deploys may take a long time;
    }
    
    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                case PUT: return handlePUT(request);
                case POST: return handlePOST(request);
                case DELETE: return handleDELETE(request);
                case OPTIONS: return handleOPTIONS();
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        }
        catch (ForbiddenException e) {
            return ErrorResponse.forbidden(Exceptions.toMessageString(e));
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
    
    private HttpResponse handleGET(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/application/v4/")) return root(request);
        if (path.matches("/application/v4/user")) return authenticatedUser(request);
        if (path.matches("/application/v4/tenant")) return tenants(request);
        if (path.matches("/application/v4/tenant-pipeline")) return tenantPipelines();
        if (path.matches("/application/v4/athensDomain")) return athenzDomains(request);
        if (path.matches("/application/v4/property")) return properties();
        if (path.matches("/application/v4/cookiefreshness")) return cookieFreshness(request);
        if (path.matches("/application/v4/tenant/{tenant}")) return tenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application")) return applications(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}")) return application(path.get("tenant"), path.get("application"), path, request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}")) return deployment(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/converge")) return waitForConvergence(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/service")) return services(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/service/{service}/{*}")) return service(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), path.get("service"), path.getRest(), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation")) return rotationStatus(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation/override"))
            return getGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handlePUT(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/application/v4/user")) return createUser(request);
        if (path.matches("/application/v4/tenant/{tenant}")) return updateTenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/migrateTenantToAthens")) return migrateTenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation/override"))
            return setGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), false, request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handlePOST(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/application/v4/tenant/{tenant}")) return createTenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}")) return createApplication(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/promote")) return promoteApplication(path.get("tenant"), path.get("application"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying")) return deploy(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}")) return deploy(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/deploy")) return deploy(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request); // legacy synonym of the above
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/restart")) return restart(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/log")) return log(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/promote")) return promoteApplicationDeployment(path.get("tenant"), path.get("application"), path.get("environment"), path.get("region"));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handleDELETE(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/application/v4/tenant/{tenant}")) return deleteTenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}")) return deleteApplication(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying")) return cancelDeploy(path.get("tenant"), path.get("application"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}")) return deactivate(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation/override"))
            return setGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), true, request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }
    
    private HttpResponse handleOPTIONS() {
        // We implement this to avoid redirect loops on OPTIONS requests from browsers, but do not really bother
        // spelling out the methods supported at each path, which we should
        EmptyJsonResponse response = new EmptyJsonResponse();
        response.headers().put("Allow", "GET,PUT,POST,DELETE,OPTIONS");
        return response;
    }
    
    private HttpResponse root(HttpRequest request) {
        return new ResourceResponse(request, "user", "tenant", "tenant-pipeline", "athensDomain",
                                    "property", "cookiefreshness");
    }
    
    private HttpResponse authenticatedUser(HttpRequest request) {
        String userIdString = request.getProperty("userOverride");
        if (userIdString == null)
            userIdString = userFrom(request)
                     .orElseThrow(() -> new ForbiddenException("You must be authenticated or specify userOverride"));
        UserId userId = new UserId(userIdString);
        
        List<Tenant> tenants = controller.tenants().asList(userId);

        Slime slime = new Slime();
        Cursor response = slime.setObject();
        response.setString("user", userId.id());
        Cursor tenantsArray = response.setArray("tenants");
        for (Tenant tenant : tenants)
            tenantInTenantsListToSlime(tenant, request.getUri(), tenantsArray.addObject());
        response.setBool("tenantExists", tenants.stream().map(Tenant::getId).anyMatch(id -> id.isTenantFor(userId)));
        return new SlimeJsonResponse(slime);
    }
    
    private HttpResponse tenants(HttpRequest request) {
        Slime slime = new Slime();
        Cursor response = slime.setArray();
        for (Tenant tenant : controller.tenants().asList())
            tenantInTenantsListToSlime(tenant, request.getUri(), response.addObject());
        return new SlimeJsonResponse(slime);
    }
    
    /** Lists the screwdriver project id for each application */
    private HttpResponse tenantPipelines() {
        Slime slime = new Slime();
        Cursor response = slime.setObject();
        Cursor pipelinesArray = response.setArray("tenantPipelines");
        for (Application application : controller.applications().asList()) {
            if ( ! application.deploymentJobs().projectId().isPresent()) continue;

            Cursor pipelineObject = pipelinesArray.addObject();
            pipelineObject.setString("screwdriverId", String.valueOf(application.deploymentJobs().projectId().get()));
            pipelineObject.setString("tenant", application.id().tenant().value());
            pipelineObject.setString("application", application.id().application().value());
            pipelineObject.setString("instance", application.id().instance().value());
        }
        response.setArray("brokenTenantPipelines"); // not used but may need to be present
        return new SlimeJsonResponse(slime);
    }
    
    private HttpResponse athenzDomains(HttpRequest request) {
        Slime slime = new Slime();
        Cursor response = slime.setObject();
        Cursor array = response.setArray("data");
        for (AthenzDomain athenzDomain : controller.getDomainList(request.getProperty("prefix"))) {
            array.addString(athenzDomain.id());
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse properties() {
        Slime slime = new Slime();
        Cursor response = slime.setObject();
        Cursor array = response.setArray("properties");
        for (Map.Entry<PropertyId, Property> entry : controller.fetchPropertyList().entrySet()) {
            Cursor propertyObject = array.addObject();
            propertyObject.setString("propertyid", entry.getKey().id());
            propertyObject.setString("property", entry.getValue().id());
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse cookieFreshness(HttpRequest request) {
        Slime slime = new Slime();
        String passThruHeader = request.getHeader(SetBouncerPassthruHeaderFilter.BOUNCER_PASSTHRU_HEADER_FIELD);
        slime.setObject().setBool("shouldRefreshCookie", 
                                  ! SetBouncerPassthruHeaderFilter.BOUNCER_PASSTHRU_COOKIE_OK.equals(passThruHeader));
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse tenant(String tenantName, HttpRequest request) {
        Optional<Tenant> tenant = controller.tenants().tenant(new TenantId(tenantName));
        if ( ! tenant.isPresent())
            return ErrorResponse.notFoundError("Tenant '" + tenantName + "' does not exist");
        return new SlimeJsonResponse(toSlime(tenant.get(), request, true));
    }
    
    private HttpResponse applications(String tenantName, HttpRequest request) {
        TenantName tenant = TenantName.from(tenantName);
        Slime slime = new Slime();
        Cursor array = slime.setArray();
        for (Application application : controller.applications().asList(tenant))
            toSlime(application, array.addObject(), request);
        return new SlimeJsonResponse(slime);
    }
    
    private HttpResponse application(String tenantName, String applicationName, Path path, HttpRequest request) {
        Slime slime = new Slime();
        Cursor response = slime.setObject();

        com.yahoo.config.provision.ApplicationId applicationId = com.yahoo.config.provision.ApplicationId.from(tenantName, applicationName, "default");
        Application application =
                controller.applications().get(applicationId)
                        .orElseThrow(() -> new NotExistsException(applicationId + " not found"));
        
        // Currently deploying change
        if (application.deploying().isPresent()) {
            Cursor deployingObject = response.setObject("deploying");
            if (application.deploying().get() instanceof Change.VersionChange)
                deployingObject.setString("version", ((Change.VersionChange)application.deploying().get()).version().toString());
            else if (((Change.ApplicationChange)application.deploying().get()).revision().isPresent())
                toSlime(((Change.ApplicationChange)application.deploying().get()).revision().get(), deployingObject.setObject("revision"));
        }

        // Jobs sorted according to deployment spec
        Map<DeploymentJobs.JobType, JobStatus> jobStatus = controller.applications().deploymentTrigger()
                .deploymentOrder()
                .sortBy(application.deploymentSpec(), application.deploymentJobs().jobStatus());

        Cursor deploymentsArray = response.setArray("deploymentJobs");
        for (JobStatus job : jobStatus.values()) {
            Cursor jobObject = deploymentsArray.addObject();            
            jobObject.setString("type", job.type().id());
            jobObject.setBool("success", job.isSuccess());

            job.lastTriggered().ifPresent(jobRun -> toSlime(jobRun, jobObject.setObject("lastTriggered")));
            job.lastCompleted().ifPresent(jobRun -> toSlime(jobRun, jobObject.setObject("lastCompleted")));
            job.firstFailing().ifPresent(jobRun -> toSlime(jobRun, jobObject.setObject("firstFailing")));
            job.lastSuccess().ifPresent(jobRun -> toSlime(jobRun, jobObject.setObject("lastSuccess")));
        }

        // Compile version. The version that should be used when building an application
        response.setString("compileVersion", application.compileVersion(controller).toFullString());

        // Rotations
        Cursor globalRotationsArray = response.setArray("globalRotations");
        Set<URI> rotations = controller.getRotationUris(applicationId);
        Map<String, RotationStatus> rotationHealthStatus =
                rotations.isEmpty() ? Collections.emptyMap() : controller.getHealthStatus(rotations.iterator().next().getHost());
        for (URI rotation : rotations)
            globalRotationsArray.addString(rotation.toString());

        // Deployments sorted according to deployment spec
        Map<Zone, Deployment> deployments = controller.applications().deploymentTrigger()
                .deploymentOrder()
                .sortBy(application.deploymentSpec().zones(), application.deployments());
        Cursor instancesArray = response.setArray("instances");
        for (Deployment deployment : deployments.values()) {
            Cursor deploymentObject = instancesArray.addObject();
            deploymentObject.setString("environment", deployment.zone().environment().value());
            deploymentObject.setString("region", deployment.zone().region().value());
            deploymentObject.setString("instance", application.id().instance().value()); // pointless
            if ( ! rotations.isEmpty())
                setRotationStatus(deployment, rotationHealthStatus, deploymentObject);
            deploymentObject.setString("url", withPath(path.asString() +
                                                       "/environment/" + deployment.zone().environment().value() +
                                                       "/region/" + deployment.zone().region().value() +
                                                       "/instance/" + application.id().instance().value(),
                                                       request.getUri()).toString());
        }
        
        // Metrics
        try {
            MetricsService.ApplicationMetrics metrics = controller.metricsService().getApplicationMetrics(applicationId);
            Cursor metricsObject = response.setObject("metrics");
            metricsObject.setDouble("queryServiceQuality", metrics.queryServiceQuality());
            metricsObject.setDouble("writeServiceQuality", metrics.writeServiceQuality());
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed getting Yamas metrics", Exceptions.toMessageString(e));
        }

        return new SlimeJsonResponse(slime);
    }

    private HttpResponse deployment(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        Application application = controller.applications().get(id)
                .orElseThrow(() -> new NotExistsException(id + " not found"));

        DeploymentId deploymentId = new DeploymentId(application.id(),
                                                     new Zone(Environment.from(environment), RegionName.from(region)));

        Deployment deployment = application.deployments().get(deploymentId.zone());
        if (deployment == null)
            throw new NotExistsException(application + " is not deployed in " + deploymentId.zone());

        Optional<InstanceEndpoints> deploymentEndpoints = controller.applications().getDeploymentEndpoints(deploymentId);

        Slime slime = new Slime();
        Cursor response = slime.setObject();
        Cursor serviceUrlArray = response.setArray("serviceUrls");
        if (deploymentEndpoints.isPresent()) {
            for (URI uri : deploymentEndpoints.get().getContainerEndpoints())
                serviceUrlArray.addString(uri.toString());
        }

        response.setString("nodes", withPath("/zone/v2/" + environment + "/" + region + "/nodes/v2/node/?&recursive=true&application=" + tenantName + "." + applicationName + "." + instanceName, request.getUri()).toString());

        Environment env = Environment.from(environment);
        RegionName regionName = RegionName.from(region);
        URI elkUrl = controller.getElkUri(env, regionName, deploymentId);
        if (elkUrl != null)
            response.setString("elkUrl", elkUrl.toString());

        response.setString("yamasUrl", monitoringSystemUri(deploymentId).toString());
        response.setString("version", deployment.version().toFullString());
        response.setString("revision", deployment.revision().id());
        response.setLong("deployTimeEpochMs", deployment.at().toEpochMilli());
        Optional<Duration> deploymentTimeToLive = controller.zoneRegistry().getDeploymentTimeToLive(Environment.from(environment), RegionName.from(region));
        deploymentTimeToLive.ifPresent(duration -> response.setLong("expiryTimeEpochMs", deployment.at().plus(duration).toEpochMilli()));

        application.deploymentJobs().projectId().ifPresent(i -> response.setString("screwdriverId", String.valueOf(i)));
        sourceRevisionToSlime(deployment.revision().source(), response);

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

        return new SlimeJsonResponse(slime);
    }

    private void toSlime(ApplicationRevision revision, Cursor object) {
        object.setString("hash", revision.id());
        if (revision.source().isPresent())
            sourceRevisionToSlime(revision.source(), object.setObject("source"));
    }

    private void sourceRevisionToSlime(Optional<SourceRevision> revision, Cursor object) {
        if ( ! revision.isPresent()) return;
        object.setString("gitRepository", revision.get().repository());
        object.setString("gitBranch", revision.get().branch());
        object.setString("gitCommit", revision.get().commit());
    }

    private URI monitoringSystemUri(DeploymentId deploymentId) {
        return controller.zoneRegistry().getMonitoringSystemUri(deploymentId.zone().environment(),
                                                                deploymentId.zone().region(),
                                                                deploymentId.applicationId());
    }

    private HttpResponse setGlobalRotationOverride(String tenantName, String applicationName, String instanceName, String environment, String region, boolean inService, HttpRequest request) {

        // Check if request is authorized
        Optional<Tenant> existingTenant = controller.tenants().tenant(new TenantId(tenantName));
        if (!existingTenant.isPresent())
            return ErrorResponse.notFoundError("Tenant '" + tenantName + "' does not exist");

        authorizer.throwIfUnauthorized(existingTenant.get().getId(), request);

        // Decode payload (reason) and construct parameter to the configserver

        Inspector requestData = toSlime(request.getData()).get();
        String reason = mandatory("reason", requestData).asString();
        String agent = authorizer.getUserId(request).toString();
        long timestamp = controller.clock().instant().getEpochSecond();
        EndpointStatus.Status status = inService ? EndpointStatus.Status.in : EndpointStatus.Status.out;
        EndpointStatus endPointStatus = new EndpointStatus(status, reason, agent, timestamp);

        // DeploymentId identifies the zone and application we are dealing with
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                new Zone(Environment.from(environment), RegionName.from(region)));
        try {
            List<String> rotations = controller.applications().setGlobalRotationStatus(deploymentId, endPointStatus);
            return new MessageResponse(String.format("Rotations %s successfully set to %s service", rotations.toString(), inService ? "in" : "out of"));
        } catch (IOException e) {
            return ErrorResponse.internalServerError("Unable to alter rotation status: " + e.getMessage());
        }
    }

    private HttpResponse getGlobalRotationOverride(String tenantName, String applicationName, String instanceName, String environment, String region) {

        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                new Zone(Environment.from(environment), RegionName.from(region)));

        Slime slime = new Slime();
        Cursor c1 = slime.setObject().setArray("globalrotationoverride");
        try {
            Map<String, EndpointStatus> rotations = controller.applications().getGlobalRotationStatus(deploymentId);
            for (String rotation : rotations.keySet()) {
                EndpointStatus currentStatus = rotations.get(rotation);
                c1.addString(rotation);
                Cursor c2 = c1.addObject();
                c2.setString("status", currentStatus.getStatus().name());
                c2.setString("reason", currentStatus.getReason() == null ? "" : currentStatus.getReason());
                c2.setString("agent", currentStatus.getAgent()  == null ? "" : currentStatus.getAgent());
                c2.setLong("timestamp", currentStatus.getEpoch());
            }
        } catch (IOException e) {
            return ErrorResponse.internalServerError("Unable to get rotation status: " + e.getMessage());
        }

        return new SlimeJsonResponse(slime);
    }

    private HttpResponse rotationStatus(String tenantName, String applicationName, String instanceName, String environment, String region) {

        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, instanceName);
        Set<URI> rotations = controller.getRotationUris(applicationId);
        if (rotations.isEmpty())
            throw new NotExistsException("global rotation does not exist for '" + environment + "." + region + "'");

        Slime slime = new Slime();
        Cursor response = slime.setObject();

        Map<String, RotationStatus> rotationHealthStatus = controller.getHealthStatus(rotations.iterator().next().getHost());

        for (String rotationEndpoint : rotationHealthStatus.keySet()) {
            if (rotationEndpoint.contains(toDns(environment)) && rotationEndpoint.contains(toDns(region))) {
                Cursor bcpStatusObject = response.setObject("bcpStatus");
                bcpStatusObject.setString("rotationStatus", rotationHealthStatus.getOrDefault(rotationEndpoint, RotationStatus.UNKNOWN).name());
            }
        }

        return new SlimeJsonResponse(slime);
    }

    private HttpResponse waitForConvergence(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        return new JacksonJsonResponse(controller.waitForConfigConvergence(new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                                                                            new Zone(Environment.from(environment), RegionName.from(region))),
                                                                           asLong(request.getProperty("timeout"), 1000)));
    }

    private HttpResponse services(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        ApplicationView applicationView = controller.getApplicationView(tenantName, applicationName, instanceName, environment, region);
        ServiceApiResponse response = new ServiceApiResponse(new Zone(Environment.from(environment), RegionName.from(region)),
                                                             new com.yahoo.config.provision.ApplicationId.Builder().tenant(tenantName).applicationName(applicationName).instanceName(instanceName).build(),
                                                             controller.getConfigServerUris(Environment.from(environment), RegionName.from(region)),
                                                             request.getUri());
        response.setResponse(applicationView);
        return response;
    }

    private HttpResponse service(String tenantName, String applicationName, String instanceName, String environment, String region, String serviceName, String restPath, HttpRequest request) {
        Map<?,?> result = controller.getServiceApiResponse(tenantName, applicationName, instanceName, environment, region, serviceName, restPath);
        ServiceApiResponse response = new ServiceApiResponse(new Zone(Environment.from(environment), RegionName.from(region)),
                                                             new com.yahoo.config.provision.ApplicationId.Builder().tenant(tenantName).applicationName(applicationName).instanceName(instanceName).build(),
                                                             controller.getConfigServerUris(Environment.from(environment), RegionName.from(region)),
                                                             request.getUri());
        response.setResponse(result, serviceName, restPath);
        return response;
    }
    
    private HttpResponse createUser(HttpRequest request) {
        Optional<String> username = userFrom(request);
        if ( ! username.isPresent() ) throw new ForbiddenException("Not authenticated.");

        try {
            controller.tenants().createUserTenant(username.get());
            return new MessageResponse("Created user '" + username.get() + "'");
        } catch (AlreadyExistsException e) {
            // Ok
            return new MessageResponse("User '" + username + "' already exists");
        }
    }

    private HttpResponse updateTenant(String tenantName, HttpRequest request) {
        Optional<Tenant> existingTenant = controller.tenants().tenant(new TenantId(tenantName));
        if ( ! existingTenant.isPresent()) return ErrorResponse.notFoundError("Tenant '" + tenantName + "' does not exist");;

        Inspector requestData = toSlime(request.getData()).get();

        authorizer.throwIfUnauthorized(existingTenant.get().getId(), request);
        Tenant updatedTenant;
        switch (existingTenant.get().tenantType()) {
            case USER: {
                throw new BadRequestException("Cannot set property or OpsDB user group for user tenant");
            }
            case OPSDB: {
                UserGroup userGroup = new UserGroup(mandatory("userGroup", requestData).asString());
                updatedTenant = Tenant.createOpsDbTenant(new TenantId(tenantName), 
                                                         userGroup, 
                                                         new Property(mandatory("property", requestData).asString()),
                                                         optional("propertyId", requestData).map(PropertyId::new));
                throwIfNotSuperUserOrPartOfOpsDbGroup(userGroup, request);
                controller.tenants().updateTenant(updatedTenant, authorizer.getNToken(request));
                break;
            }
            case ATHENS: {
                if (requestData.field("userGroup").valid())
                    throw new BadRequestException("Cannot set OpsDB user group to Athens tenant");
                updatedTenant = Tenant.createAthensTenant(new TenantId(tenantName), 
                                                          new AthenzDomain(mandatory("athensDomain", requestData).asString()),
                                                          new Property(mandatory("property", requestData).asString()),
                                                          optional("propertyId", requestData).map(PropertyId::new));
                controller.tenants().updateTenant(updatedTenant, authorizer.getNToken(request));
                break;
            }
            default: {
                throw new BadRequestException("Unknown tenant type: " + existingTenant.get().tenantType());
            }
        }
        return new SlimeJsonResponse(toSlime(updatedTenant, request, true));
    }

    private HttpResponse createTenant(String tenantName, HttpRequest request) {
        if (new TenantId(tenantName).isUser())
            return ErrorResponse.badRequest("Use User API to create user tenants.");

        Inspector requestData = toSlime(request.getData()).get();

        Tenant tenant = new Tenant(new TenantId(tenantName),
                                   optional("userGroup", requestData).map(UserGroup::new),
                                   optional("property", requestData).map(Property::new),
                                   optional("athensDomain", requestData).map(AthenzDomain::new),
                                   optional("propertyId", requestData).map(PropertyId::new));
        if (tenant.isOpsDbTenant())
            throwIfNotSuperUserOrPartOfOpsDbGroup(new UserGroup(mandatory("userGroup", requestData).asString()), request);
        if (tenant.isAthensTenant())
            throwIfNotAthenzDomainAdmin(new AthenzDomain(mandatory("athensDomain", requestData).asString()), request);
 
        controller.tenants().addTenant(tenant, authorizer.getNToken(request));
        return new SlimeJsonResponse(toSlime(tenant, request, true));
    }

    private HttpResponse migrateTenant(String tenantName, HttpRequest request) {
        TenantId tenantid = new TenantId(tenantName);
        Inspector requestData = toSlime(request.getData()).get();
        AthenzDomain tenantDomain = new AthenzDomain(mandatory("athensDomain", requestData).asString());
        Property property = new Property(mandatory("property", requestData).asString());
        PropertyId propertyId = new PropertyId(mandatory("propertyId", requestData).asString());

        authorizer.throwIfUnauthorized(tenantid, request);
        throwIfNotAthenzDomainAdmin(tenantDomain, request);
        NToken nToken = authorizer.getNToken(request)
                .orElseThrow(() ->
                        new BadRequestException("The NToken for a domain admin is required to migrate tenant to Athens"));
        Tenant tenant = controller.tenants().migrateTenantToAthenz(tenantid, tenantDomain, propertyId, property, nToken);
        return new SlimeJsonResponse(toSlime(tenant, request, true));
    }

    private HttpResponse createApplication(String tenantName, String applicationName, HttpRequest request) {
        authorizer.throwIfUnauthorized(new TenantId(tenantName), request);
        Application application;
        try {
            application = controller.applications().createApplication(com.yahoo.config.provision.ApplicationId.from(tenantName, applicationName, "default"), authorizer.getNToken(request));
        }
        catch (ZmsException e) { // TODO: Push conversion down
            if (e.getCode() == com.yahoo.jdisc.Response.Status.FORBIDDEN)
                throw new ForbiddenException("Not authorized to create application", e);
            else
                throw e;
        }

        Slime slime = new Slime();
        toSlime(application, slime.setObject(), request);
        return new SlimeJsonResponse(slime);
    }

    /** Trigger deployment of the last built application package, on a given version */
    private HttpResponse deploy(String tenantName, String applicationName, HttpRequest request) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, "default");
        try (Lock lock = controller.applications().lock(id)) {
            Application application = controller.applications().require(id);
            if (application.deploying().isPresent())
                throw new IllegalArgumentException("Can not start a deployment of " + application + " at this time: " +
                                                   application.deploying().get() + " is in progress");

            Version version = decideDeployVersion(request);
            if ( ! systemHasVersion(version))
                throw new IllegalArgumentException("Cannot trigger deployment of version '" + version + "': " +
                                                   "Version is not active in this system. " +
                                                   "Active versions: " + controller.versionStatus().versions());

            controller.applications().deploymentTrigger().triggerChange(application.id(), new Change.VersionChange(version));
            return new MessageResponse("Triggered deployment of " + application + " on version " + version);
        }
    }

    /** Cancel any ongoing change for given application */
    private HttpResponse cancelDeploy(String tenantName, String applicationName) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, "default");
        try (Lock lock = controller.applications().lock(id)) {
            Application application = controller.applications().require(id);
            Optional<Change> change = application.deploying();
            if (!change.isPresent()) {
                return new MessageResponse("No deployment in progress for " + application + " at this time");
            }
            controller.applications().deploymentTrigger().cancelChange(id);
            return new MessageResponse("Cancelled " + change.get() + " for " + application);
        }
    }

    /** Schedule restart of deployment, or specific host in a deployment */
    private HttpResponse restart(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                                     new Zone(Environment.from(environment), RegionName.from(region)));
        // TODO: Propagate all filters
        if (request.getProperty("hostname") != null)
            controller.applications().restartHost(deploymentId, new Hostname(request.getProperty("hostname")));
        else
            controller.applications().restart(deploymentId);

        // TODO: Change to return JSON
        return new StringResponse("Requested restart of " + path(TenantResource.API_PATH, tenantName,
                                                                 ApplicationResource.API_PATH, applicationName,
                                                                 EnvironmentResource.API_PATH, environment,
                                                                 "region", region,
                                                                 "instance", instanceName));
    }

    /**
     * This returns and deletes recent error logs from this deployment, which is used by tenant deployment jobs to verify that
     * the application is working. It is called for all production zones, also those in which the application is not present,
     * and possibly before it is present, so failures are normal and expected.
     */
    private HttpResponse log(String tenantName, String applicationName, String instanceName, String environment, String region) {
        try {
            DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                                         new Zone(Environment.from(environment), RegionName.from(region)));
            return new JacksonJsonResponse(controller.grabLog(deploymentId));
        }
        catch (RuntimeException e) {
            Slime slime = new Slime();
            slime.setObject();
            return new SlimeJsonResponse(slime);
        }
    }

    private HttpResponse deploy(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, instanceName);
        Zone zone = new Zone(Environment.from(environment), RegionName.from(region));

        Map<String, byte[]> dataParts = new MultipartParser().parse(request);
        if ( ! dataParts.containsKey("deployOptions"))
            return ErrorResponse.badRequest("Missing required form part 'deployOptions'");
        if ( ! dataParts.containsKey("applicationZip"))
            return ErrorResponse.badRequest("Missing required form part 'applicationZip'");

        Inspector deployOptions = SlimeUtils.jsonToSlime(dataParts.get("deployOptions")).get();

        DeployAuthorizer deployAuthorizer = new DeployAuthorizer(controller.zoneRegistry(), athenzClientFactory);
        Tenant tenant = controller.tenants().tenant(new TenantId(tenantName)).orElseThrow(() -> new NotExistsException(new TenantId(tenantName)));
        Principal principal = authorizer.getPrincipal(request);
        if (principal instanceof AthenzPrincipal) {
            deployAuthorizer.throwIfUnauthorizedForDeploy(principal,
                                                          Environment.from(environment),
                                                          tenant,
                                                          applicationId);
        } else { // In case of host-based principal
            // TODO What about other user type principals like Bouncer?
            log.log(LogLevel.WARNING,
                    "Using deprecated DeployAuthorizer.throwIfUnauthorizedForDeploy. Principal=" + principal);
            UserId userId = new UserId(principal.getName());
            deployAuthorizer.throwIfUnauthorizedForDeploy(
                    Environment.from(environment),
                    userId,
                    tenant,
                    applicationId,
                    optional("screwdriverBuildJob", deployOptions).map(ScrewdriverId::new));
        }

        
        // TODO: get rid of the json object
        DeployOptions deployOptionsJsonClass = new DeployOptions(screwdriverBuildJobFromSlime(deployOptions.field("screwdriverBuildJob")),
                                                                 optional("vespaVersion", deployOptions).map(Version::new),
                                                                 deployOptions.field("ignoreValidationErrors").asBool(),
                                                                 deployOptions.field("deployCurrentVersion").asBool()); 
        ActivateResult result = controller.applications().deployApplication(applicationId, 
                                                                            zone, 
                                                                            new ApplicationPackage(dataParts.get("applicationZip")),
                                                                            deployOptionsJsonClass);
        return new SlimeJsonResponse(toSlime(result, dataParts.get("applicationZip").length));
    }

    private HttpResponse deleteTenant(String tenantName, HttpRequest request) {
        Optional<Tenant> tenant = controller.tenants().tenant(new TenantId(tenantName));
        if ( ! tenant.isPresent()) return ErrorResponse.notFoundError("Could not delete tenant '" + tenantName + "': Tenant not found"); // NOTE: The Jersey implementation would silently ignore this

        authorizer.throwIfUnauthorized(new TenantId(tenantName), request);
        controller.tenants().deleteTenant(new TenantId(tenantName), authorizer.getNToken(request));

        // TODO: Change to a message response saying the tenant was deleted
        return new SlimeJsonResponse(toSlime(tenant.get(), request, false));
    }

    private HttpResponse deleteApplication(String tenantName, String applicationName, HttpRequest request) {
        authorizer.throwIfUnauthorized(new TenantId(tenantName), request);

        com.yahoo.config.provision.ApplicationId id = com.yahoo.config.provision.ApplicationId.from(tenantName, applicationName, "default");
        Application deleted = controller.applications().deleteApplication(id, authorizer.getNToken(request));
        if (deleted == null)
            return ErrorResponse.notFoundError("Could not delete application '" + id + "': Application not found");
        return new EmptyJsonResponse(); // TODO: Replicates current behavior but should return a message response instead
    }

    private HttpResponse deactivate(String tenantName, String applicationName, String instanceName, String environment, String region) {
        Application application = controller.applications().require(ApplicationId.from(tenantName, applicationName, instanceName));

        Zone zone = new Zone(Environment.from(environment), RegionName.from(region));
        Deployment deployment = application.deployments().get(zone);
        if (deployment == null) {
            // Attempt to deactivate application even if the deployment is not known by the controller
            controller.applications().deactivate(application, zone);
        } else {
            controller.applications().deactivate(application, deployment, false);
        }

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
    private HttpResponse promoteApplication(String tenantName, String applicationName) {
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
    private HttpResponse promoteApplicationDeployment(String tenantName, String applicationName, String environmentName, String regionName) {
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

    private Optional<String> userFrom(HttpRequest request) {
        return authorizer.getPrincipalIfAny(request).map(Principal::getName);
    }

    private void toSlime(Tenant tenant, Cursor object, HttpRequest request, boolean listApplications) {
        object.setString("type", tenant.tenantType().name());
        tenant.getAthensDomain().ifPresent(a -> object.setString("athensDomain", a.id()));
        tenant.getProperty().ifPresent(p -> object.setString("property", p.id()));
        tenant.getPropertyId().ifPresent(p -> object.setString("propertyId", p.toString()));
        tenant.getUserGroup().ifPresent(g -> object.setString("userGroup", g.id()));
        Cursor applicationArray = object.setArray("applications");
        if (listApplications) { // This cludge is needed because we call this after deleting the tenant. As this call makes another tenant lookup it will fail. TODO is to support lookup on tenant
            for (Application application : controller.applications().asList(TenantName.from(tenant.getId().id()))) {
                if (application.id().instance().isDefault()) // TODO: Skip non-default applications until supported properly
                    toSlime(application, applicationArray.addObject(), request);
            }
        }
    }

    // A tenant has different content when in a list ... antipattern, but not solvable before application/v5
    private void tenantInTenantsListToSlime(Tenant tenant, URI requestURI, Cursor object) {
        object.setString("tenant", tenant.getId().id());
        Cursor metaData = object.setObject("metaData");
        metaData.setString("type", tenant.tenantType().name());
        tenant.getAthensDomain().ifPresent(a -> metaData.setString("athensDomain", a.id()));
        tenant.getProperty().ifPresent(p -> metaData.setString("property", p.id()));
        tenant.getUserGroup().ifPresent(g -> metaData.setString("userGroup", g.id()));
        object.setString("url", withPath("/application/v4/tenant/" + tenant.getId().id(), requestURI).toString());
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

    private void setRotationStatus(Deployment deployment, Map<String, RotationStatus> healthStatus, Cursor object) {
        if ( ! deployment.zone().environment().equals(Environment.prod)) return;

        Cursor bcpStatusObject = object.setObject("bcpStatus");
        bcpStatusObject.setString("rotationStatus", findRotationStatus(deployment, healthStatus).name());
    }

    private RotationStatus findRotationStatus(Deployment deployment, Map<String, RotationStatus> healthStatus) {
        for (String endpoint : healthStatus.keySet()) {
            if (endpoint.contains(toDns(deployment.zone().environment().value())) &&
                endpoint.contains(toDns(deployment.zone().region().value()))) {
                return healthStatus.getOrDefault(endpoint, RotationStatus.UNKNOWN);
            }
        }

        return RotationStatus.UNKNOWN;
    }

    private String toDns(String id) {
        return id.replace('_', '-');
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
        object.setString("version", jobRun.version().toFullString());
        jobRun.revision().ifPresent(revision -> toSlime(revision, object.setObject("revision")));
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

    private void throwIfNotSuperUserOrPartOfOpsDbGroup(UserGroup userGroup, HttpRequest request) {
        UserId userId = authorizer.getUserId(request);
        if (!authorizer.isSuperUser(request) && !authorizer.isGroupMember(userId, userGroup) ) {
            throw new ForbiddenException(String.format("User '%s' is not super user or part of the OpsDB user group '%s'",
                                                       userId.id(), userGroup.id()));
        }
    }

    private void throwIfNotAthenzDomainAdmin(AthenzDomain tenantDomain, HttpRequest request) {
        UserId userId = authorizer.getUserId(request);
        if ( ! authorizer.isAthenzDomainAdmin(userId, tenantDomain)) {
            throw new ForbiddenException(
                    String.format("The user '%s' is not admin in Athenz domain '%s'", userId.id(), tenantDomain.id()));
        }
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

    private Slime toSlime(Tenant tenant, HttpRequest request, boolean listApplications) {
        Slime slime = new Slime();
        toSlime(tenant, slime.setObject(), request, listApplications);
        return slime;
    }

    private void toSlime(Application application, Cursor object, HttpRequest request) {
        object.setString("application", application.id().application().value());
        object.setString("instance", application.id().instance().value());
        object.setString("url", withPath("/application/v4/tenant/" + application.id().tenant().value() +
                                         "/application/" + application.id().application().value(), request.getUri()).toString());
    }

    private Slime toSlime(ActivateResult result, long applicationZipSizeBytes) {
        Slime slime = new Slime();
        Cursor object = slime.setObject();
        object.setString("revisionId", result.getRevisionId().id());
        object.setLong("applicationZipSize", applicationZipSizeBytes);
        Cursor logArray = object.setArray("prepareMessages");
        if (result.getPrepareResponse().log != null) {
            for (Log logMessage : result.getPrepareResponse().log) {
                Cursor logObject = logArray.addObject();
                logObject.setLong("time", logMessage.time);
                logObject.setString("level", logMessage.level);
                logObject.setString("message", logMessage.message);
            }
        }

        Cursor changeObject = object.setObject("configChangeActions");

        Cursor restartActionsArray = changeObject.setArray("restart");
        for (RestartAction restartAction : result.getPrepareResponse().configChangeActions.restartActions) {
            Cursor restartActionObject = restartActionsArray.addObject();
            restartActionObject.setString("clusterName", restartAction.clusterName);
            restartActionObject.setString("clusterType", restartAction.clusterType);
            restartActionObject.setString("serviceType", restartAction.serviceType);
            serviceInfosToSlime(restartAction.services, restartActionObject.setArray("services"));
            stringsToSlime(restartAction.messages, restartActionObject.setArray("messages"));
        }

        Cursor refeedActionsArray = changeObject.setArray("refeed");
        for (RefeedAction refeedAction : result.getPrepareResponse().configChangeActions.refeedActions) {
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

    // TODO: get rid of the json object
    private Optional<ScrewdriverBuildJob> screwdriverBuildJobFromSlime(Inspector object) {
        if ( ! object.valid() ) return Optional.empty();
        Optional<ScrewdriverId> screwdriverId = optional("screwdriverId", object).map(ScrewdriverId::new);
        return Optional.of(new ScrewdriverBuildJob(screwdriverId.orElse(null),
                                                   gitRevisionFromSlime(object.field("gitRevision"))));
    }

    // TODO: get rid of the json object
    private GitRevision gitRevisionFromSlime(Inspector object) {
        return new GitRevision(optional("repository", object).map(GitRepository::new).orElse(null),
                               optional("branch", object).map(GitBranch::new).orElse(null),
                               optional("commit", object).map(GitCommit::new).orElse(null));
    }

    private String readToString(InputStream stream) {
        Scanner scanner = new Scanner(stream).useDelimiter("\\A");
        if ( ! scanner.hasNext()) return null;
        return scanner.next();
    }

    private boolean systemHasVersion(Version version) {
        return controller.versionStatus().versions().stream().anyMatch(v -> v.versionNumber().equals(version));
    }

    private Version decideDeployVersion(HttpRequest request) {
        String requestVersion = readToString(request.getData());
        if (requestVersion != null)
            return new Version(requestVersion);
        else
            return controller.systemVersion();
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
}
