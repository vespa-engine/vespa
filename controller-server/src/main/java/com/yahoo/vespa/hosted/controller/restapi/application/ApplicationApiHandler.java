// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import ai.vespa.hosted.api.Signatures;
import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;
import ai.vespa.http.HttpURL.Query;
import ai.vespa.validation.Validation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.handler.metrics.JsonResponse;
import com.yahoo.container.jdisc.EmptyResponse;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.http.filter.security.misc.User;
import com.yahoo.restapi.ByteArrayResponse;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.ResourceResponse;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.security.KeyUtils;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonParseException;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.Text;
import com.yahoo.vespa.athenz.api.OAuthCredentials;
import com.yahoo.vespa.athenz.client.zms.ZmsClientException;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.NotExistsException;
import com.yahoo.vespa.hosted.controller.api.application.v4.EnvironmentResource;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ProtonMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.ClusterId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.aws.TenantRoles;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.DeploymentResult;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.DeploymentResult.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Load;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.RestartFilter;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.RoleDefinition;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.application.AssignedRotation;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointList;
import com.yahoo.vespa.hosted.controller.application.QuotaUsage;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel;
import com.yahoo.vespa.hosted.controller.deployment.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.Submission;
import com.yahoo.vespa.hosted.controller.deployment.TestConfigSerializer;
import com.yahoo.vespa.hosted.controller.maintenance.ResourceMeterMaintainer;
import com.yahoo.vespa.hosted.controller.notification.Notification;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;
import com.yahoo.vespa.hosted.controller.persistence.SupportAccessSerializer;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;
import com.yahoo.vespa.hosted.controller.routing.context.DeploymentRoutingContext;
import com.yahoo.vespa.hosted.controller.routing.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.routing.rotation.RotationState;
import com.yahoo.vespa.hosted.controller.routing.rotation.RotationStatus;
import com.yahoo.vespa.hosted.controller.security.AccessControlRequests;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.support.access.SupportAccess;
import com.yahoo.vespa.hosted.controller.tenant.ArchiveAccess;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.DeletedTenant;
import com.yahoo.vespa.hosted.controller.tenant.Email;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;
import com.yahoo.vespa.hosted.controller.tenant.PendingMailVerification;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.TenantAddress;
import com.yahoo.vespa.hosted.controller.tenant.TenantBilling;
import com.yahoo.vespa.hosted.controller.tenant.TenantContact;
import com.yahoo.vespa.hosted.controller.tenant.TenantContacts;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfo;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.Principal;
import java.security.PublicKey;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Scanner;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.CONFLICT;
import static com.yahoo.yolean.Exceptions.uncheck;
import static java.util.Comparator.comparingInt;
import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;

/**
 * This implements the application/v4 API which is used to deploy and manage applications
 * on hosted Vespa.
 *
 * @author bratseth
 * @author mpolden
 */
@SuppressWarnings("unused") // created by injection
public class ApplicationApiHandler extends AuditLoggingRequestHandler {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private final Controller controller;
    private final AccessControlRequests accessControlRequests;
    private final TestConfigSerializer testConfigSerializer;

    @Inject
    public ApplicationApiHandler(ThreadedHttpRequestHandler.Context parentCtx,
                                 Controller controller,
                                 AccessControlRequests accessControlRequests) {
        super(parentCtx, controller.auditLogger());
        this.controller = controller;
        this.accessControlRequests = accessControlRequests;
        this.testConfigSerializer = new TestConfigSerializer(controller.system());
    }

    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(20); // deploys may take a long time;
    }

    @Override
    public HttpResponse auditAndHandle(HttpRequest request) {
        try {
            Path path = new Path(request.getUri());
            return switch (request.getMethod()) {
                case GET: yield handleGET(path, request);
                case PUT: yield handlePUT(path, request);
                case POST: yield handlePOST(path, request);
                case PATCH: yield handlePATCH(path, request);
                case DELETE: yield handleDELETE(path, request);
                case OPTIONS: yield handleOPTIONS();
                default: yield ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            };
        }
        catch (RestApiException.Forbidden e) {
            return ErrorResponse.forbidden(Exceptions.toMessageString(e));
        }
        catch (RestApiException.Unauthorized e) {
            return ErrorResponse.unauthorized(Exceptions.toMessageString(e));
        }
        catch (NotExistsException e) {
            return ErrorResponse.notFoundError(Exceptions.toMessageString(e));
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (ConfigServerException e) {
            return switch (e.code()) {
                case NOT_FOUND -> ErrorResponse.notFoundError(Exceptions.toMessageString(e));
                case ACTIVATION_CONFLICT -> new ErrorResponse(CONFLICT, e.code().name(), Exceptions.toMessageString(e));
                case INTERNAL_SERVER_ERROR -> ErrorResponses.logThrowing(request, log, e);
                default -> new ErrorResponse(BAD_REQUEST, e.code().name(), Exceptions.toMessageString(e));
            };
        }
        catch (RuntimeException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse handleGET(Path path, HttpRequest request) {
        if (path.matches("/application/v4/")) return root(request);
        if (path.matches("/application/v4/notifications")) return notifications(request, Optional.ofNullable(request.getProperty("tenant")), true);
        if (path.matches("/application/v4/tenant")) return tenants(request);
        if (path.matches("/application/v4/tenant/{tenant}")) return tenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/access/request/operator")) return accessRequests(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/info")) return tenantInfo(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/info/profile")) return withCloudTenant(path.get("tenant"), this::tenantInfoProfile);
        if (path.matches("/application/v4/tenant/{tenant}/info/billing")) return withCloudTenant(path.get("tenant"), this::tenantInfoBilling);
        if (path.matches("/application/v4/tenant/{tenant}/info/contacts")) return withCloudTenant(path.get("tenant"), this::tenantInfoContacts);
        if (path.matches("/application/v4/tenant/{tenant}/notifications")) return notifications(request, Optional.of(path.get("tenant")), false);
        if (path.matches("/application/v4/tenant/{tenant}/secret-store/{name}/validate")) return validateSecretStore(path.get("tenant"), path.get("name"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application")) return applications(path.get("tenant"), Optional.empty(), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}")) return application(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/compile-version")) return compileVersion(path.get("tenant"), path.get("application"), request.getProperty("allowMajor"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deployment")) return JobControllerApiHandlerHelper.overviewResponse(controller, TenantAndApplicationId.from(path.get("tenant"), path.get("application")), request.getUri());
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/package")) return applicationPackage(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/diff/{number}")) return applicationPackageDiff(path.get("tenant"), path.get("application"), path.get("number"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying")) return deploying(path.get("tenant"), path.get("application"), "default", request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying/pin")) return deploying(path.get("tenant"), path.get("application"), "default", request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance")) return applications(path.get("tenant"), Optional.of(path.get("application")), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}")) return instance(path.get("tenant"), path.get("application"), path.get("instance"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/deploying")) return deploying(path.get("tenant"), path.get("application"), path.get("instance"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/deploying/pin")) return deploying(path.get("tenant"), path.get("application"), path.get("instance"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job")) return JobControllerApiHandlerHelper.jobTypeResponse(controller, appIdFromPath(path), request.getUri());
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}")) return JobControllerApiHandlerHelper.runResponse(controller.applications().requireApplication(TenantAndApplicationId.from(path.get("tenant"), path.get("application"))), controller.jobController().runs(appIdFromPath(path), jobTypeFromPath(path)).descendingMap(), Optional.ofNullable(request.getProperty("limit")), request.getUri()); // (((＼（✘෴✘）／)))
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}/package")) return devApplicationPackage(appIdFromPath(path), jobTypeFromPath(path));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}/diff/{number}")) return devApplicationPackageDiff(runIdFromPath(path));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}/test-config")) return testConfig(appIdFromPath(path), jobTypeFromPath(path));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}/run/{number}")) return JobControllerApiHandlerHelper.runDetailsResponse(controller.jobController(), runIdFromPath(path), request.getProperty("after"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}/run/{number}/logs")) return JobControllerApiHandlerHelper.vespaLogsResponse(controller.jobController(), runIdFromPath(path), asLong(request.getProperty("from"), 0), request.getBooleanProperty("tester"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}")) return deployment(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/reindexing")) return getReindexing(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/suspended")) return suspended(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/service/{service}/{host}/status/{*}")) return status(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), path.get("service"), path.get("host"), path.getRest(), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/service/{service}/{host}/state/v1/{*}")) return stateV1(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), path.get("service"), path.get("host"), path.getRest(), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/orchestrator")) return orchestrator(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/nodes")) return nodes(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/clusters")) return clusters(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/content/{*}")) return content(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), path.getRest(), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/logs")) return logs(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request.propertyMap());
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/private-services")) return getPrivateServiceInfo(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/access/support")) return supportAccess(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request.propertyMap());
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/node/{node}/service-dump")) return getServiceDump(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), path.get("node"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/scaling")) return scaling(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/metrics")) return metrics(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/global-rotation")) return rotationStatus(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), Optional.ofNullable(request.getProperty("endpointId")));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/global-rotation/override")) return getGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}")) return deployment(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}")) return deployment(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/suspended")) return suspended(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/service/{service}/{host}/status/{*}")) return status(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), path.get("service"), path.get("host"), path.getRest(), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/nodes")) return nodes(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/clusters")) return clusters(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/logs")) return logs(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request.propertyMap());
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation")) return rotationStatus(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), Optional.ofNullable(request.getProperty("endpointId")));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation/override")) return getGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handlePUT(Path path, HttpRequest request) {
        if (path.matches("/application/v4/tenant/{tenant}")) return updateTenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/access/request/operator")) return requestSshAccess(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/access/approve/operator")) return approveAccessRequest(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/access/managed/operator")) return addManagedAccess(path.get("tenant"));
        if (path.matches("/application/v4/tenant/{tenant}/info")) return updateTenantInfo(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/info/profile")) return withCloudTenant(path.get("tenant"), request, this::putTenantInfoProfile);
        if (path.matches("/application/v4/tenant/{tenant}/info/billing")) return withCloudTenant(path.get("tenant"), request, this::putTenantInfoBilling);
        if (path.matches("/application/v4/tenant/{tenant}/info/contacts")) return withCloudTenant(path.get("tenant"), request, this::putTenantInfoContacts);
        if (path.matches("/application/v4/tenant/{tenant}/info/resend-mail-verification")) return withCloudTenant(path.get("tenant"), request, this::resendEmailVerification);
        if (path.matches("/application/v4/tenant/{tenant}/archive-access/aws")) return allowAwsArchiveAccess(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/archive-access/gcp")) return allowGcpArchiveAccess(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/secret-store/{name}")) return addSecretStore(path.get("tenant"), path.get("name"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/global-rotation/override")) return setGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), false, request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation/override")) return setGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), false, request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handlePOST(Path path, HttpRequest request) {
        if (path.matches("/application/v4/tenant/{tenant}")) return createTenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/key")) return addDeveloperKey(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}")) return createApplication(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying/platform")) return deployPlatform(path.get("tenant"), path.get("application"), "default", false, request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying/pin")) return deployPlatform(path.get("tenant"), path.get("application"), "default", true, request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying/application")) return deployApplication(path.get("tenant"), path.get("application"), "default", request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/key")) return addDeployKey(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/submit")) return submit(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}")) return createInstance(path.get("tenant"), path.get("application"), path.get("instance"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/deploy/{jobtype}")) return jobDeploy(appIdFromPath(path), jobTypeFromPath(path), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/deploying/platform")) return deployPlatform(path.get("tenant"), path.get("application"), path.get("instance"), false, request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/deploying/pin")) return deployPlatform(path.get("tenant"), path.get("application"), path.get("instance"), true, request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/deploying/application")) return deployApplication(path.get("tenant"), path.get("application"), path.get("instance"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/submit")) return submit(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}")) return trigger(appIdFromPath(path), jobTypeFromPath(path), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}/pause")) return pause(appIdFromPath(path), jobTypeFromPath(path));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}")) return deploySystemApplication(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/deploy")) return deploySystemApplication(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request); // legacy synonym of the above
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/reindex")) return reindex(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/reindexing")) return enableReindexing(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/restart")) return restart(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/suspend")) return suspend(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), true);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/access/support")) return allowSupportAccess(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/node/{node}/service-dump")) return requestServiceDump(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), path.get("node"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}")) return deploySystemApplication(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/deploy")) return deploySystemApplication(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request); // legacy synonym of the above
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/restart")) return restart(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handlePATCH(Path path, HttpRequest request) {
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}")) return patchApplication(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}")) return patchApplication(path.get("tenant"), path.get("application"), request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handleDELETE(Path path, HttpRequest request) {
        if (path.matches("/application/v4/tenant/{tenant}")) return deleteTenant(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/access/managed/operator")) return removeManagedAccess(path.get("tenant"));
        if (path.matches("/application/v4/tenant/{tenant}/key")) return removeDeveloperKey(path.get("tenant"), request);
        if (path.matches("/application/v4/tenant/{tenant}/archive-access/aws")) return removeAwsArchiveAccess(path.get("tenant"));
        if (path.matches("/application/v4/tenant/{tenant}/archive-access/gcp")) return removeGcpArchiveAccess(path.get("tenant"));
        if (path.matches("/application/v4/tenant/{tenant}/secret-store/{name}")) return deleteSecretStore(path.get("tenant"), path.get("name"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}")) return deleteApplication(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deployment")) return removeAllProdDeployments(path.get("tenant"), path.get("application"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying")) return cancelDeploy(path.get("tenant"), path.get("application"), "default", "all");
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying/{choice}")) return cancelDeploy(path.get("tenant"), path.get("application"), "default", path.get("choice"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/key")) return removeDeployKey(path.get("tenant"), path.get("application"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/submit/{build}")) return cancelBuild(path.get("tenant"), path.get("application"), path.get("build"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}")) return deleteInstance(path.get("tenant"), path.get("application"), path.get("instance"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/deploying")) return cancelDeploy(path.get("tenant"), path.get("application"), path.get("instance"), "all");
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/deploying/{choice}")) return cancelDeploy(path.get("tenant"), path.get("application"), path.get("instance"), path.get("choice"));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}")) return JobControllerApiHandlerHelper.abortJobResponse(controller.jobController(), request, appIdFromPath(path), jobTypeFromPath(path));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/job/{jobtype}/pause")) return resume(appIdFromPath(path), jobTypeFromPath(path));
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}")) return deactivate(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/reindexing")) return disableReindexing(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/suspend")) return suspend(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), false);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/global-rotation/override")) return setGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), true, request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/access/support")) return disallowSupportAccess(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}")) return deactivate(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), request);
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation/override")) return setGlobalRotationOverride(path.get("tenant"), path.get("application"), path.get("instance"), path.get("environment"), path.get("region"), true, request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handleOPTIONS() {
        // We implement this to avoid redirect loops on OPTIONS requests from browsers, but do not really bother
        // spelling out the methods supported at each path, which we should
        EmptyResponse response = new EmptyResponse();
        response.headers().put("Allow", "GET,PUT,POST,PATCH,DELETE,OPTIONS");
        return response;
    }

    private HttpResponse recursiveRoot(HttpRequest request) {
        Slime slime = new Slime();
        Cursor tenantArray = slime.setArray();
        List<Application> applications = controller.applications().asList();
        for (Tenant tenant : controller.tenants().asList(includeDeleted(request)))
            toSlime(tenantArray.addObject(),
                    tenant,
                    applications.stream().filter(app -> app.id().tenant().equals(tenant.name())).toList(),
                    request);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse root(HttpRequest request) {
        return recurseOverTenants(request)
                ? recursiveRoot(request)
                : new ResourceResponse(request, "tenant");
    }

    private HttpResponse tenants(HttpRequest request) {
        Slime slime = new Slime();
        Cursor response = slime.setArray();
        for (Tenant tenant : controller.tenants().asList(includeDeleted(request)))
            tenantInTenantsListToSlime(tenant, request.getUri(), response.addObject());
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse tenant(String tenantName, HttpRequest request) {
        return controller.tenants().get(TenantName.from(tenantName), includeDeleted(request))
                         .map(tenant -> tenant(tenant, request))
                         .orElseGet(() -> ErrorResponse.notFoundError("Tenant '" + tenantName + "' does not exist"));
    }

    private HttpResponse tenant(Tenant tenant, HttpRequest request) {
        Slime slime = new Slime();
        toSlime(slime.setObject(), tenant, controller.applications().asList(tenant.name()), request);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse accessRequests(String tenantName, HttpRequest request) {
        var tenant = TenantName.from(tenantName);
        if (controller.tenants().require(tenant).type() != Tenant.Type.cloud)
            return ErrorResponse.badRequest("Can only see access requests for cloud tenants");

        var accessControlService = controller.serviceRegistry().accessControlService();
        var slime = new Slime();
        var cursor = slime.setObject();
        try {
            var accessRoleInformation = accessControlService.getAccessRoleInformation(tenant);
            var managedAccess = accessControlService.getManagedAccess(tenant);
            cursor.setBool("managedAccess", managedAccess);
            accessRoleInformation.getPendingRequest()
                                 .ifPresent(membershipRequest -> {
                                     var requestCursor = cursor.setObject("pendingRequest");
                                     requestCursor.setString("requestTime", membershipRequest.getCreationTime());
                                     requestCursor.setString("reason", membershipRequest.getReason());
                                 });
            var auditLogCursor = cursor.setArray("auditLog");
            accessRoleInformation.getAuditLog()
                                 .forEach(auditLogEntry -> {
                                     var entryCursor = auditLogCursor.addObject();
                                     entryCursor.setString("created", auditLogEntry.getCreationTime());
                                     entryCursor.setString("approver", auditLogEntry.getApprover());
                                     entryCursor.setString("reason", auditLogEntry.getReason());
                                     entryCursor.setString("status", auditLogEntry.getAction());
                                 });
        }
        catch (ZmsClientException e) {
            if (e.getErrorCode() == 404) cursor.setBool("managedAccess", false);
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse requestSshAccess(String tenantName, HttpRequest request) {
        if (!isOperator(request)) {
            return ErrorResponse.forbidden("Only operators are allowed to request ssh access");
        }

        if (controller.tenants().require(TenantName.from(tenantName)).type() != Tenant.Type.cloud)
            return ErrorResponse.badRequest("Can only request access for cloud tenants");

        controller.serviceRegistry().accessControlService().requestSshAccess(TenantName.from(tenantName));
        return new MessageResponse("OK");

    }

    private HttpResponse approveAccessRequest(String tenantName, HttpRequest request) {
        var tenant = TenantName.from(tenantName);

        if (controller.tenants().require(tenant).type() != Tenant.Type.cloud)
            return ErrorResponse.badRequest("Can only see access requests for cloud tenants");

        var inspector = toSlime(request.getData()).get();
        var expiry = inspector.field("expiry").valid() ?
                Instant.ofEpochMilli(inspector.field("expiry").asLong()) :
                Instant.now().plus(1, ChronoUnit.DAYS);
        var approve = inspector.field("approve").asBool();

        controller.serviceRegistry().accessControlService().decideSshAccess(tenant, expiry, OAuthCredentials.fromAuth0RequestContext(request.getJDiscRequest().context()), approve);
        return new MessageResponse("OK");
    }

    private HttpResponse addManagedAccess(String tenantName) {
        return setManagedAccess(tenantName, true);
    }

    private HttpResponse removeManagedAccess(String tenantName) {
        return setManagedAccess(tenantName, false);
    }

    private HttpResponse setManagedAccess(String tenantName, boolean managedAccess) {
        var tenant = TenantName.from(tenantName);

        if (controller.tenants().require(tenant).type() != Tenant.Type.cloud)
            return ErrorResponse.badRequest("Can only set access privel for cloud tenants");

        try {
            controller.serviceRegistry().accessControlService().setManagedAccess(tenant, managedAccess);
            var slime = new Slime();
            slime.setObject().setBool("managedAccess", managedAccess);
            return new SlimeJsonResponse(slime);
        }
        catch (ZmsClientException e) {
            if (e.getErrorCode() == 404) return ErrorResponse.conflict("Configuration not yet ready, please try again in a few minutes");
            throw e;
        }
    }

    private HttpResponse tenantInfo(String tenantName, HttpRequest request) {
        return controller.tenants().get(TenantName.from(tenantName))
                .filter(tenant -> tenant.type() == Tenant.Type.cloud)
                .map(tenant -> tenantInfo(((CloudTenant)tenant).info(), request))
                .orElseGet(() -> ErrorResponse.notFoundError("Tenant '" + tenantName + "' does not exist or does not support this"));
    }

    private HttpResponse withCloudTenant(String tenantName, Function<CloudTenant, SlimeJsonResponse> handler) {
        return controller.tenants().get(TenantName.from(tenantName))
                .filter(tenant -> tenant.type() == Tenant.Type.cloud)
                .map(tenant -> handler.apply((CloudTenant) tenant))
                .orElseGet(() -> ErrorResponse.notFoundError("Tenant '" + tenantName + "' does not exist or does not support this"));
    }

    private SlimeJsonResponse tenantInfo(TenantInfo info, HttpRequest request) {
        Slime slime = new Slime();
        Cursor infoCursor = slime.setObject();
        if (!info.isEmpty()) {
            infoCursor.setString("name", info.name());
            infoCursor.setString("email", info.email());
            infoCursor.setString("website", info.website());
            infoCursor.setString("contactName", info.contact().name());
            infoCursor.setString("contactEmail", info.contact().email().getEmailAddress());
            infoCursor.setBool("contactEmailVerified", info.contact().email().isVerified());
            toSlime(info.address(), infoCursor);
            toSlime(info.billingContact(), infoCursor);
            toSlime(info.contacts(), infoCursor);
        }

        return new SlimeJsonResponse(slime);
    }

    private SlimeJsonResponse tenantInfoProfile(CloudTenant cloudTenant) {
        var slime = new Slime();
        var root = slime.setObject();
        var info = cloudTenant.info();

        if (!info.isEmpty()) {
            var contact = root.setObject("contact");
            contact.setString("name", info.contact().name());
            contact.setString("email", info.contact().email().getEmailAddress());
            contact.setBool("emailVerified", info.contact().email().isVerified());

            var tenant = root.setObject("tenant");
            tenant.setString("company", info.name());
            tenant.setString("website", info.website());

            toSlime(info.address(), root); // will create "address" on the parent
        }

        return new SlimeJsonResponse(slime);
    }

    private SlimeJsonResponse withCloudTenant(String tenantName, HttpRequest request, BiFunction<CloudTenant, Inspector, SlimeJsonResponse> handler) {
        return controller.tenants().get(tenantName)
                .map(tenant -> handler.apply((CloudTenant) tenant, toSlime(request.getData()).get()))
                .orElseGet(() -> ErrorResponse.notFoundError("Tenant '" + tenantName + "' does not exist or does not support this"));
    }

    private SlimeJsonResponse putTenantInfoProfile(CloudTenant cloudTenant, Inspector inspector) {
        var info = cloudTenant.info();

        var mergedEmail = optional("email", inspector.field("contact"))
                .filter(address -> !address.equals(info.contact().email().getEmailAddress()))
                .map(address -> {
                    controller.mailVerifier().sendMailVerification(cloudTenant.name(), address, PendingMailVerification.MailType.TENANT_CONTACT);
                    return new Email(address, false);
                })
                .orElse(info.contact().email());

        var mergedContact = TenantContact.empty()
                .withName(getString(inspector.field("contact").field("name"), info.contact().name()))
                .withEmail(mergedEmail);

        var mergedAddress = updateTenantInfoAddress(inspector.field("address"), info.address());

        var mergedInfo = info
                .withName(getString(inspector.field("tenant").field("name"), info.name()))
                .withWebsite(getString(inspector.field("tenant").field("website"), info.website()))
                .withContact(mergedContact)
                .withAddress(mergedAddress);

        validateMergedTenantInfo(mergedInfo);

        controller.tenants().lockOrThrow(cloudTenant.name(), LockedTenant.Cloud.class, lockedTenant -> {
            lockedTenant = lockedTenant.withInfo(mergedInfo);
            controller.tenants().store(lockedTenant);
        });

        return new MessageResponse("Tenant info updated");
    }

    private SlimeJsonResponse tenantInfoBilling(CloudTenant cloudTenant) {
        var slime = new Slime();
        var root = slime.setObject();
        var info = cloudTenant.info();

        if (!info.isEmpty()) {
            var billingContact = info.billingContact();

            var contact = root.setObject("contact");
            contact.setString("name", billingContact.contact().name());
            contact.setString("email", billingContact.contact().email().getEmailAddress());
            contact.setString("phone", billingContact.contact().phone());

            toSlime(billingContact.address(), root); // will create "address" on the parent
        }

        return new SlimeJsonResponse(slime);
    }

    private SlimeJsonResponse putTenantInfoBilling(CloudTenant cloudTenant, Inspector inspector) {
        var info = cloudTenant.info();
        var contact = info.billingContact().contact();
        var address = info.billingContact().address();

        var mergedContact = updateTenantInfoContact(inspector.field("contact"), cloudTenant.name(), contact, false);
        var mergedAddress = updateTenantInfoAddress(inspector.field("address"), info.billingContact().address());

        var mergedBilling = info.billingContact()
                .withContact(mergedContact)
                .withAddress(mergedAddress);

        var mergedInfo = info.withBilling(mergedBilling);

        // Store changes
        controller.tenants().lockOrThrow(cloudTenant.name(), LockedTenant.Cloud.class, lockedTenant -> {
            lockedTenant = lockedTenant.withInfo(mergedInfo);
            controller.tenants().store(lockedTenant);
        });

        return new MessageResponse("Tenant info updated");
    }

    private SlimeJsonResponse tenantInfoContacts(CloudTenant cloudTenant) {
        var slime = new Slime();
        var root = slime.setObject();
        toSlime(cloudTenant.info().contacts(), root);
        return new SlimeJsonResponse(slime);
    }

    private SlimeJsonResponse putTenantInfoContacts(CloudTenant cloudTenant, Inspector inspector) {
        var mergedInfo = cloudTenant.info()
                .withContacts(updateTenantInfoContacts(inspector.field("contacts"), cloudTenant.name(), cloudTenant.info().contacts()));

        // Store changes
        controller.tenants().lockOrThrow(cloudTenant.name(), LockedTenant.Cloud.class, lockedTenant -> {
            lockedTenant = lockedTenant.withInfo(mergedInfo);
            controller.tenants().store(lockedTenant);
        });

        return new MessageResponse("Tenant info updated");
    }

    private void validateMergedTenantInfo(TenantInfo mergedInfo) {
        // Assert that we have a valid tenant info
        if (mergedInfo.contact().name().isBlank()) {
            throw new IllegalArgumentException("'contactName' cannot be empty");
        }
        if (mergedInfo.contact().email().getEmailAddress().isBlank()) {
            throw new IllegalArgumentException("'contactEmail' cannot be empty");
        }
        if (! mergedInfo.contact().email().getEmailAddress().contains("@")) {
            // email address validation is notoriously hard - we should probably just try to send a
            // verification email to this address.  checking for @ is a simple best-effort.
            throw new IllegalArgumentException("'contactEmail' needs to be an email address");
        }
        if (! mergedInfo.website().isBlank()) {
            try {
                new URL(mergedInfo.website());
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("'website' needs to be a valid address");
            }
        }
    }

    private void toSlime(TenantAddress address, Cursor parentCursor) {
        if (address.isEmpty()) return;

        Cursor addressCursor = parentCursor.setObject("address");
        addressCursor.setString("addressLines", address.address());
        addressCursor.setString("postalCodeOrZip", address.code());
        addressCursor.setString("city", address.city());
        addressCursor.setString("stateRegionProvince", address.region());
        addressCursor.setString("country", address.country());
    }

    private void toSlime(TenantBilling billingContact, Cursor parentCursor) {
        if (billingContact.isEmpty()) return;

        Cursor addressCursor = parentCursor.setObject("billingContact");
        addressCursor.setString("name", billingContact.contact().name());
        addressCursor.setString("email", billingContact.contact().email().getEmailAddress());
        addressCursor.setString("phone", billingContact.contact().phone());
        toSlime(billingContact.address(), addressCursor);
    }

    private void toSlime(TenantContacts contacts, Cursor parentCursor) {
        Cursor contactsCursor = parentCursor.setArray("contacts");
        contacts.all().forEach(contact -> {
            Cursor contactCursor = contactsCursor.addObject();
            Cursor audiencesArray = contactCursor.setArray("audiences");
            contact.audiences().forEach(audience -> audiencesArray.addString(toAudience(audience)));
            switch (contact.type()) {
                case EMAIL:
                    var email = (TenantContacts.EmailContact) contact;
                    contactCursor.setString("email", email.email().getEmailAddress());
                    contactCursor.setBool("emailVerified", email.email().isVerified());
                    return;
                default:
                    throw new IllegalArgumentException("Serialization for contact type not implemented: " + contact.type());
            }
        });
    }

    private static TenantContacts.Audience fromAudience(String value) {
        return switch (value) {
            case "tenant":  yield TenantContacts.Audience.TENANT;
            case "notifications":  yield TenantContacts.Audience.NOTIFICATIONS;
            default: throw new IllegalArgumentException("Unknown contact audience '" + value + "'.");
        };
    }

    private static String toAudience(TenantContacts.Audience audience) {
        return switch (audience) {
            case TENANT: yield "tenant";
            case NOTIFICATIONS: yield "notifications";
        };
    }


    private HttpResponse updateTenantInfo(String tenantName, HttpRequest request) {
        return controller.tenants().get(TenantName.from(tenantName))
                .filter(tenant -> tenant.type() == Tenant.Type.cloud)
                .map(tenant -> updateTenantInfo(((CloudTenant)tenant), request))
                .orElseGet(() -> ErrorResponse.notFoundError("Tenant '" + tenantName + "' does not exist or does not support this"));
    }

    private String getString(Inspector field, String defaultVale) {
        return field.valid() ? field.asString().trim() : defaultVale;
    }

    private SlimeJsonResponse updateTenantInfo(CloudTenant tenant, HttpRequest request) {
        TenantInfo oldInfo = tenant.info();

        // Merge info from request with the existing info
        Inspector insp = toSlime(request.getData()).get();

        var mergedEmail = optional("contactEmail", insp)
                .filter(address -> !address.equals(oldInfo.contact().email().getEmailAddress()))
                .map(address -> {
                    controller.mailVerifier().sendMailVerification(tenant.name(), address, PendingMailVerification.MailType.TENANT_CONTACT);
                    return new Email(address, false);
                })
                .orElse(oldInfo.contact().email());

        TenantContact mergedContact = TenantContact.empty()
                .withName(getString(insp.field("contactName"), oldInfo.contact().name()))
                .withEmail(mergedEmail);

        TenantInfo mergedInfo = TenantInfo.empty()
                .withName(getString(insp.field("name"), oldInfo.name()))
                .withEmail(getString(insp.field("email"), oldInfo.email()))
                .withWebsite(getString(insp.field("website"), oldInfo.website()))
                .withContact(mergedContact)
                .withAddress(updateTenantInfoAddress(insp.field("address"), oldInfo.address()))
                .withBilling(updateTenantInfoBillingContact(insp.field("billingContact"), tenant.name(), oldInfo.billingContact()))
                .withContacts(updateTenantInfoContacts(insp.field("contacts"), tenant.name(), oldInfo.contacts()));

        validateMergedTenantInfo(mergedInfo);

        // Store changes
        controller.tenants().lockOrThrow(tenant.name(), LockedTenant.Cloud.class, lockedTenant -> {
            lockedTenant = lockedTenant.withInfo(mergedInfo);
            controller.tenants().store(lockedTenant);
        });

        return new MessageResponse("Tenant info updated");
    }

    private TenantAddress updateTenantInfoAddress(Inspector insp, TenantAddress oldAddress) {
        if (!insp.valid()) return oldAddress;
        TenantAddress address = TenantAddress.empty()
                .withCountry(getString(insp.field("country"), oldAddress.country()))
                .withRegion(getString(insp.field("stateRegionProvince"), oldAddress.region()))
                .withCity(getString(insp.field("city"), oldAddress.city()))
                .withCode(getString(insp.field("postalCodeOrZip"), oldAddress.code()))
                .withAddress(getString(insp.field("addressLines"), oldAddress.address()));

        List<String> fields = List.of(address.address(),
                        address.code(),
                        address.country(),
                        address.city(),
                        address.region());

        if (fields.stream().allMatch(String::isBlank) || fields.stream().noneMatch(String::isBlank))
            return address;

        throw new IllegalArgumentException("All address fields must be set");
    }

    private TenantContact updateTenantInfoContact(Inspector insp, TenantName tenantName, TenantContact oldContact, boolean isBillingContact) {
        if (!insp.valid()) return oldContact;

        var mergedEmail = optional("email", insp)
                .filter(address -> !address.equals(oldContact.email().getEmailAddress()))
                .map(address -> {
                    if (isBillingContact)
                        return new Email(address, true);
                    controller.mailVerifier().sendMailVerification(tenantName, address, PendingMailVerification.MailType.TENANT_CONTACT);
                    return new Email(address, false);
                })
                .orElse(oldContact.email());

        return TenantContact.empty()
                .withName(getString(insp.field("name"), oldContact.name()))
                .withEmail(mergedEmail)
                .withPhone(getString(insp.field("phone"), oldContact.phone()));
    }

    private TenantBilling updateTenantInfoBillingContact(Inspector insp, TenantName tenantName, TenantBilling oldContact) {
        if (!insp.valid()) return oldContact;

        return TenantBilling.empty()
                .withContact(updateTenantInfoContact(insp, tenantName, oldContact.contact(), true))
                .withAddress(updateTenantInfoAddress(insp.field("address"), oldContact.address()));
    }

    private TenantContacts updateTenantInfoContacts(Inspector insp, TenantName tenantName, TenantContacts oldContacts) {
        if (!insp.valid()) return oldContacts;

        List<TenantContacts.EmailContact> contacts = SlimeUtils.entriesStream(insp).map(inspector -> {
                String email = inspector.field("email").asString().trim();
                List<TenantContacts.Audience> audiences = SlimeUtils.entriesStream(inspector.field("audiences"))
                            .map(audience -> fromAudience(audience.asString()))
                            .toList();

                // If contact exists, update audience. Otherwise, create new unverified contact
                return oldContacts.ofType(TenantContacts.EmailContact.class)
                        .stream()
                        .filter(contact -> contact.email().getEmailAddress().equals(email))
                        .findAny()
                        .map(emailContact -> new TenantContacts.EmailContact(audiences, emailContact.email()))
                        .orElseGet(() -> {
                            controller.mailVerifier().sendMailVerification(tenantName, email, PendingMailVerification.MailType.NOTIFICATIONS);
                            return new TenantContacts.EmailContact(audiences, new Email(email, false));
                        });
            }).toList();

        return new TenantContacts(contacts);
    }

    private HttpResponse notifications(HttpRequest request, Optional<String> tenant, boolean includeTenantFieldInResponse) {
        boolean productionOnly = showOnlyProductionInstances(request);
        boolean excludeMessages = "true".equals(request.getProperty("excludeMessages"));
        Slime slime = new Slime();
        Cursor notificationsArray = slime.setObject().setArray("notifications");

        tenant.map(t -> Stream.of(TenantName.from(t)))
                .orElseGet(() -> controller.notificationsDb().listTenantsWithNotifications().stream())
                .flatMap(tenantName -> controller.notificationsDb().listNotifications(NotificationSource.from(tenantName), productionOnly).stream())
                .filter(notification ->
                        propertyEquals(request, "application", ApplicationName::from, notification.source().application()) &&
                        propertyEquals(request, "instance", InstanceName::from, notification.source().instance()) &&
                        propertyEquals(request, "zone", ZoneId::from, notification.source().zoneId()) &&
                        propertyEquals(request, "job", job -> JobType.fromJobName(job, controller.zoneRegistry()), notification.source().jobType()) &&
                        propertyEquals(request, "type", Notification.Type::valueOf, Optional.of(notification.type())) &&
                        propertyEquals(request, "level", Notification.Level::valueOf, Optional.of(notification.level())))
                .forEach(notification -> toSlime(notificationsArray.addObject(), notification, includeTenantFieldInResponse, excludeMessages));
        return new SlimeJsonResponse(slime);
    }

    private static <T> boolean propertyEquals(HttpRequest request, String property, Function<String, T> mapper, Optional<T> value) {
        return Optional.ofNullable(request.getProperty(property))
                .map(propertyValue -> value.isPresent() && mapper.apply(propertyValue).equals(value.get()))
                .orElse(true);
    }

    private static void toSlime(Cursor cursor, Notification notification, boolean includeTenantFieldInResponse, boolean excludeMessages) {
        cursor.setLong("at", notification.at().toEpochMilli());
        cursor.setString("level", notificationLevelAsString(notification.level()));
        cursor.setString("type", notificationTypeAsString(notification.type()));
        if (!excludeMessages) {
            Cursor messagesArray = cursor.setArray("messages");
            notification.messages().forEach(messagesArray::addString);
        }

        if (includeTenantFieldInResponse) cursor.setString("tenant", notification.source().tenant().value());
        notification.source().application().ifPresent(application -> cursor.setString("application", application.value()));
        notification.source().instance().ifPresent(instance -> cursor.setString("instance", instance.value()));
        notification.source().zoneId().ifPresent(zoneId -> {
            cursor.setString("environment", zoneId.environment().value());
            cursor.setString("region", zoneId.region().value());
        });
        notification.source().clusterId().ifPresent(clusterId -> cursor.setString("clusterId", clusterId.value()));
        notification.source().jobType().ifPresent(jobType -> cursor.setString("jobName", jobType.jobName()));
        notification.source().runNumber().ifPresent(runNumber -> cursor.setLong("runNumber", runNumber));
    }

    private static String notificationTypeAsString(Notification.Type type) {
        return switch (type) {
            case submission, applicationPackage: yield "applicationPackage";
            case testPackage: yield "testPackage";
            case deployment: yield "deployment";
            case feedBlock: yield "feedBlock";
            case reindex: yield "reindex";
        };
    }

    private static String notificationLevelAsString(Notification.Level level) {
        return switch (level) {
            case info: yield "info";
            case warning: yield "warning";
            case error: yield "error";
        };
    }

    private HttpResponse applications(String tenantName, Optional<String> applicationName, HttpRequest request) {
        TenantName tenant = TenantName.from(tenantName);
        getTenantOrThrow(tenantName);

        List<Application> applications = applicationName.isEmpty() ?
                controller.applications().asList(tenant) :
                controller.applications().getApplication(TenantAndApplicationId.from(tenantName, applicationName.get()))
                    .map(List::of)
                    .orElseThrow(() -> new NotExistsException("Application '" + applicationName.get() + "' does not exist"));

        Slime slime = new Slime();
        Cursor applicationArray = slime.setArray();
        for (Application application : applications) {
            Cursor applicationObject = applicationArray.addObject();
            applicationObject.setString("tenant", application.id().tenant().value());
            applicationObject.setString("application", application.id().application().value());
            applicationObject.setString("url", withPath("/application/v4" +
                                                        "/tenant/" + application.id().tenant().value() +
                                                        "/application/" + application.id().application().value(),
                                                        request.getUri()).toString());
            Cursor instanceArray = applicationObject.setArray("instances");
            for (InstanceName instance : showOnlyProductionInstances(request) ? application.productionInstances().keySet()
                                                                              : application.instances().keySet()) {
                Cursor instanceObject = instanceArray.addObject();
                instanceObject.setString("instance", instance.value());
                instanceObject.setString("url", withPath("/application/v4" +
                                                         "/tenant/" + application.id().tenant().value() +
                                                         "/application/" + application.id().application().value() +
                                                         "/instance/" + instance.value(),
                                                         request.getUri()).toString());
            }
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse devApplicationPackage(ApplicationId id, JobType type) {
        ZoneId zone = type.zone();
        RevisionId revision = controller.jobController().last(id, type).get().versions().targetRevision();
        byte[] applicationPackage = controller.applications().applicationStore().get(new DeploymentId(id, zone), revision);
        return new ZipResponse(id.toFullString() + "." + zone.value() + ".zip", applicationPackage);
    }

    private HttpResponse devApplicationPackageDiff(RunId runId) {
        DeploymentId deploymentId = new DeploymentId(runId.application(), runId.job().type().zone());
        return controller.applications().applicationStore().getDevDiff(deploymentId, runId.number())
                .map(ByteArrayResponse::new)
                .orElseThrow(() -> new NotExistsException("No application package diff found for " + runId));
    }

    private HttpResponse applicationPackage(String tenantName, String applicationName, HttpRequest request) {
        TenantAndApplicationId tenantAndApplication = TenantAndApplicationId.from(tenantName, applicationName);
        final long build;
        String requestedBuild = request.getProperty("build");
        if (requestedBuild != null) {
            if (requestedBuild.equals("latestDeployed")) {
                build = controller.applications().requireApplication(tenantAndApplication).latestDeployedRevision()
                                  .map(RevisionId::number)
                                  .orElseThrow(() -> new NotExistsException("no application package has been deployed in production for " + tenantAndApplication));
            } else {
                try {
                    build = Validation.requireAtLeast(Long.parseLong(request.getProperty("build")), "build number", 1L);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("invalid value for request parameter 'build'", e);
                }
            }
        } else {
            build = controller.applications().requireApplication(tenantAndApplication).revisions().last()
                              .map(version -> version.id().number())
                              .orElseThrow(() -> new NotExistsException("no application package has been submitted for " + tenantAndApplication));
        }
        RevisionId revision = RevisionId.forProduction(build);
        boolean tests = request.getBooleanProperty("tests");
        byte[] applicationPackage = tests ?
                controller.applications().applicationStore().getTester(tenantAndApplication.tenant(), tenantAndApplication.application(), revision) :
                controller.applications().applicationStore().get(new DeploymentId(tenantAndApplication.defaultInstance(), ZoneId.defaultId()), revision);
        String filename = tenantAndApplication + (tests ? "-tests" : "-build") + revision.number() + ".zip";
        return new ZipResponse(filename, applicationPackage);
    }

    private HttpResponse applicationPackageDiff(String tenant, String application, String number) {
        TenantAndApplicationId tenantAndApplication = TenantAndApplicationId.from(tenant, application);
        return controller.applications().applicationStore().getDiff(tenantAndApplication.tenant(), tenantAndApplication.application(), Long.parseLong(number))
                .map(ByteArrayResponse::new)
                .orElseThrow(() -> new NotExistsException("No application package diff found for '" + tenantAndApplication + "' with build number " + number));
    }

    private HttpResponse application(String tenantName, String applicationName, HttpRequest request) {
        Slime slime = new Slime();
        toSlime(slime.setObject(), getApplication(tenantName, applicationName), request);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse compileVersion(String tenantName, String applicationName, String allowMajorParam) {
        Slime slime = new Slime();
        OptionalInt allowMajor = OptionalInt.empty();
        if (allowMajorParam != null) {
            try {
                allowMajor = OptionalInt.of(Integer.parseInt(allowMajorParam));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid major version '" + allowMajorParam + "'", e);
            }
        }
        Version compileVersion = controller.applications().compileVersion(TenantAndApplicationId.from(tenantName, applicationName), allowMajor);
        slime.setObject().setString("compileVersion", compileVersion.toFullString());
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse instance(String tenantName, String applicationName, String instanceName, HttpRequest request) {
        Slime slime = new Slime();
        toSlime(slime.setObject(), getInstance(tenantName, applicationName, instanceName),
                controller.jobController().deploymentStatus(getApplication(tenantName, applicationName)), request);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse addDeveloperKey(String tenantName, HttpRequest request) {
        if (controller.tenants().require(TenantName.from(tenantName)).type() != Tenant.Type.cloud)
            throw new IllegalArgumentException("Tenant '" + tenantName + "' is not a cloud tenant");

        Principal user = request.getJDiscRequest().getUserPrincipal();
        String pemDeveloperKey = toSlime(request.getData()).get().field("key").asString();
        PublicKey developerKey = KeyUtils.fromPemEncodedPublicKey(pemDeveloperKey);
        Slime root = new Slime();
        controller.tenants().lockOrThrow(TenantName.from(tenantName), LockedTenant.Cloud.class, tenant -> {
            tenant = tenant.withDeveloperKey(developerKey, user);
            toSlime(root.setObject().setArray("keys"), tenant.get().developerKeys());
            controller.tenants().store(tenant);
        });
        return new SlimeJsonResponse(root);
    }

    private HttpResponse validateSecretStore(String tenantName, String secretStoreName, HttpRequest request) {
        var awsRegion = request.getProperty("aws-region");
        var parameterName = request.getProperty("parameter-name");
        var applicationId = ApplicationId.fromFullString(request.getProperty("application-id"));
        if (!applicationId.tenant().equals(TenantName.from(tenantName)))
            return ErrorResponse.badRequest("Invalid application id");
        var zoneId = requireZone(ZoneId.from(request.getProperty("zone")));
        var deploymentId = new DeploymentId(applicationId, zoneId);

        var tenant = controller.tenants().require(applicationId.tenant(), CloudTenant.class);

        var tenantSecretStore = tenant.tenantSecretStores()
                .stream()
                .filter(secretStore -> secretStore.getName().equals(secretStoreName))
                .findFirst();

        if (tenantSecretStore.isEmpty())
            return ErrorResponse.notFoundError("No secret store '" + secretStoreName + "' configured for tenant '" + tenantName + "'");

        var response = controller.serviceRegistry().configServer().validateSecretStore(deploymentId, tenantSecretStore.get(), awsRegion, parameterName);
        try {
            var responseRoot = new Slime();
            var responseCursor = responseRoot.setObject();
            responseCursor.setString("target", deploymentId.toString());
            var responseResultCursor = responseCursor.setObject("result");
            var responseSlime = SlimeUtils.jsonToSlime(response);
            SlimeUtils.copyObject(responseSlime.get(), responseResultCursor);
            return new SlimeJsonResponse(responseRoot);
        } catch (JsonParseException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse removeDeveloperKey(String tenantName, HttpRequest request) {
        if (controller.tenants().require(TenantName.from(tenantName)).type() != Tenant.Type.cloud)
            throw new IllegalArgumentException("Tenant '" + tenantName + "' is not a cloud tenant");

        String pemDeveloperKey = toSlime(request.getData()).get().field("key").asString();
        PublicKey developerKey = KeyUtils.fromPemEncodedPublicKey(pemDeveloperKey);
        Principal user = controller.tenants().require(TenantName.from(tenantName), CloudTenant.class).developerKeys().get(developerKey);
        Slime root = new Slime();
        controller.tenants().lockOrThrow(TenantName.from(tenantName), LockedTenant.Cloud.class, tenant -> {
            tenant = tenant.withoutDeveloperKey(developerKey);
            toSlime(root.setObject().setArray("keys"), tenant.get().developerKeys());
            controller.tenants().store(tenant);
        });
        return new SlimeJsonResponse(root);
    }

    private void toSlime(Cursor keysArray, Map<PublicKey, ? extends Principal> keys) {
        keys.forEach((key, principal) -> {
            Cursor keyObject = keysArray.addObject();
            keyObject.setString("key", KeyUtils.toPem(key));
            keyObject.setString("user", principal.getName());
        });
    }

    private HttpResponse addDeployKey(String tenantName, String applicationName, HttpRequest request) {
        String pemDeployKey = toSlime(request.getData()).get().field("key").asString();
        PublicKey deployKey = KeyUtils.fromPemEncodedPublicKey(pemDeployKey);
        Slime root = new Slime();
        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(tenantName, applicationName), application -> {
            application = application.withDeployKey(deployKey);
            application.get().deployKeys().stream()
                       .map(KeyUtils::toPem)
                       .forEach(root.setObject().setArray("keys")::addString);
            controller.applications().store(application);
        });
        return new SlimeJsonResponse(root);
    }

    private HttpResponse removeDeployKey(String tenantName, String applicationName, HttpRequest request) {
        String pemDeployKey = toSlime(request.getData()).get().field("key").asString();
        PublicKey deployKey = KeyUtils.fromPemEncodedPublicKey(pemDeployKey);
        Slime root = new Slime();
        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(tenantName, applicationName), application -> {
            application = application.withoutDeployKey(deployKey);
            application.get().deployKeys().stream()
                       .map(KeyUtils::toPem)
                       .forEach(root.setObject().setArray("keys")::addString);
            controller.applications().store(application);
        });
        return new SlimeJsonResponse(root);
    }

    private HttpResponse addSecretStore(String tenantName, String name, HttpRequest request) {
        if (controller.tenants().require(TenantName.from(tenantName)).type() != Tenant.Type.cloud)
            throw new IllegalArgumentException("Tenant '" + tenantName + "' is not a cloud tenant");

        var data = toSlime(request.getData()).get();
        var awsId = mandatory("awsId", data).asString();
        var externalId = mandatory("externalId", data).asString();
        var role = mandatory("role", data).asString();

        var tenant = controller.tenants().require(TenantName.from(tenantName), CloudTenant.class);
        var tenantSecretStore = new TenantSecretStore(name, awsId, role);

        if (!tenantSecretStore.isValid()) {
            return ErrorResponse.badRequest("Secret store " + tenantSecretStore + " is invalid");
        }
        if (tenant.tenantSecretStores().contains(tenantSecretStore)) {
            return ErrorResponse.badRequest("Secret store " + tenantSecretStore + " is already configured");
        }

        controller.serviceRegistry().roleService().createTenantPolicy(TenantName.from(tenantName), name, awsId, role);
        controller.serviceRegistry().tenantSecretService().addSecretStore(tenant.name(), tenantSecretStore, externalId);
        // Store changes
        controller.tenants().lockOrThrow(tenant.name(), LockedTenant.Cloud.class, lockedTenant -> {
            lockedTenant = lockedTenant.withSecretStore(tenantSecretStore);
            controller.tenants().store(lockedTenant);
        });

        tenant = controller.tenants().require(TenantName.from(tenantName), CloudTenant.class);
        var slime = new Slime();
        toSlime(slime.setObject(), tenant.tenantSecretStores());
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse deleteSecretStore(String tenantName, String name, HttpRequest request) {
        var tenant = controller.tenants().require(TenantName.from(tenantName), CloudTenant.class);

        var optionalSecretStore = tenant.tenantSecretStores().stream()
                .filter(secretStore -> secretStore.getName().equals(name))
                .findFirst();

        if (optionalSecretStore.isEmpty())
            return ErrorResponse.notFoundError("Could not delete secret store '" + name + "': Secret store not found");

        var tenantSecretStore = optionalSecretStore.get();
        controller.serviceRegistry().tenantSecretService().deleteSecretStore(tenant.name(), tenantSecretStore);
        controller.serviceRegistry().roleService().deleteTenantPolicy(tenant.name(), tenantSecretStore.getName(), tenantSecretStore.getRole());
        controller.tenants().lockOrThrow(tenant.name(), LockedTenant.Cloud.class, lockedTenant -> {
            lockedTenant = lockedTenant.withoutSecretStore(tenantSecretStore);
            controller.tenants().store(lockedTenant);
        });

        tenant = controller.tenants().require(TenantName.from(tenantName), CloudTenant.class);
        var slime = new Slime();
        toSlime(slime.setObject(), tenant.tenantSecretStores());
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse allowAwsArchiveAccess(String tenantName, HttpRequest request) {
        if (controller.tenants().require(TenantName.from(tenantName)).type() != Tenant.Type.cloud)
            throw new IllegalArgumentException("Tenant '" + tenantName + "' is not a cloud tenant");

        var data = toSlime(request.getData()).get();
        var role = mandatory("role", data).asString();

        if (role.isBlank()) {
            return ErrorResponse.badRequest("AWS archive access role can't be whitespace only");
        }

        controller.tenants().lockOrThrow(TenantName.from(tenantName), LockedTenant.Cloud.class, lockedTenant -> {
            var access = lockedTenant.get().archiveAccess();
            lockedTenant = lockedTenant.withArchiveAccess(access.withAWSRole(role));
            controller.tenants().store(lockedTenant);
        });

        return new MessageResponse("AWS archive access role set to '" + role + "' for tenant " + tenantName +  ".");
    }

    private HttpResponse removeAwsArchiveAccess(String tenantName) {
        if (controller.tenants().require(TenantName.from(tenantName)).type() != Tenant.Type.cloud)
            throw new IllegalArgumentException("Tenant '" + tenantName + "' is not a cloud tenant");

        controller.tenants().lockOrThrow(TenantName.from(tenantName), LockedTenant.Cloud.class, lockedTenant -> {
            var access = lockedTenant.get().archiveAccess();
            lockedTenant = lockedTenant.withArchiveAccess(access.removeAWSRole());
            controller.tenants().store(lockedTenant);
        });

        return new MessageResponse("AWS archive access role removed for tenant " + tenantName + ".");
    }

    private HttpResponse allowGcpArchiveAccess(String tenantName, HttpRequest request) {
        if (controller.tenants().require(TenantName.from(tenantName)).type() != Tenant.Type.cloud)
            throw new IllegalArgumentException("Tenant '" + tenantName + "' is not a cloud tenant");

        var data = toSlime(request.getData()).get();
        var member = mandatory("member", data).asString();

        if (member.isBlank()) {
            return ErrorResponse.badRequest("GCP archive access role can't be whitespace only");
        }

        controller.tenants().lockOrThrow(TenantName.from(tenantName), LockedTenant.Cloud.class, lockedTenant -> {
            var access = lockedTenant.get().archiveAccess();
            lockedTenant = lockedTenant.withArchiveAccess(access.withGCPMember(member));
            controller.tenants().store(lockedTenant);
        });

        return new MessageResponse("GCP archive access member set to '" + member + "' for tenant " + tenantName +  ".");
    }

    private HttpResponse removeGcpArchiveAccess(String tenantName) {
        if (controller.tenants().require(TenantName.from(tenantName)).type() != Tenant.Type.cloud)
            throw new IllegalArgumentException("Tenant '" + tenantName + "' is not a cloud tenant");

        controller.tenants().lockOrThrow(TenantName.from(tenantName), LockedTenant.Cloud.class, lockedTenant -> {
            var access = lockedTenant.get().archiveAccess();
            lockedTenant = lockedTenant.withArchiveAccess(access.removeGCPMember());
            controller.tenants().store(lockedTenant);
        });

        return new MessageResponse("GCP archive access member removed for tenant " + tenantName + ".");
    }

    private HttpResponse patchApplication(String tenantName, String applicationName, HttpRequest request) {
        Inspector requestObject = toSlime(request.getData()).get();
        StringJoiner messageBuilder = new StringJoiner("\n").setEmptyValue("No applicable changes.");
        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(tenantName, applicationName), application -> {
            Inspector majorVersionField = requestObject.field("majorVersion");
            if (majorVersionField.valid()) {
                Integer majorVersion = majorVersionField.asLong() == 0 ? null : (int) majorVersionField.asLong();
                application = application.withMajorVersion(majorVersion);
                messageBuilder.add("Set major version to " + (majorVersion == null ? "empty" : majorVersion));
            }

            // TODO jonmv: Remove when clients are updated.
            Inspector pemDeployKeyField = requestObject.field("pemDeployKey");
            if (pemDeployKeyField.valid()) {
                String pemDeployKey = pemDeployKeyField.asString();
                PublicKey deployKey = KeyUtils.fromPemEncodedPublicKey(pemDeployKey);
                application = application.withDeployKey(deployKey);
                messageBuilder.add("Added deploy key " + pemDeployKey);
            }

            controller.applications().store(application);
        });
        return new MessageResponse(messageBuilder.toString());
    }

    private Application getApplication(String tenantName, String applicationName) {
        TenantAndApplicationId applicationId = TenantAndApplicationId.from(tenantName, applicationName);
        return controller.applications().getApplication(applicationId)
                         .orElseThrow(() -> new NotExistsException(applicationId + " not found"));
    }

    private Instance getInstance(String tenantName, String applicationName, String instanceName) {
        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, instanceName);
        return controller.applications().getInstance(applicationId)
                          .orElseThrow(() -> new NotExistsException(applicationId + " not found"));
    }

    private HttpResponse nodes(String tenantName, String applicationName, String instanceName, String environment, String region) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = requireZone(environment, region);
        List<Node> nodes = controller.serviceRegistry().configServer().nodeRepository().list(zone, NodeFilter.all().applications(id));

        Slime slime = new Slime();
        Cursor nodesArray = slime.setObject().setArray("nodes");
        for (Node node : nodes) {
            Cursor nodeObject = nodesArray.addObject();
            nodeObject.setString("hostname", node.hostname().value());
            nodeObject.setString("state", valueOf(node.state()));
            node.reservedTo().ifPresent(tenant -> nodeObject.setString("reservedTo", tenant.value()));
            nodeObject.setString("orchestration", valueOf(node.serviceState()));
            nodeObject.setString("version", node.currentVersion().toString());
            node.flavor().ifPresent(flavor -> nodeObject.setString("flavor", flavor));
            toSlime(node.resources(), nodeObject);
            nodeObject.setString("clusterId", node.clusterId());
            nodeObject.setString("clusterType", valueOf(node.clusterType()));
            nodeObject.setBool("down", node.down());
            nodeObject.setBool("retired", node.retired() || node.wantToRetire());
            nodeObject.setBool("restarting", node.wantedRestartGeneration() > node.restartGeneration());
            nodeObject.setBool("rebooting", node.wantedRebootGeneration() > node.rebootGeneration());
            nodeObject.setString("group", node.group());
            nodeObject.setLong("index", node.index());
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse clusters(String tenantName, String applicationName, String instanceName, String environment, String region) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = requireZone(environment, region);
        com.yahoo.vespa.hosted.controller.api.integration.configserver.Application application = controller.serviceRegistry().configServer().nodeRepository().getApplication(zone, id);

        Slime slime = new Slime();
        Cursor clustersObject = slime.setObject().setObject("clusters");
        for (Cluster cluster : application.clusters().values()) {
            Cursor clusterObject = clustersObject.setObject(cluster.id().value());
            clusterObject.setString("type", cluster.type().name());
            toSlime(cluster.min(), clusterObject.setObject("min"));
            toSlime(cluster.max(), clusterObject.setObject("max"));
            if ( ! cluster.groupSize().isEmpty())
                toSlime(cluster.groupSize(), clusterObject.setObject("groupSize"));
            toSlime(cluster.current(), clusterObject.setObject("current"));
            toSlime(cluster.target(), clusterObject.setObject("target"));
            toSlime(cluster.suggested(), clusterObject.setObject("suggested"));
            scalingEventsToSlime(cluster.scalingEvents(), clusterObject.setArray("scalingEvents"));
            clusterObject.setLong("scalingDuration", cluster.scalingDuration().toMillis());
        }
        return new SlimeJsonResponse(slime);
    }

    private static String valueOf(Node.State state) {
        return switch (state) {
            case failed: yield "failed";
            case parked: yield "parked";
            case dirty: yield "dirty";
            case ready: yield "ready";
            case active: yield "active";
            case inactive: yield "inactive";
            case reserved: yield "reserved";
            case provisioned: yield "provisioned";
            case breakfixed: yield "breakfixed";
            case deprovisioned: yield "deprovisioned";
            default: throw new IllegalArgumentException("Unexpected node state '" + state + "'.");
        };
    }

    static String valueOf(Node.ServiceState state) {
        switch (state) {
            case expectedUp: return "expectedUp";
            case allowedDown: return "allowedDown";
            case permanentlyDown: return "permanentlyDown";
            case unorchestrated: return "unorchestrated";
            case unknown: break;
        }

        return "unknown";
    }

    private static String valueOf(Node.ClusterType type) {
        return switch (type) {
            case admin: yield "admin";
            case content: yield "content";
            case container: yield "container";
            case combined: yield "combined";
            case unknown: throw new IllegalArgumentException("Unexpected node cluster type '" + type + "'.");
        };
    }

    private static String valueOf(NodeResources.DiskSpeed diskSpeed) {
        return switch (diskSpeed) {
            case fast : yield "fast";
            case slow : yield "slow";
            case any  : yield "any";
        };
    }

    private static String valueOf(NodeResources.StorageType storageType) {
        return switch (storageType) {
            case remote : yield "remote";
            case local  : yield "local";
            case any    : yield "any";
        };
    }

    private HttpResponse logs(String tenantName, String applicationName, String instanceName, String environment, String region, Map<String, String> queryParameters) {
        ApplicationId application = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = requireZone(environment, region);
        DeploymentId deployment = new DeploymentId(application, zone);
        InputStream logStream = controller.serviceRegistry().configServer().getLogs(deployment, queryParameters);
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                try (logStream) {
                    logStream.transferTo(outputStream);
                }
            }
            @Override
            public long maxPendingBytes() {
                return 1 << 26;
            }
        };
    }

    private HttpResponse supportAccess(String tenantName, String applicationName, String instanceName, String environment, String region, Map<String, String> queryParameters) {
        DeploymentId deployment = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName), requireZone(environment, region));
        SupportAccess supportAccess = controller.supportAccess().forDeployment(deployment);
        return new SlimeJsonResponse(SupportAccessSerializer.serializeCurrentState(supportAccess, controller.clock().instant()));
    }

    // TODO support access: only let tenants (not operators!) allow access
    // TODO support access: configurable period of access?
    private HttpResponse allowSupportAccess(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        DeploymentId deployment = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName), requireZone(environment, region));
        Principal principal = requireUserPrincipal(request);
        Instant now = controller.clock().instant();
        SupportAccess allowed = controller.supportAccess().allow(deployment, now.plus(7, ChronoUnit.DAYS), principal.getName());
        return new SlimeJsonResponse(SupportAccessSerializer.serializeCurrentState(allowed, now));
    }

    private HttpResponse disallowSupportAccess(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        DeploymentId deployment = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName), requireZone(environment, region));
        Principal principal = requireUserPrincipal(request);
        SupportAccess disallowed = controller.supportAccess().disallow(deployment, principal.getName());
        controller.applications().deploymentTrigger().reTriggerOrAddToQueue(deployment, "re-triggered to disallow support access, by " + request.getJDiscRequest().getUserPrincipal().getName());
        return new SlimeJsonResponse(SupportAccessSerializer.serializeCurrentState(disallowed, controller.clock().instant()));
    }

    private HttpResponse metrics(String tenantName, String applicationName, String instanceName, String environment, String region) {
        ApplicationId application = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = requireZone(environment, region);
        DeploymentId deployment = new DeploymentId(application, zone);
        List<ProtonMetrics> protonMetrics = controller.serviceRegistry().configServer().getProtonMetrics(deployment);
        return buildResponseFromProtonMetrics(protonMetrics);
    }

    private HttpResponse scaling(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        var from = Optional.ofNullable(request.getProperty("from"))
                .map(Long::valueOf)
                .map(Instant::ofEpochSecond)
                .orElse(Instant.EPOCH);
        var until = Optional.ofNullable(request.getProperty("until"))
                .map(Long::valueOf)
                .map(Instant::ofEpochSecond)
                .orElse(Instant.now(controller.clock()));

        var application = ApplicationId.from(tenantName, applicationName, instanceName);
        var zone = requireZone(environment, region);
        var deployment = new DeploymentId(application, zone);
        var events = controller.serviceRegistry().resourceDatabase().scalingEvents(from, until, deployment);
        var slime = new Slime();
        var root = slime.setObject();
        for (var entry : events.entrySet()) {
            var serviceRoot = root.setArray(entry.getKey().clusterId().value());
            scalingEventsToSlime(entry.getValue(), serviceRoot);
        }
        return new SlimeJsonResponse(slime);
    }

    private JsonResponse buildResponseFromProtonMetrics(List<ProtonMetrics> protonMetrics) {
        try {
            var jsonObject = jsonMapper.createObjectNode();
            var jsonArray = jsonMapper.createArrayNode();
            for (ProtonMetrics metrics : protonMetrics) {
                jsonArray.add(metrics.toJson());
            }
            jsonObject.set("metrics", jsonArray);
            return new JsonResponse(200, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject));
        } catch (JsonProcessingException e) {
            log.log(Level.WARNING, "Unable to build JsonResponse with Proton data: " + e.getMessage(), e);
            return new JsonResponse(500, "");
        }
    }



    private HttpResponse trigger(ApplicationId id, JobType type, HttpRequest request) {
        Inspector requestObject = toSlime(request.getData()).get();
        boolean requireTests = ! requestObject.field("skipTests").asBool();
        boolean reTrigger = requestObject.field("reTrigger").asBool();
        boolean upgradeRevision = ! requestObject.field("skipRevision").asBool();
        boolean upgradePlatform = ! requestObject.field("skipUpgrade").asBool();
        String triggered = reTrigger
                           ? controller.applications().deploymentTrigger()
                                       .reTrigger(id, type, "re-triggered by " + request.getJDiscRequest().getUserPrincipal().getName()).type().jobName()
                           : controller.applications().deploymentTrigger()
                                       .forceTrigger(id, type, "triggered by " + request.getJDiscRequest().getUserPrincipal().getName(), requireTests, upgradeRevision, upgradePlatform)
                                       .stream().map(job -> job.type().jobName()).collect(joining(", "));
        String suppressedUpgrades = ( ! upgradeRevision || ! upgradePlatform ? ", without " : "") +
                                    (upgradeRevision ? "" : "revision") +
                                    ( ! upgradeRevision && ! upgradePlatform ? " and " : "") +
                                    (upgradePlatform ? "" : "platform") +
                                    ( ! upgradeRevision || ! upgradePlatform ? " upgrade" : "");
        return new MessageResponse(triggered.isEmpty() ? "Job " + type.jobName() + " for " + id + " not triggered"
                                                       : "Triggered " + triggered + " for " + id + suppressedUpgrades);
    }

    private HttpResponse pause(ApplicationId id, JobType type) {
        Instant until = controller.clock().instant().plus(DeploymentTrigger.maxPause);
        controller.applications().deploymentTrigger().pauseJob(id, type, until);
        return new MessageResponse(type.jobName() + " for " + id + " paused for " + DeploymentTrigger.maxPause);
    }

    private HttpResponse resume(ApplicationId id, JobType type) {
        controller.applications().deploymentTrigger().resumeJob(id, type);
        return new MessageResponse(type.jobName() + " for " + id + " resumed");
    }

    private SlimeJsonResponse resendEmailVerification(CloudTenant tenant, Inspector inspector) {
        var mail = mandatory("mail", inspector).asString();
        var type = mandatory("mailType", inspector).asString();

        var mailType = switch (type) {
            case "contact" -> PendingMailVerification.MailType.TENANT_CONTACT;
            case "notifications" -> PendingMailVerification.MailType.NOTIFICATIONS;
            default -> throw new IllegalArgumentException("Unknown mail type " + type);
        };

        var pendingVerification = controller.mailVerifier().resendMailVerification(tenant.name(), mail, mailType);
        return pendingVerification.isPresent() ? new MessageResponse("Re-sent verification mail to " + mail) :
                ErrorResponse.notFoundError("No pending mail verification found for " + mail);
    }

    private void toSlime(Cursor object, Application application, HttpRequest request) {
        object.setString("tenant", application.id().tenant().value());
        object.setString("application", application.id().application().value());
        object.setString("deployments", withPath("/application/v4" +
                                                 "/tenant/" + application.id().tenant().value() +
                                                 "/application/" + application.id().application().value() +
                                                 "/job/",
                                                 request.getUri()).toString());

        DeploymentStatus status = controller.jobController().deploymentStatus(application);
        application.revisions().last().ifPresent(version -> JobControllerApiHandlerHelper.toSlime(object.setObject("latestVersion"), version));

        application.projectId().ifPresent(id -> object.setLong("projectId", id));

        // TODO jonmv: Remove this when users are updated.
        application.instances().values().stream().findFirst().ifPresent(instance -> {
            // Currently deploying change
            if ( ! instance.change().isEmpty())
                toSlime(object.setObject("deploying"), instance.change(), application);

            // Outstanding change
            if ( ! status.outstandingChange(instance.name()).isEmpty())
                toSlime(object.setObject("outstandingChange"), status.outstandingChange(instance.name()), application);
        });

        application.majorVersion().ifPresent(majorVersion -> object.setLong("majorVersion", majorVersion));

        Cursor instancesArray = object.setArray("instances");
        for (Instance instance : showOnlyProductionInstances(request) ? application.productionInstances().values()
                                                                      : application.instances().values())
            toSlime(instancesArray.addObject(), status, instance, application.deploymentSpec(), request);

        application.deployKeys().stream().map(KeyUtils::toPem).forEach(object.setArray("pemDeployKeys")::addString);

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
        application.deploymentIssueId().ifPresent(issueId -> object.setString("deploymentIssueId", issueId.value()));
    }

    // TODO: Eliminate duplicated code in this and toSlime(Cursor, Instance, DeploymentStatus, HttpRequest)
    private void toSlime(Cursor object, DeploymentStatus status, Instance instance, DeploymentSpec deploymentSpec, HttpRequest request) {
        object.setString("instance", instance.name().value());

        if (deploymentSpec.instance(instance.name()).isPresent()) {
            // Jobs ordered according to deployment spec
            Collection<JobStatus> jobStatus = status.instanceJobs(instance.name()).values();

            if ( ! instance.change().isEmpty())
                toSlime(object.setObject("deploying"), instance.change(), status.application());

            // Outstanding change
            if ( ! status.outstandingChange(instance.name()).isEmpty())
                toSlime(object.setObject("outstandingChange"), status.outstandingChange(instance.name()), status.application());

            // Change blockers
            Cursor changeBlockers = object.setArray("changeBlockers");
            deploymentSpec.instance(instance.name()).ifPresent(spec -> spec.changeBlocker().forEach(changeBlocker -> {
                Cursor changeBlockerObject = changeBlockers.addObject();
                changeBlockerObject.setBool("versions", changeBlocker.blocksVersions());
                changeBlockerObject.setBool("revisions", changeBlocker.blocksRevisions());
                changeBlockerObject.setString("timeZone", changeBlocker.window().zone().getId());
                Cursor days = changeBlockerObject.setArray("days");
                changeBlocker.window().days().stream().map(DayOfWeek::getValue).forEach(days::addLong);
                Cursor hours = changeBlockerObject.setArray("hours");
                changeBlocker.window().hours().forEach(hours::addLong);
            }));
        }

        // Rotation ID
        addRotationId(object, instance);

        // Deployments sorted according to deployment spec
        List<Deployment> deployments = deploymentSpec.instance(instance.name())
                                                     .map(spec -> sortedDeployments(instance.deployments().values(), spec))
                                                     .orElse(List.copyOf(instance.deployments().values()));

        Cursor deploymentsArray = object.setArray("deployments");
        for (Deployment deployment : deployments) {
            Cursor deploymentObject = deploymentsArray.addObject();

            // Rotation status for this deployment
            if (deployment.zone().environment() == Environment.prod && ! instance.rotations().isEmpty())
                toSlime(instance.rotations(), instance.rotationStatus(), deployment, deploymentObject);

            if (recurseOverDeployments(request)) // List full deployment information when recursive.
                toSlime(deploymentObject, new DeploymentId(instance.id(), deployment.zone()), deployment, request);
            else {
                deploymentObject.setString("environment", deployment.zone().environment().value());
                deploymentObject.setString("region", deployment.zone().region().value());
                deploymentObject.setString("url", withPath(request.getUri().getPath() +
                                                           "/instance/" + instance.name().value() +
                                                           "/environment/" + deployment.zone().environment().value() +
                                                           "/region/" + deployment.zone().region().value(),
                                                           request.getUri()).toString());
            }
        }
    }

    // TODO(mpolden): Remove once MultiRegionTest stops expecting this field
    private void addRotationId(Cursor object, Instance instance) {
        // Legacy field. Identifies the first assigned rotation, if any.
        instance.rotations().stream()
                .map(AssignedRotation::rotationId)
                .findFirst()
                .ifPresent(rotation -> object.setString("rotationId", rotation.asString()));
    }

    private void toSlime(Cursor object, Instance instance, DeploymentStatus status, HttpRequest request) {
        Application application = status.application();
        object.setString("tenant", instance.id().tenant().value());
        object.setString("application", instance.id().application().value());
        object.setString("instance", instance.id().instance().value());
        object.setString("deployments", withPath("/application/v4" +
                                                 "/tenant/" + instance.id().tenant().value() +
                                                 "/application/" + instance.id().application().value() +
                                                 "/instance/" + instance.id().instance().value() + "/job/",
                                                 request.getUri()).toString());

        application.revisions().last().ifPresent(version -> {
            version.sourceUrl().ifPresent(url -> object.setString("sourceUrl", url));
            version.commit().ifPresent(commit -> object.setString("commit", commit));
        });

        application.projectId().ifPresent(id -> object.setLong("projectId", id));

        if (application.deploymentSpec().instance(instance.name()).isPresent()) {
            // Jobs ordered according to deployment spec
            Collection<JobStatus> jobStatus = status.instanceJobs(instance.name()).values();

            if ( ! instance.change().isEmpty())
                toSlime(object.setObject("deploying"), instance.change(), application);

            // Outstanding change
            if ( ! status.outstandingChange(instance.name()).isEmpty())
                toSlime(object.setObject("outstandingChange"), status.outstandingChange(instance.name()), application);

            // Change blockers
            Cursor changeBlockers = object.setArray("changeBlockers");
            application.deploymentSpec().instance(instance.name()).ifPresent(spec -> spec.changeBlocker().forEach(changeBlocker -> {
                Cursor changeBlockerObject = changeBlockers.addObject();
                changeBlockerObject.setBool("versions", changeBlocker.blocksVersions());
                changeBlockerObject.setBool("revisions", changeBlocker.blocksRevisions());
                changeBlockerObject.setString("timeZone", changeBlocker.window().zone().getId());
                Cursor days = changeBlockerObject.setArray("days");
                changeBlocker.window().days().stream().map(DayOfWeek::getValue).forEach(days::addLong);
                Cursor hours = changeBlockerObject.setArray("hours");
                changeBlocker.window().hours().forEach(hours::addLong);
            }));
        }

        application.majorVersion().ifPresent(majorVersion -> object.setLong("majorVersion", majorVersion));

        // Rotation ID
        addRotationId(object, instance);

        // Deployments sorted according to deployment spec
        List<Deployment> deployments = application.deploymentSpec().instance(instance.name())
                                                  .map(spec -> sortedDeployments(instance.deployments().values(), spec))
                                                  .orElse(List.copyOf(instance.deployments().values()));
        Cursor instancesArray = object.setArray("instances");
        for (Deployment deployment : deployments) {
            Cursor deploymentObject = instancesArray.addObject();

            // Rotation status for this deployment
            if (deployment.zone().environment() == Environment.prod) {
                //  0 rotations: No fields written
                //  1 rotation : Write legacy field and endpointStatus field
                // >1 rotation : Write only endpointStatus field
                if (instance.rotations().size() == 1) {
                    // TODO(mpolden): Stop writing this field once clients stop expecting it
                    toSlime(instance.rotationStatus().of(instance.rotations().get(0).rotationId(), deployment),
                            deploymentObject);
                }
                if ( ! recurseOverDeployments(request) && ! instance.rotations().isEmpty()) { // TODO jonmv: clean up when clients have converged.
                    toSlime(instance.rotations(), instance.rotationStatus(), deployment, deploymentObject);
                }

            }

            if (recurseOverDeployments(request)) // List full deployment information when recursive.
                toSlime(deploymentObject, new DeploymentId(instance.id(), deployment.zone()), deployment, request);
            else {
                deploymentObject.setString("environment", deployment.zone().environment().value());
                deploymentObject.setString("region", deployment.zone().region().value());
                deploymentObject.setString("instance", instance.id().instance().value()); // pointless
                deploymentObject.setString("url", withPath(request.getUri().getPath() +
                                                           "/environment/" + deployment.zone().environment().value() +
                                                           "/region/" + deployment.zone().region().value(),
                                                           request.getUri()).toString());
            }
        }
        // Add dummy values for not-yet-existent prod deployments, and running dev/perf deployments.
        Stream.concat(status.jobSteps().keySet().stream()
                            .filter(job -> job.application().instance().equals(instance.name()))
                            .filter(job -> job.type().isProduction() && job.type().isDeployment()),
                      controller.jobController().active(instance.id()).stream()
                                .map(run -> run.id().job())
                                .filter(job -> job.type().environment().isManuallyDeployed()))
              .map(job -> job.type().zone())
              .filter(zone -> ! instance.deployments().containsKey(zone))
              .forEach(zone -> {
                  Cursor deploymentObject = instancesArray.addObject();
                  deploymentObject.setString("environment", zone.environment().value());
                  deploymentObject.setString("region", zone.region().value());
              });


        // TODO jonmv: Remove when clients are updated
        application.deployKeys().stream().findFirst().ifPresent(key -> object.setString("pemDeployKey", KeyUtils.toPem(key)));

        application.deployKeys().stream().map(KeyUtils::toPem).forEach(object.setArray("pemDeployKeys")::addString);

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
        application.deploymentIssueId().ifPresent(issueId -> object.setString("deploymentIssueId", issueId.value()));
    }

    private HttpResponse deployment(String tenantName, String applicationName, String instanceName, String environment,
                                    String region, HttpRequest request) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        Instance instance = controller.applications().getInstance(id)
                                      .orElseThrow(() -> new NotExistsException(id + " not found"));

        DeploymentId deploymentId = new DeploymentId(instance.id(),
                                                     requireZone(environment, region));

        Deployment deployment = instance.deployments().get(deploymentId.zoneId());
        if (deployment == null)
            throw new NotExistsException(instance + " is not deployed in " + deploymentId.zoneId());

        Slime slime = new Slime();
        toSlime(slime.setObject(), deploymentId, deployment, request);
        return new SlimeJsonResponse(slime);
    }

    private void toSlime(Cursor object, Change change, Application application) {
        change.platform().ifPresent(version -> object.setString("version", version.toString()));
        change.revision().ifPresent(revision -> JobControllerApiHandlerHelper.toSlime(object.setObject("revision"), application.revisions().get(revision)));
    }

    private void toSlime(Endpoint endpoint, Cursor object) {
        object.setString("cluster", endpoint.cluster().value());
        object.setBool("tls", endpoint.tls());
        object.setString("url", endpoint.url().toString());
        object.setString("scope", endpointScopeString(endpoint.scope()));
        object.setString("routingMethod", routingMethodString(endpoint.routingMethod()));
        object.setBool("legacy", endpoint.legacy());
    }

    private void toSlime(Cursor response, DeploymentId deploymentId, Deployment deployment, HttpRequest request) {
        response.setString("tenant", deploymentId.applicationId().tenant().value());
        response.setString("application", deploymentId.applicationId().application().value());
        response.setString("instance", deploymentId.applicationId().instance().value()); // pointless
        response.setString("environment", deploymentId.zoneId().environment().value());
        response.setString("region", deploymentId.zoneId().region().value());
        var application = controller.applications().requireApplication(TenantAndApplicationId.from(deploymentId.applicationId()));

        // Add zone endpoints
        boolean legacyEndpoints = request.getBooleanProperty("includeLegacyEndpoints");
        var endpointArray = response.setArray("endpoints");
        EndpointList zoneEndpoints = controller.routing().readEndpointsOf(deploymentId)
                                               .scope(Endpoint.Scope.zone);
        if (!legacyEndpoints) {
            zoneEndpoints = zoneEndpoints.not().legacy().direct();
        }
        for (var endpoint : zoneEndpoints) {
            toSlime(endpoint, endpointArray.addObject());
        }
        // Add declared endpoints
        EndpointList declaredEndpoints = controller.routing().declaredEndpointsOf(application)
                                                   .targets(deploymentId);
        if (!legacyEndpoints) {
            declaredEndpoints = declaredEndpoints.not().legacy().direct();
        }
        for (var endpoint : declaredEndpoints) {
            toSlime(endpoint, endpointArray.addObject());
        }

        response.setString("clusters", withPath(toPath(deploymentId) + "/clusters", request.getUri()).toString());
        response.setString("nodes", withPathAndQuery("/zone/v2/" + deploymentId.zoneId().environment() + "/" + deploymentId.zoneId().region() + "/nodes/v2/node/", "recursive=true&application=" + deploymentId.applicationId().tenant() + "." + deploymentId.applicationId().application() + "." + deploymentId.applicationId().instance(), request.getUri()).toString());
        response.setString("yamasUrl", monitoringSystemUri(deploymentId).toString());
        response.setString("version", deployment.version().toFullString());
        response.setString("revision", application.revisions().get(deployment.revision()).stringId()); // TODO jonmv or freva:  ƪ(`▿▿▿▿´ƪ)
        response.setLong("build", deployment.revision().number());
        Instant lastDeploymentStart = controller.jobController().lastDeploymentStart(deploymentId.applicationId(), deployment);
        response.setLong("deployTimeEpochMs", lastDeploymentStart.toEpochMilli());
        controller.zoneRegistry().getDeploymentTimeToLive(deploymentId.zoneId())
                  .ifPresent(deploymentTimeToLive -> response.setLong("expiryTimeEpochMs", lastDeploymentStart.plus(deploymentTimeToLive).toEpochMilli()));

        application.projectId().ifPresent(i -> response.setString("screwdriverId", String.valueOf(i)));

        controller.applications().decideCloudAccountOf(deploymentId, application.deploymentSpec()).ifPresent(cloudAccount -> {
            Cursor enclave = response.setObject("enclave");
            enclave.setString("cloudAccount", cloudAccount.value());
            controller.zoneRegistry().cloudAccountAthenzDomain(cloudAccount).ifPresent(domain -> enclave.setString("athensDomain", domain.value()));
        });

        var instance = application.instances().get(deploymentId.applicationId().instance());
        if (instance != null) {
            if (!instance.rotations().isEmpty() && deployment.zone().environment() == Environment.prod)
                toSlime(instance.rotations(), instance.rotationStatus(), deployment, response);

            if (!deployment.zone().environment().isManuallyDeployed()) {
                DeploymentStatus status = controller.jobController().deploymentStatus(application);
                JobId jobId = new JobId(instance.id(), JobType.deploymentTo(deployment.zone()));
                Optional.ofNullable(status.jobSteps().get(jobId))
                        .ifPresent(stepStatus -> {
                            JobControllerApiHandlerHelper.toSlime(response.setObject("applicationVersion"), application.revisions().get(deployment.revision()));
                            if ( ! status.jobsToRun().containsKey(stepStatus.job().get()))
                                response.setString("status", "complete");
                            else if (stepStatus.readyAt(instance.change()).map(controller.clock().instant()::isBefore).orElse(true))
                                response.setString("status", "pending");
                            else
                                response.setString("status", "running");
                        });
            } else {
                var deploymentRun = controller.jobController().last(deploymentId.applicationId(), JobType.deploymentTo(deploymentId.zoneId()));
                deploymentRun.ifPresent(run -> {
                    response.setString("status", run.hasEnded() ? "complete" : "running");
                });
            }
        }

        response.setDouble("quota", deployment.quota().rate());
        deployment.cost().ifPresent(cost -> response.setDouble("cost", cost));

        controller.archiveBucketDb().archiveUriFor(deploymentId.zoneId(), deploymentId.applicationId().tenant(), false)
                .ifPresent(archiveUri -> response.setString("archiveUri", archiveUri.toString()));

        Cursor activity = response.setObject("activity");
        deployment.activity().lastQueried().ifPresent(instant -> activity.setLong("lastQueried",
                                                                                  instant.toEpochMilli()));
        deployment.activity().lastWritten().ifPresent(instant -> activity.setLong("lastWritten",
                                                                                  instant.toEpochMilli()));
        deployment.activity().lastQueriesPerSecond().ifPresent(value -> activity.setDouble("lastQueriesPerSecond", value));
        deployment.activity().lastWritesPerSecond().ifPresent(value -> activity.setDouble("lastWritesPerSecond", value));

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

    private void toSlime(RotationState state, Cursor object) {
        Cursor bcpStatus = object.setObject("bcpStatus");
        bcpStatus.setString("rotationStatus", rotationStateString(state));
    }

    private void toSlime(List<AssignedRotation> rotations, RotationStatus status, Deployment deployment, Cursor object) {
        var array = object.setArray("endpointStatus");
        for (var rotation : rotations) {
            var statusObject = array.addObject();
            var targets = status.of(rotation.rotationId());
            statusObject.setString("endpointId", rotation.endpointId().id());
            statusObject.setString("rotationId", rotation.rotationId().asString());
            statusObject.setString("clusterId", rotation.clusterId().value());
            statusObject.setString("status", rotationStateString(status.of(rotation.rotationId(), deployment)));
            statusObject.setLong("lastUpdated", targets.lastUpdated().toEpochMilli());
        }
    }

    private URI monitoringSystemUri(DeploymentId deploymentId) {
        return controller.zoneRegistry().getMonitoringSystemUri(deploymentId);
    }

    private HttpResponse setGlobalRotationOverride(String tenantName, String applicationName, String instanceName, String environment, String region, boolean inService, HttpRequest request) {
        Instance instance = controller.applications().requireInstance(ApplicationId.from(tenantName, applicationName, instanceName));
        ZoneId zone = requireZone(environment, region);
        Deployment deployment = instance.deployments().get(zone);
        if (deployment == null) {
            throw new NotExistsException(instance + " has no deployment in " + zone);
        }
        DeploymentId deploymentId = new DeploymentId(instance.id(), zone);
        RoutingStatus.Agent agent = isOperator(request) ? RoutingStatus.Agent.operator : RoutingStatus.Agent.tenant;
        RoutingStatus.Value status = inService ? RoutingStatus.Value.in : RoutingStatus.Value.out;
        controller.routing().of(deploymentId).setRoutingStatus(status, agent);
        return new MessageResponse(Text.format("Successfully set %s in %s %s service",
                                                 instance.id().toShortString(), zone, inService ? "in" : "out of"));
    }

    private String serviceTypeIn(DeploymentId id) {
        CloudName cloud = controller.zoneRegistry().zones().all().get(id.zoneId()).get().getCloudName();
        if (CloudName.AWS.equals(cloud)) return "aws-private-link";
        if (CloudName.GCP.equals(cloud)) return "gcp-service-connect";
        return "unknown";
    }

    private HttpResponse getPrivateServiceInfo(String tenantName, String applicationName, String instanceName, String environment, String region) {
        DeploymentId id = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                           ZoneId.from(environment, region));
        List<LoadBalancer> lbs = controller.serviceRegistry().configServer().getLoadBalancers(id.applicationId(), id.zoneId());
        Slime slime = new Slime();
        Cursor lbArray = slime.setObject().setArray("privateServices");
        for (LoadBalancer lb : lbs) {
            Cursor serviceObject = lbArray.addObject();
            serviceObject.setString("cluster", lb.cluster().value());
            lb.service().ifPresent(service -> {
                serviceObject.setString("serviceId", service.id()); // Really the "serviceName", but this is what the user needs >_<
                serviceObject.setString("type", serviceTypeIn(id));
                Cursor urnsArray = serviceObject.setArray("allowedUrns");
                for (AllowedUrn urn : service.allowedUrns()) {
                    Cursor urnObject = urnsArray.addObject();
                    urnObject.setString("type", switch (urn.type()) {
                        case awsPrivateLink -> "aws-private-link";
                        case gcpServiceConnect -> "gcp-service-connect";
                    });
                    urnObject.setString("urn", urn.urn());
                }
                Cursor endpointsArray = serviceObject.setArray("endpoints");
                controller.serviceRegistry().vpcEndpointService()
                          .getConnections(new ClusterId(id, lb.cluster()), lb.cloudAccount())
                        .forEach(endpoint -> {
                            Cursor endpointObject = endpointsArray.addObject();
                            endpointObject.setString("endpointId", endpoint.endpointId());
                            endpointObject.setString("state", endpoint.state());
                        });
            });
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse getGlobalRotationOverride(String tenantName, String applicationName, String instanceName, String environment, String region) {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                                     requireZone(environment, region));
        Slime slime = new Slime();
        Cursor array = slime.setObject().setArray("globalrotationoverride");
        Optional<Endpoint> primaryEndpoint = controller.routing().readDeclaredEndpointsOf(deploymentId.applicationId())
                                                       .requiresRotation()
                                                       .primary();
        if (primaryEndpoint.isPresent()) {
            DeploymentRoutingContext context = controller.routing().of(deploymentId);
            RoutingStatus status = context.routingStatus();
            array.addString(primaryEndpoint.get().upstreamName(deploymentId));
            Cursor statusObject = array.addObject();
            statusObject.setString("status", status.value().name());
            statusObject.setString("reason", "");
            statusObject.setString("agent", status.agent().name());
            statusObject.setLong("timestamp", status.changedAt().getEpochSecond());
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse rotationStatus(String tenantName, String applicationName, String instanceName, String environment, String region, Optional<String> endpointId) {
        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, instanceName);
        Instance instance = controller.applications().requireInstance(applicationId);
        ZoneId zone = requireZone(environment, region);
        RotationId rotation = findRotationId(instance, endpointId);
        Deployment deployment = instance.deployments().get(zone);
        if (deployment == null) {
            throw new NotExistsException(instance + " has no deployment in " + zone);
        }

        Slime slime = new Slime();
        Cursor response = slime.setObject();
        toSlime(instance.rotationStatus().of(rotation, deployment), response);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse deploying(String tenantName, String applicationName, String instanceName, HttpRequest request) {
        Instance instance = controller.applications().requireInstance(ApplicationId.from(tenantName, applicationName, instanceName));
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        if ( ! instance.change().isEmpty()) {
            instance.change().platform().ifPresent(version -> root.setString("platform", version.toString()));
            instance.change().revision().ifPresent(revision -> root.setString("application", revision.toString()));
            root.setBool("pinned", instance.change().isPinned());
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse suspended(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                                     requireZone(environment, region));
        boolean suspended = controller.applications().isSuspended(deploymentId);
        Slime slime = new Slime();
        Cursor response = slime.setObject();
        response.setBool("suspended", suspended);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse status(String tenantName, String applicationName, String instanceName, String environment, String region, String serviceName, String host, HttpURL.Path restPath, HttpRequest request) {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName), requireZone(environment, region));
        return controller.serviceRegistry().configServer().getServiceNodePage(deploymentId,
                                                                              serviceName,
                                                                              DomainName.of(host),
                                                                              HttpURL.Path.parse("/status").append(restPath),
                                                                              Query.empty().add(request.getJDiscRequest().parameters()));
    }

    private HttpResponse orchestrator(String tenantName, String applicationName, String instanceName, String environment, String region) {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName), requireZone(environment, region));
        return controller.serviceRegistry().configServer().getServiceNodes(deploymentId);
    }

    private HttpResponse stateV1(String tenantName, String applicationName, String instanceName, String environment, String region, String serviceName, String host, HttpURL.Path rest, HttpRequest request) {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName), requireZone(environment, region));
        Query query = Query.empty().add(request.getJDiscRequest().parameters());
        query = query.set("forwarded-url", HttpURL.from(request.getUri()).withQuery(Query.empty()).asURI().toString());
        return controller.serviceRegistry().configServer().getServiceNodePage(
                deploymentId, serviceName, DomainName.of(host), HttpURL.Path.parse("/state/v1").append(rest), query);
    }

    private HttpResponse content(String tenantName, String applicationName, String instanceName, String environment, String region, HttpURL.Path restPath, HttpRequest request) {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName), requireZone(environment, region));
        return controller.serviceRegistry().configServer().getApplicationPackageContent(deploymentId, restPath, request.getUri());
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
        if (controller.system().isPublic()) {
            User user = getAttribute(request, User.ATTRIBUTE_NAME, User.class);
            TenantInfo info = controller.tenants().require(tenant, CloudTenant.class)
                    .info()
                    .withContact(TenantContact.from(user.name(), new Email(user.email(), true)));
            // Store changes
            controller.tenants().lockOrThrow(tenant, LockedTenant.Cloud.class, lockedTenant -> {
                lockedTenant = lockedTenant.withInfo(info);
                controller.tenants().store(lockedTenant);
            });
        }
        return tenant(controller.tenants().require(TenantName.from(tenantName)), request);
    }

    private HttpResponse createApplication(String tenantName, String applicationName, HttpRequest request) {
        Inspector requestObject = toSlime(request.getData()).get();
        TenantAndApplicationId id = TenantAndApplicationId.from(tenantName, applicationName);
        Credentials credentials = accessControlRequests.credentials(id.tenant(), requestObject, request.getJDiscRequest());
        Application application = controller.applications().createApplication(id, credentials);
        Slime slime = new Slime();
        toSlime(id, slime.setObject(), request);
        return new SlimeJsonResponse(slime);
    }

    // TODO jonmv: Remove when clients are updated.
    private HttpResponse createInstance(String tenantName, String applicationName, String instanceName, HttpRequest request) {
        TenantAndApplicationId applicationId = TenantAndApplicationId.from(tenantName, applicationName);
        if (controller.applications().getApplication(applicationId).isEmpty())
            createApplication(tenantName, applicationName, request);

        controller.applications().createInstance(applicationId.instance(instanceName));

        Slime slime = new Slime();
        toSlime(applicationId.instance(instanceName), slime.setObject(), request);
        return new SlimeJsonResponse(slime);
    }

    /** Trigger deployment of the given Vespa version if a valid one is given, e.g., "7.8.9". */
    private HttpResponse deployPlatform(String tenantName, String applicationName, String instanceName, boolean pin, HttpRequest request) {
        String versionString = readToString(request.getData());
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        StringBuilder response = new StringBuilder();
        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application -> {
            Version version = Version.fromString(versionString);
            VersionStatus versionStatus = controller.readVersionStatus();
            if (version.equals(Version.emptyVersion))
                version = controller.systemVersion(versionStatus);
            if ( ! versionStatus.isActive(version) && ! isOperator(request))
                throw new IllegalArgumentException("Cannot trigger deployment of version '" + version + "': " +
                                                   "Version is not active in this system. " +
                                                   "Active versions: " + versionStatus.versions()
                                                                                      .stream()
                                                                                      .map(VespaVersion::versionNumber)
                                                                                      .map(Version::toString)
                                                                                      .collect(joining(", ")));
            Change change = Change.of(version);
            if (pin)
                change = change.withPin();

            controller.applications().deploymentTrigger().forceChange(id, change, isOperator(request));
            response.append("Triggered ").append(change).append(" for ").append(id);
        });
        return new MessageResponse(response.toString());
    }

    /** Trigger deployment to the last known application package for the given application. */
    private HttpResponse deployApplication(String tenantName, String applicationName, String instanceName, HttpRequest request) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        Inspector buildField = toSlime(request.getData()).get().field("build");
        long build = buildField.valid() ? buildField.asLong() : -1;

        StringBuilder response = new StringBuilder();
        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application -> {
            RevisionId revision = build == -1 ? application.get().revisions().last().get().id()
                                              : getRevision(application.get(), build);
            Change change = Change.of(revision);
            controller.applications().deploymentTrigger().forceChange(id, change, isOperator(request));
            response.append("Triggered ").append(change).append(" for ").append(id);
        });
        return new MessageResponse(response.toString());
    }

    private RevisionId getRevision(Application application, long build) {
        return application.revisions().withPackage().stream()
                          .map(ApplicationVersion::id)
                          .filter(version -> version.number() == build)
                          .findFirst()
                          .filter(version -> controller.applications().applicationStore().hasBuild(application.id().tenant(),
                                                                                                   application.id().application(),
                                                                                                   build))
                          .orElseThrow(() -> new IllegalArgumentException("Build number '" + build + "' was not found"));
    }

    private HttpResponse cancelBuild(String tenantName, String applicationName, String build){
        TenantAndApplicationId id = TenantAndApplicationId.from(tenantName, applicationName);
        RevisionId revision = RevisionId.forProduction(Long.parseLong(build));
        controller.applications().lockApplicationOrThrow(id, application -> {
            controller.applications().store(application.withRevisions(revisions -> revisions.with(revisions.get(revision).skipped())));
            for (Instance instance : application.get().instances().values())
                if (instance.change().revision().equals(Optional.of(revision)))
                    controller.applications().deploymentTrigger().cancelChange(instance.id(), ChangesToCancel.APPLICATION);
        });
        return new MessageResponse("Marked build '" + build + "' as non-deployable");
    }

    /** Cancel ongoing change for given application, e.g., everything with {"cancel":"all"} */
    private HttpResponse cancelDeploy(String tenantName, String applicationName, String instanceName, String choice) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        StringBuilder response = new StringBuilder();
        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application -> {
            Change change = application.get().require(id.instance()).change();
            if (change.isEmpty()) {
                response.append("No deployment in progress for ").append(id).append(" at this time");
                return;
            }

            ChangesToCancel cancel = ChangesToCancel.valueOf(choice.toUpperCase());
            controller.applications().deploymentTrigger().cancelChange(id, cancel);
            response.append("Changed deployment from '").append(change).append("' to '").append(controller.applications().requireInstance(id).change()).append("' for ").append(id);
        });

        return new MessageResponse(response.toString());
    }

    /** Schedule reindexing of an application, or a subset of clusters, possibly on a subset of documents. */
    private HttpResponse reindex(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = requireZone(environment, region);
        List<String> clusterNames = Optional.ofNullable(request.getProperty("clusterId")).stream()
                                            .flatMap(clusters -> Stream.of(clusters.split(",")))
                                            .filter(cluster -> ! cluster.isBlank())
                                            .toList();
        List<String> documentTypes = Optional.ofNullable(request.getProperty("documentType")).stream()
                                             .flatMap(types -> Stream.of(types.split(",")))
                                             .filter(type -> ! type.isBlank())
                                             .toList();

        Double speed = request.hasProperty("speed") ? Double.parseDouble(request.getProperty("speed")) : null;
        boolean indexedOnly = request.getBooleanProperty("indexedOnly");
        controller.applications().reindex(id, zone, clusterNames, documentTypes, indexedOnly, speed, "reindexing triggered by " + requireUserPrincipal(request).getName());
        return new MessageResponse("Requested reindexing of " + id + " in " + zone +
                                   (clusterNames.isEmpty() ? "" : ", on clusters " + String.join(", ", clusterNames)) +
                                   (documentTypes.isEmpty() ? "" : ", for types " + String.join(", ", documentTypes)) +
                                   (indexedOnly ? ", for indexed types" : "") +
                                   (speed != null ? ", with speed " + speed : ""));
    }

    /** Gets reindexing status of an application in a zone. */
    private HttpResponse getReindexing(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = requireZone(environment, region);
        ApplicationReindexing reindexing = controller.applications().applicationReindexing(id, zone);

        Slime slime = new Slime();
        Cursor root = slime.setObject();

        root.setBool("enabled", reindexing.enabled());

        Cursor clustersArray = root.setArray("clusters");
        reindexing.clusters().entrySet().stream().sorted(comparingByKey())
                  .forEach(cluster -> {
                      Cursor clusterObject = clustersArray.addObject();
                      clusterObject.setString("name", cluster.getKey());

                      Cursor pendingArray = clusterObject.setArray("pending");
                      cluster.getValue().pending().entrySet().stream().sorted(comparingByKey())
                             .forEach(pending -> {
                                 Cursor pendingObject = pendingArray.addObject();
                                 pendingObject.setString("type", pending.getKey());
                                 pendingObject.setLong("requiredGeneration", pending.getValue());
                             });

                      Cursor readyArray = clusterObject.setArray("ready");
                      cluster.getValue().ready().entrySet().stream().sorted(comparingByKey())
                             .forEach(ready -> {
                                 Cursor readyObject = readyArray.addObject();
                                 readyObject.setString("type", ready.getKey());
                                 setStatus(readyObject, ready.getValue());
                             });
                  });
        return new SlimeJsonResponse(slime);
    }

    void setStatus(Cursor statusObject, ApplicationReindexing.Status status) {
        status.readyAt().ifPresent(readyAt -> statusObject.setLong("readyAtMillis", readyAt.toEpochMilli()));
        status.startedAt().ifPresent(startedAt -> statusObject.setLong("startedAtMillis", startedAt.toEpochMilli()));
        status.endedAt().ifPresent(endedAt -> statusObject.setLong("endedAtMillis", endedAt.toEpochMilli()));
        status.state().map(ApplicationApiHandler::toString).ifPresent(state -> statusObject.setString("state", state));
        status.message().ifPresent(message -> statusObject.setString("message", message));
        status.progress().ifPresent(progress -> statusObject.setDouble("progress", progress));
        status.speed().ifPresent(speed -> statusObject.setDouble("speed", speed));
        status.cause().ifPresent(cause -> statusObject.setString("cause", cause));
    }

    private static String toString(ApplicationReindexing.State state) {
        return switch (state) {
            case PENDING: yield "pending";
            case RUNNING: yield "running";
            case FAILED: yield "failed";
            case SUCCESSFUL: yield "successful";
        };
    }

    /** Enables reindexing of an application in a zone. */
    private HttpResponse enableReindexing(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = requireZone(environment, region);
        controller.applications().enableReindexing(id, zone);
        return new MessageResponse("Enabled reindexing of " + id + " in " + zone);
    }

    /** Disables reindexing of an application in a zone. */
    private HttpResponse disableReindexing(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        ApplicationId id = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = requireZone(environment, region);
        controller.applications().disableReindexing(id, zone);
        return new MessageResponse("Disabled reindexing of " + id + " in " + zone);
    }

    /** Schedule restart of deployment, or specific host in a deployment */
    private HttpResponse restart(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                                     requireZone(environment, region));
        RestartFilter restartFilter = new RestartFilter()
                .withHostName(Optional.ofNullable(request.getProperty("hostname")).map(HostName::of))
                .withClusterType(Optional.ofNullable(request.getProperty("clusterType")).map(ClusterSpec.Type::from))
                .withClusterId(Optional.ofNullable(request.getProperty("clusterId")).map(ClusterSpec.Id::from));

        controller.applications().restart(deploymentId, restartFilter);
        return new MessageResponse("Requested restart of " + deploymentId);
    }

    /** Set suspension status of the given deployment. */
    private HttpResponse suspend(String tenantName, String applicationName, String instanceName, String environment, String region, boolean suspend) {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                                     requireZone(environment, region));
        controller.applications().setSuspension(deploymentId, suspend);
        return new MessageResponse((suspend ? "Suspended" : "Resumed") + " orchestration of " + deploymentId);
    }

    private HttpResponse jobDeploy(ApplicationId id, JobType type, HttpRequest request) {
        if ( ! type.environment().isManuallyDeployed() && ! isOperator(request))
            throw new IllegalArgumentException("Direct deployments are only allowed to manually deployed environments.");

        Map<String, byte[]> dataParts = parseDataParts(request);
        if ( ! dataParts.containsKey("applicationZip"))
            throw new IllegalArgumentException("Missing required form part 'applicationZip'");

        ApplicationPackage applicationPackage = new ApplicationPackage(dataParts.get(EnvironmentResource.APPLICATION_ZIP));
        controller.applications().verifyApplicationIdentityConfiguration(id.tenant(),
                                                                         Optional.of(id.instance()),
                                                                         Optional.of(type.zone()),
                                                                         applicationPackage,
                                                                         Optional.of(requireUserPrincipal(request)));

        Optional<Version> version = Optional.ofNullable(dataParts.get("deployOptions"))
                                            .map(json -> SlimeUtils.jsonToSlime(json).get())
                                            .flatMap(options -> optional("vespaVersion", options))
                                            .map(Version::fromString);

        ensureApplicationExists(TenantAndApplicationId.from(id), request);

        boolean dryRun = Optional.ofNullable(dataParts.get("deployOptions"))
                                 .map(json -> SlimeUtils.jsonToSlime(json).get())
                                 .flatMap(options -> optional("dryRun", options))
                                 .map(Boolean::valueOf)
                                 .orElse(false);

        controller.jobController().deploy(id, type, version, applicationPackage, dryRun, isOperator(request));
        RunId runId = controller.jobController().last(id, type).get().id();
        Slime slime = new Slime();
        Cursor rootObject = slime.setObject();
        rootObject.setString("message", "Deployment started in " + runId +
                                        ". This may take about 15 minutes the first time.");
        rootObject.setLong("run", runId.number());
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse deploySystemApplication(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, instanceName);
        ZoneId zone = requireZone(environment, region);

        // Get deployOptions
        Map<String, byte[]> dataParts = parseDataParts(request);
        if ( ! dataParts.containsKey("deployOptions"))
            return ErrorResponse.badRequest("Missing required form part 'deployOptions'");
        Inspector deployOptions = SlimeUtils.jsonToSlime(dataParts.get("deployOptions")).get();

        // Resolve system application
        Optional<SystemApplication> systemApplication = SystemApplication.matching(applicationId);
        if (systemApplication.isEmpty() || ! systemApplication.get().hasApplicationPackage()) {
            return ErrorResponse.badRequest("Deployment of " + applicationId + " is not supported through this API");
        }

        // Make it explicit that version is not yet supported here
        String vespaVersion = deployOptions.field("vespaVersion").asString();
        if ( ! vespaVersion.isEmpty()) {
            return ErrorResponse.badRequest("Specifying version for " + applicationId + " is not permitted");
        }

        // To avoid second guessing the orchestrated upgrades of system applications we don't allow
        // deploying these during a system upgrade, i.e., when a new Vespa version is being rolled out
        VersionStatus versionStatus = controller.readVersionStatus();
        if (versionStatus.isUpgrading()) {
            throw new IllegalArgumentException("Deployment of system applications during a system upgrade is not allowed");
        }
        Optional<VespaVersion> systemVersion = versionStatus.systemVersion();
        if (systemVersion.isEmpty()) {
            throw new IllegalArgumentException("Deployment of system applications is not permitted until system version is determined");
        }
        DeploymentResult result = controller.applications()
                                            .deploySystemApplicationPackage(systemApplication.get(), zone, systemVersion.get().versionNumber());
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("message", "Deployed " + systemApplication.get() + " in " + zone + " on " + systemVersion.get().versionNumber());

        Cursor logArray = root.setArray("prepareMessages");
        for (LogEntry logMessage : result.log()) {
            Cursor logObject = logArray.addObject();
            logObject.setLong("time", logMessage.epochMillis());
            logObject.setString("level", logMessage.level().getName());
            logObject.setString("message", logMessage.message());
        }

        return new SlimeJsonResponse(slime);
    }

    private HttpResponse deleteTenant(String tenantName, HttpRequest request) {
        boolean forget = request.getBooleanProperty("forget");
        if (forget && ! isOperator(request))
            return ErrorResponse.forbidden("Only operators can forget a tenant");

        controller.tenants().delete(TenantName.from(tenantName),
                                    Optional.of(accessControlRequests.credentials(TenantName.from(tenantName),
                                                                      toSlime(request.getData()).get(),
                                                                      request.getJDiscRequest())),
                                    forget);

        return new MessageResponse("Deleted tenant " + tenantName);
    }

    private HttpResponse deleteApplication(String tenantName, String applicationName, HttpRequest request) {
        TenantAndApplicationId id = TenantAndApplicationId.from(tenantName, applicationName);
        Credentials credentials = accessControlRequests.credentials(id.tenant(), toSlime(request.getData()).get(), request.getJDiscRequest());
        controller.applications().deleteApplication(id, credentials);
        return new MessageResponse("Deleted application " + id);
    }

    private HttpResponse deleteInstance(String tenantName, String applicationName, String instanceName, HttpRequest request) {
        TenantAndApplicationId id = TenantAndApplicationId.from(tenantName, applicationName);
        controller.applications().deleteInstance(id.instance(instanceName));
        if (controller.applications().requireApplication(id).instances().isEmpty()) {
            Credentials credentials = accessControlRequests.credentials(id.tenant(), toSlime(request.getData()).get(), request.getJDiscRequest());
            controller.applications().deleteApplication(id, credentials);
        }
        return new MessageResponse("Deleted instance " + id.instance(instanceName).toFullString());
    }

    private HttpResponse deactivate(String tenantName, String applicationName, String instanceName, String environment, String region, HttpRequest request) {
        DeploymentId id = new DeploymentId(ApplicationId.from(tenantName, applicationName, instanceName),
                                           requireZone(environment, region));
        // Attempt to deactivate application even if the deployment is not known by the controller
        controller.applications().deactivate(id.applicationId(), id.zoneId());
        controller.jobController().last(id.applicationId(), JobType.deploymentTo(id.zoneId()))
                  .filter(run -> ! run.hasEnded())
                  .ifPresent(last -> controller.jobController().abort(last.id(), "deployment deactivated by " + request.getJDiscRequest().getUserPrincipal().getName()));
        return new MessageResponse("Deactivated " + id);
    }

    /** Returns test config for indicated job, with production deployments of the default instance if the given is not in deployment spec. */
    private HttpResponse testConfig(ApplicationId id, JobType type) {
        Application application = controller.applications().requireApplication(TenantAndApplicationId.from(id));
        ApplicationId prodInstanceId = application.deploymentSpec().instance(id.instance()).isPresent()
                                       ? id : TenantAndApplicationId.from(id).defaultInstance();
        HashSet<DeploymentId> deployments = controller.applications()
                                                      .getInstance(prodInstanceId).stream()
                                                      .flatMap(instance -> instance.productionDeployments().keySet().stream())
                                                      .map(zone -> new DeploymentId(prodInstanceId, zone))
                                                      .collect(Collectors.toCollection(HashSet::new));


        // If a production job is specified, the production deployment of the orchestrated instance is the relevant one,
        // as user instances should not exist in prod.
        ApplicationId toTest = type.isProduction() ? prodInstanceId : id;
        if ( ! type.isProduction())
            deployments.add(new DeploymentId(toTest, type.zone()));

        Deployment deployment = application.require(toTest.instance()).deployments().get(type.zone());
        if (deployment == null)
            throw new NotExistsException(toTest + " is not deployed in " + type.zone());

        return new SlimeJsonResponse(testConfigSerializer.configSlime(id,
                                                                      type,
                                                                      false,
                                                                      deployment.version(),
                                                                      deployment.revision(),
                                                                      deployment.at(),
                                                                      controller.routing().readTestRunnerEndpointsOf(deployments),
                                                                      controller.applications().reachableContentClustersByZone(deployments)));
    }

    private HttpResponse requestServiceDump(String tenant, String application, String instance, String environment,
                                            String region, String hostname, HttpRequest request) {
        NodeRepository nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        ZoneId zone = requireZone(environment, region);

        // Check that no other service dump is in progress
        Slime report = getReport(nodeRepository, zone, tenant, application, instance, hostname).orElse(null);
        if (report != null) {
            Cursor cursor = report.get();
            // Note: same behaviour for both value '0' and missing value.
            boolean force = request.getBooleanProperty("force");
            if (!force && cursor.field("failedAt").asLong() == 0 && cursor.field("completedAt").asLong() == 0) {
                throw new IllegalArgumentException("Service dump already in progress for " + cursor.field("configId").asString());
            }
        }
        Slime requestPayload;
        try {
            requestPayload = SlimeUtils.jsonToSlimeOrThrow(request.getData().readAllBytes());
        } catch (Exception e) {
            throw new IllegalArgumentException("Missing or invalid JSON in request content", e);
        }
        Cursor requestPayloadCursor = requestPayload.get();
        String configId = requestPayloadCursor.field("configId").asString();
        long expiresAt = requestPayloadCursor.field("expiresAt").asLong();
        if (configId.isEmpty()) {
            throw new IllegalArgumentException("Missing configId");
        }
        Cursor artifactsCursor = requestPayloadCursor.field("artifacts");
        int artifactEntries = artifactsCursor.entries();
        if (artifactEntries == 0) {
            throw new IllegalArgumentException("Missing or empty 'artifacts'");
        }

        Slime dumpRequest = new Slime();
        Cursor dumpRequestCursor = dumpRequest.setObject();
        dumpRequestCursor.setLong("createdMillis", controller.clock().millis());
        dumpRequestCursor.setString("configId", configId);
        Cursor dumpRequestArtifactsCursor = dumpRequestCursor.setArray("artifacts");
        for (int i = 0; i < artifactEntries; i++) {
            dumpRequestArtifactsCursor.addString(artifactsCursor.entry(i).asString());
        }
        if (expiresAt > 0) {
            dumpRequestCursor.setLong("expiresAt", expiresAt);
        }
        Cursor dumpOptionsCursor = requestPayloadCursor.field("dumpOptions");
        if (dumpOptionsCursor.children() > 0) {
            SlimeUtils.copyObject(dumpOptionsCursor, dumpRequestCursor.setObject("dumpOptions"));
        }
        var reportsUpdate = Map.of("serviceDump", new String(uncheck(() -> SlimeUtils.toJsonBytes(dumpRequest))));
        nodeRepository.updateReports(zone, hostname, reportsUpdate);
        boolean wait = request.getBooleanProperty("wait");
        if (!wait) return new MessageResponse("Request created");
        return waitForServiceDumpResult(nodeRepository, zone, tenant, application, instance, hostname);
    }

    private HttpResponse getServiceDump(String tenant, String application, String instance, String environment,
                                        String region, String hostname, HttpRequest request) {
        NodeRepository nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        ZoneId zone = requireZone(environment, region);
        Slime report = getReport(nodeRepository, zone, tenant, application, instance, hostname)
            .orElseThrow(() -> new NotExistsException("No service dump for node " + hostname));
        return new SlimeJsonResponse(report);
    }

    private HttpResponse waitForServiceDumpResult(NodeRepository nodeRepository, ZoneId zone, String tenant,
                                                  String application, String instance, String hostname) {
        int pollInterval = 2;
        Slime report;
        while (true) {
            report = getReport(nodeRepository, zone, tenant, application, instance, hostname).get();
            Cursor cursor = report.get();
            if (cursor.field("completedAt").asLong() > 0 || cursor.field("failedAt").asLong() > 0) {
                break;
            }
            final Slime copyForLambda = report;
            log.fine(() -> uncheck(() -> new String(SlimeUtils.toJsonBytes(copyForLambda))));
            log.fine("Sleeping " + pollInterval + " seconds before checking report status again");
            controller.sleeper().sleep(Duration.ofSeconds(pollInterval));
        }
        return new SlimeJsonResponse(report);
    }

    private Optional<Slime> getReport(NodeRepository nodeRepository, ZoneId zone, String tenant,
                                      String application, String instance, String hostname) {
        Node node;
        try {
            node = nodeRepository.getNode(zone, hostname);
        } catch (IllegalArgumentException e) {
            throw new NotExistsException(hostname);
        }
        ApplicationId app = ApplicationId.from(tenant, application, instance);
        ApplicationId owner = node.owner().orElseThrow(() -> new IllegalArgumentException("Node has no owner"));
        if (!app.equals(owner)) {
            throw new IllegalArgumentException("Node is not owned by " + app.toFullString());
        }
        String json = node.reports().get("serviceDump");
        if (json == null) return Optional.empty();
        return Optional.of(SlimeUtils.jsonToSlimeOrThrow(json));
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

    private void toSlime(Cursor object, Tenant tenant, List<Application> applications, HttpRequest request) {
        object.setString("tenant", tenant.name().value());
        object.setString("type", tenantType(tenant));
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
            case cloud: {
                CloudTenant cloudTenant = (CloudTenant) tenant;

                cloudTenant.creator().ifPresent(creator -> object.setString("creator", creator.getName()));
                Cursor pemDeveloperKeysArray = object.setArray("pemDeveloperKeys");
                cloudTenant.developerKeys().forEach((key, user) -> {
                    Cursor keyObject = pemDeveloperKeysArray.addObject();
                    keyObject.setString("key", KeyUtils.toPem(key));
                    keyObject.setString("user", user.getName());
                });

                // TODO: remove this once console is updated
                toSlime(object, cloudTenant.tenantSecretStores());

                toSlime(object.setObject("integrations").setObject("aws"),
                        controller.serviceRegistry().roleService().getTenantRole(tenant.name()),
                        cloudTenant.tenantSecretStores());

                try {
                    var usedQuota = applications.stream()
                            .map(Application::quotaUsage)
                            .reduce(QuotaUsage.none, QuotaUsage::add);

                    toSlime(object.setObject("quota"), usedQuota);
                } catch (Exception e) {
                    log.warning(String.format("Failed to get quota for tenant %s: %s", tenant.name(), Exceptions.toMessageString(e)));
                }

                toSlime(cloudTenant.archiveAccess(), object.setObject("archiveAccess"));

                break;
            }
            case deleted: break;
            default: throw new IllegalArgumentException("Unexpected tenant type '" + tenant.type() + "'.");
        }
        // TODO jonmv: This should list applications, not instances.
        Cursor applicationArray = object.setArray("applications");
        for (Application application : applications) {
            DeploymentStatus status = null;
            Collection<Instance> instances = showOnlyProductionInstances(request) ? application.productionInstances().values()
                                                                                  : application.instances().values();

            if (instances.isEmpty() && !showOnlyActiveInstances(request))
                toSlime(application.id(), applicationArray.addObject(), request);

            for (Instance instance : instances) {
                if (showOnlyActiveInstances(request) && instance.deployments().isEmpty())
                    continue;
                if (recurseOverApplications(request)) {
                    if (status == null) status = controller.jobController().deploymentStatus(application);
                    toSlime(applicationArray.addObject(), instance, status, request);
                } else {
                    toSlime(instance.id(), applicationArray.addObject(), request);
                }
            }
        }
        tenantMetaDataToSlime(tenant, applications, object.setObject("metaData"));
    }

    private void toSlime(ArchiveAccess archiveAccess, Cursor object) {
        archiveAccess.awsRole().ifPresent(role -> object.setString("awsRole", role));
        archiveAccess.gcpMember().ifPresent(member -> object.setString("gcpMember", member));
    }

    private void toSlime(Cursor object, QuotaUsage usage) {
        object.setDouble("budgetUsed", usage.rate());
    }

    private void toSlime(ClusterResources resources, Cursor object) {
        object.setLong("nodes", resources.nodes());
        object.setLong("groups", resources.groups());
        toSlime(resources.nodeResources(), object.setObject("nodeResources"));

        double cost = ResourceMeterMaintainer.cost(resources, controller.serviceRegistry().zoneRegistry().system());
        object.setDouble("cost", cost);
    }

    private void toSlime(IntRange range, Cursor object) {
        range.from().ifPresent(from -> object.setLong("from", from));
        range.to().ifPresent(to -> object.setLong("to", to));
    }

    private void toSlime(Cluster.Autoscaling autoscaling, Cursor autoscalingObject) {
        autoscalingObject.setString("status", autoscaling.status());
        autoscalingObject.setString("description", autoscaling.description());
        autoscaling.resources().ifPresent(resources -> toSlime(resources, autoscalingObject.setObject("resources")));
        autoscalingObject.setLong("at", autoscaling.at().toEpochMilli());
        toSlime(autoscaling.peak(), autoscalingObject.setObject("peak"));
        toSlime(autoscaling.ideal(), autoscalingObject.setObject("ideal"));
    }

    private void toSlime(Load load, Cursor loadObject) {
        loadObject.setDouble("cpu", load.cpu());
        loadObject.setDouble("memory", load.memory());
        loadObject.setDouble("disk", load.disk());
    }

    private void scalingEventsToSlime(List<Cluster.ScalingEvent> scalingEvents, Cursor scalingEventsArray) {
        for (Cluster.ScalingEvent scalingEvent : scalingEvents) {
            Cursor scalingEventObject = scalingEventsArray.addObject();
            toSlime(scalingEvent.from(), scalingEventObject.setObject("from"));
            toSlime(scalingEvent.to(), scalingEventObject.setObject("to"));
            scalingEventObject.setLong("at", scalingEvent.at().toEpochMilli());
            scalingEvent.completion().ifPresent(completion -> scalingEventObject.setLong("completion", completion.toEpochMilli()));
        }
    }

    private void toSlime(NodeResources resources, Cursor object) {
        object.setDouble("vcpu", resources.vcpu());
        object.setDouble("memoryGb", resources.memoryGb());
        object.setDouble("diskGb", resources.diskGb());
        object.setDouble("bandwidthGbps", resources.bandwidthGbps());
        object.setString("diskSpeed", valueOf(resources.diskSpeed()));
        object.setString("storageType", valueOf(resources.storageType()));
    }

    // A tenant has different content when in a list ... antipattern, but not solvable before application/v5
    private void tenantInTenantsListToSlime(Tenant tenant, URI requestURI, Cursor object) {
        object.setString("tenant", tenant.name().value());
        Cursor metaData = object.setObject("metaData");
        metaData.setString("type", tenantType(tenant));
        switch (tenant.type()) {
            case athenz:
                AthenzTenant athenzTenant = (AthenzTenant) tenant;
                metaData.setString("athensDomain", athenzTenant.domain().getName());
                metaData.setString("property", athenzTenant.property().id());
                break;
            case cloud: break;
            case deleted: break;
            default: throw new IllegalArgumentException("Unexpected tenant type '" + tenant.type() + "'.");
        }
        object.setString("url", withPath("/application/v4/tenant/" + tenant.name().value(), requestURI).toString());
    }

    private void tenantMetaDataToSlime(Tenant tenant, List<Application> applications, Cursor object) {
        Optional<Instant> lastDev = applications.stream()
                                                .flatMap(application -> application.instances().values().stream())
                                                .flatMap(instance -> instance.deployments().values().stream()
                                                                             .filter(deployment -> deployment.zone().environment() == Environment.dev)
                                                                             .map(deployment -> controller.jobController().lastDeploymentStart(instance.id(), deployment)))
                                                .max(Comparator.naturalOrder())
                                                .or(() -> applications.stream()
                                                                      .flatMap(application -> application.instances().values().stream())
                                                                      .flatMap(instance -> JobType.allIn(controller.zoneRegistry()).stream()
                                                                                                  .filter(job -> job.environment() == Environment.dev)
                                                                                                  .flatMap(jobType -> controller.jobController().last(instance.id(), jobType).stream()))
                                                                      .map(Run::start)
                                                                      .max(Comparator.naturalOrder()));
        Optional<Instant> lastSubmission = applications.stream()
                                                       .flatMap(app -> app.revisions().last().flatMap(ApplicationVersion::buildTime).stream())
                                                       .max(Comparator.naturalOrder());
        object.setLong("createdAtMillis", tenant.createdAt().toEpochMilli());
        if (tenant.type() == Tenant.Type.deleted)
            object.setLong("deletedAtMillis", ((DeletedTenant) tenant).deletedAt().toEpochMilli());
        lastDev.ifPresent(instant -> object.setLong("lastDeploymentToDevMillis", instant.toEpochMilli()));
        lastSubmission.ifPresent(instant -> object.setLong("lastSubmissionToProdMillis", instant.toEpochMilli()));

        tenant.lastLoginInfo().get(LastLoginInfo.UserLevel.user)
                .ifPresent(instant -> object.setLong("lastLoginByUserMillis", instant.toEpochMilli()));
        tenant.lastLoginInfo().get(LastLoginInfo.UserLevel.developer)
                .ifPresent(instant -> object.setLong("lastLoginByDeveloperMillis", instant.toEpochMilli()));
        tenant.lastLoginInfo().get(LastLoginInfo.UserLevel.administrator)
                .ifPresent(instant -> object.setLong("lastLoginByAdministratorMillis", instant.toEpochMilli()));
    }

    /** Returns a copy of the given URI with the host and port from the given URI, the path set to the given path and the query set to given query*/
    private URI withPathAndQuery(String newPath, String newQuery, URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), newPath, newQuery, null);
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Will not happen", e);
        }
    }

    /** Returns a copy of the given URI with the host and port from the given URI and the path set to the given path */
    private URI withPath(String newPath, URI uri) {
        return withPathAndQuery(newPath, null, uri);
    }

    private String toPath(DeploymentId id) {
        return path("/application", "v4",
                    "tenant", id.applicationId().tenant(),
                    "application", id.applicationId().application(),
                    "instance", id.applicationId().instance(),
                    "environment", id.zoneId().environment(),
                    "region", id.zoneId().region());
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
        if (principal == null) throw new IllegalArgumentException("Expected a user principal");
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

    private void toSlime(TenantAndApplicationId id, Cursor object, HttpRequest request) {
        object.setString("tenant", id.tenant().value());
        object.setString("application", id.application().value());
        object.setString("url", withPath("/application/v4" +
                                         "/tenant/" + id.tenant().value() +
                                         "/application/" + id.application().value(),
                                         request.getUri()).toString());
    }

    private void toSlime(ApplicationId id, Cursor object, HttpRequest request) {
        object.setString("tenant", id.tenant().value());
        object.setString("application", id.application().value());
        object.setString("instance", id.instance().value());
        object.setString("url", withPath("/application/v4" +
                                         "/tenant/" + id.tenant().value() +
                                         "/application/" + id.application().value() +
                                         "/instance/" + id.instance().value(),
                                         request.getUri()).toString());
    }

    private void stringsToSlime(List<String> strings, Cursor array) {
        for (String string : strings)
            array.addString(string);
    }

    private void toSlime(Cursor object, List<TenantSecretStore> tenantSecretStores) {
        Cursor secretStore = object.setArray("secretStores");
        tenantSecretStores.forEach(store -> {
            toSlime(secretStore.addObject(), store);
        });
    }

    private void toSlime(Cursor object, TenantRoles tenantRoles, List<TenantSecretStore> tenantSecretStores) {
        object.setString("tenantRole", tenantRoles.containerRole());
        var stores = object.setArray("accounts");
        tenantSecretStores.forEach(secretStore -> {
            toSlime(stores.addObject(), secretStore);
        });
    }

    private void toSlime(Cursor object, TenantSecretStore secretStore) {
        object.setString("name", secretStore.getName());
        object.setString("awsId", secretStore.getAwsId());
        object.setString("role", secretStore.getRole());
    }

    private String readToString(InputStream stream) {
        Scanner scanner = new Scanner(stream).useDelimiter("\\A");
        if ( ! scanner.hasNext()) return null;
        return scanner.next();
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

    private static boolean showOnlyProductionInstances(HttpRequest request) {
        return "true".equals(request.getProperty("production"));
    }

    private static boolean showOnlyActiveInstances(HttpRequest request) {
        return "true".equals(request.getProperty("activeInstances"));
    }

    private static boolean includeDeleted(HttpRequest request) {
        return "true".equals(request.getProperty("includeDeleted"));
    }

    private static String tenantType(Tenant tenant) {
        return switch (tenant.type()) {
            case athenz: yield "ATHENS";
            case cloud: yield "CLOUD";
            case deleted: yield "DELETED";
        };
    }

    private static ApplicationId appIdFromPath(Path path) {
        return ApplicationId.from(path.get("tenant"), path.get("application"), path.get("instance"));
    }

    private JobType jobTypeFromPath(Path path) {
        return JobType.fromJobName(path.get("jobtype"), controller.zoneRegistry());
    }

    private RunId runIdFromPath(Path path) {
        long number = Long.parseLong(path.get("number"));
        return new RunId(appIdFromPath(path), jobTypeFromPath(path), number);
    }

    private HttpResponse submit(String tenant, String application, HttpRequest request) {
        Map<String, byte[]> dataParts = parseDataParts(request);
        Inspector submitOptions = SlimeUtils.jsonToSlime(dataParts.get(EnvironmentResource.SUBMIT_OPTIONS)).get();
        long projectId = submitOptions.field("projectId").asLong(); // Absence of this means it's not a prod app :/
        projectId = projectId == 0 ? 1 : projectId;
        Optional<String> repository = optional("repository", submitOptions);
        Optional<String> branch = optional("branch", submitOptions);
        Optional<String> commit = optional("commit", submitOptions);
        Optional<SourceRevision> sourceRevision = repository.isPresent() && branch.isPresent() && commit.isPresent()
                                                  ? Optional.of(new SourceRevision(repository.get(), branch.get(), commit.get()))
                                                  : Optional.empty();
        Optional<String> sourceUrl = optional("sourceUrl", submitOptions);
        Optional<String> authorEmail = optional("authorEmail", submitOptions);
        Optional<String> description = optional("description", submitOptions);
        int risk = (int) submitOptions.field("risk").asLong();

        sourceUrl.map(URI::create).ifPresent(url -> {
            if (url.getHost() == null || url.getScheme() == null)
                throw new IllegalArgumentException("Source URL must include scheme and host");
        });

        ApplicationPackage applicationPackage = new ApplicationPackage(dataParts.get(EnvironmentResource.APPLICATION_ZIP), true);

        byte[] testPackage = dataParts.getOrDefault(EnvironmentResource.APPLICATION_TEST_ZIP, new byte[0]);
        Submission submission = new Submission(applicationPackage, testPackage, sourceUrl, sourceRevision, authorEmail, description, risk);

        controller.applications().verifyApplicationIdentityConfiguration(TenantName.from(tenant),
                                                                         Optional.empty(),
                                                                         Optional.empty(),
                                                                         applicationPackage,
                                                                         Optional.of(requireUserPrincipal(request)));

        TenantAndApplicationId id = TenantAndApplicationId.from(tenant, application);
        ensureApplicationExists(id, request);
        return JobControllerApiHandlerHelper.submitResponse(controller.jobController(), id, submission, projectId);
    }

    private HttpResponse removeAllProdDeployments(String tenant, String application) {
        JobControllerApiHandlerHelper.submitResponse(controller.jobController(),
                                                     TenantAndApplicationId.from(tenant, application),
                                                     new Submission(ApplicationPackage.deploymentRemoval(), new byte[0], Optional.empty(),
                                                                    Optional.empty(), Optional.empty(), Optional.empty(), 0),
                                                     0);
        return new MessageResponse("All deployments removed");
    }

    private ZoneId requireZone(String environment, String region) {
        return requireZone(ZoneId.from(environment, region));
    }

    private ZoneId requireZone(ZoneId zone) {
        // TODO(mpolden): Find a way to not hardcode this. Some APIs allow this "virtual" zone, e.g. /logs
        if (zone.environment() == Environment.prod && zone.region().value().equals("controller")) {
            return zone;
        }
        if (!controller.zoneRegistry().hasZone(zone)) {
            throw new IllegalArgumentException("Zone " + zone + " does not exist in this system");
        }
        return zone;
    }

    private static Map<String, byte[]> parseDataParts(HttpRequest request) {
        String contentHash = request.getHeader("X-Content-Hash");
        if (contentHash == null)
            return new MultipartParser().parse(request);

        DigestInputStream digester = Signatures.sha256Digester(request.getData());
        var dataParts = new MultipartParser().parse(request.getHeader("Content-Type"), digester, request.getUri());
        if ( ! Arrays.equals(digester.getMessageDigest().digest(), Base64.getDecoder().decode(contentHash)))
            throw new IllegalArgumentException("Value of X-Content-Hash header does not match computed content hash");

        return dataParts;
    }

    private static RotationId findRotationId(Instance instance, Optional<String> endpointId) {
        if (instance.rotations().isEmpty()) {
            throw new NotExistsException("global rotation does not exist for " + instance);
        }
        if (endpointId.isPresent()) {
            return instance.rotations().stream()
                           .filter(r -> r.endpointId().id().equals(endpointId.get()))
                           .map(AssignedRotation::rotationId)
                           .findFirst()
                           .orElseThrow(() -> new NotExistsException("endpoint " + endpointId.get() +
                                                                     " does not exist for " + instance));
        } else if (instance.rotations().size() > 1) {
            throw new IllegalArgumentException(instance + " has multiple rotations. Query parameter 'endpointId' must be given");
        }
        return instance.rotations().get(0).rotationId();
    }

    private static String rotationStateString(RotationState state) {
        return switch (state) {
            case in: yield "IN";
            case out: yield "OUT";
            case unknown: yield "UNKNOWN";
        };
    }

    private static String endpointScopeString(Endpoint.Scope scope) {
        return switch (scope) {
            case weighted: yield "weighted";
            case application: yield "application";
            case global: yield "global";
            case zone: yield "zone";
        };
    }

    private static String routingMethodString(RoutingMethod method) {
        return switch (method) {
            case exclusive: yield "exclusive";
            case sharedLayer4: yield "sharedLayer4";
        };
    }

    private static <T> T getAttribute(HttpRequest request, String attributeName, Class<T> cls) {
        return Optional.ofNullable(request.getJDiscRequest().context().get(attributeName))
                       .filter(cls::isInstance)
                       .map(cls::cast)
                       .orElseThrow(() -> new IllegalArgumentException("Attribute '" + attributeName + "' was not set on request"));
    }

    /** Returns whether given request is by an operator */
    private static boolean isOperator(HttpRequest request) {
        var securityContext = getAttribute(request, SecurityContext.ATTRIBUTE_NAME, SecurityContext.class);
        return securityContext.roles().stream()
                              .map(Role::definition)
                              .anyMatch(definition -> definition == RoleDefinition.hostedOperator);
    }

    private void ensureApplicationExists(TenantAndApplicationId id, HttpRequest request) {
        if (controller.applications().getApplication(id).isEmpty()) {
            if (controller.system().isPublic() || hasOktaContext(request)) {
                log.fine("Application does not exist in public, creating: " + id);
                var credentials = accessControlRequests.credentials(id.tenant(), null /* not used on public */ , request.getJDiscRequest());
                controller.applications().createApplication(id, credentials);
            } else {
                log.fine("Application does not exist in hosted, failing: " + id);
                throw new IllegalArgumentException("Application does not exist. Create application in Console first.");
            }
        }
    }

    private boolean hasOktaContext(HttpRequest request) {
        try {
            OAuthCredentials.fromOktaRequestContext(request.getJDiscRequest().context());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private List<Deployment> sortedDeployments(Collection<Deployment> deployments, DeploymentInstanceSpec spec) {
        List<ZoneId> productionZones = spec.zones().stream()
                                           .filter(z -> z.region().isPresent())
                                           .map(z -> ZoneId.from(z.environment(), z.region().get()))
                                           .toList();
        return deployments.stream()
                          .sorted(comparingInt(deployment -> productionZones.indexOf(deployment.zone())))
                          .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

}

