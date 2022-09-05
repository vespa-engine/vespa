// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.ResourceResponse;
import com.yahoo.restapi.RestApiException;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.Text;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.maintenance.ControllerMaintenance;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.vespa.hosted.controller.support.access.SupportAccess;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Scanner;
import java.util.function.Function;

/**
 * This implements the controller/v1 API which provides operators with information about,
 * and control over the Controller.
 * 
 * @author bratseth
 */
@SuppressWarnings("unused") // Created by injection
public class ControllerApiHandler extends AuditLoggingRequestHandler {

    private final ControllerMaintenance maintenance;
    private final Controller controller;

    public ControllerApiHandler(ThreadedHttpRequestHandler.Context parentCtx, Controller controller, ControllerMaintenance maintenance) {
        super(parentCtx, controller.auditLogger());
        this.controller = controller;
        this.maintenance = maintenance;
    }

    @Override
    public HttpResponse auditAndHandle(HttpRequest request) {
        try {
            return switch (request.getMethod()) {
                case GET -> get(request);
                case POST -> post(request);
                case DELETE -> delete(request);
                case PATCH -> patch(request);
                default -> ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            };
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }
    
    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/controller/v1/")) return root(request);
        if (path.matches("/controller/v1/auditlog/")) return new AuditLogResponse(controller.auditLogger().readLog());
        if (path.matches("/controller/v1/maintenance/")) return new JobsResponse(controller.jobControl());
        if (path.matches("/controller/v1/stats")) return new StatsResponse(controller);
        if (path.matches("/controller/v1/jobs/upgrader")) return new UpgraderResponse(maintenance.upgrader());
        if (path.matches("/controller/v1/metering/tenant/{tenant}/month/{month}")) return new MeteringResponse(controller.serviceRegistry().resourceDatabase(), path.get("tenant"), path.get("month"));
        return notFound(path);
    }

    private HttpResponse post(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/controller/v1/jobs/upgrader/confidence/{version}")) return overrideConfidence(request, path.get("version"));
        if (path.matches("/controller/v1/access/requests/{user}")) return approveMembership(request, path.get("user"));
        if (path.matches("/controller/v1/access/grants/{user}")) return grantAccess(request, path.get("user"));
        return notFound(path);
    }

    private HttpResponse approveMembership(HttpRequest request, String user) {
        AthenzUser athenzUser = AthenzUser.fromUserId(user);
        byte[] jsonBytes = toJsonBytes(request.getData());
        Inspector inspector = SlimeUtils.jsonToSlime(jsonBytes).get();
        ApplicationId applicationId = requireField(inspector, "applicationId", ApplicationId::fromSerializedForm);
        ZoneId zone = requireField(inspector, "zone", ZoneId::from);
        if(controller.supportAccess().allowDataplaneMembership(athenzUser, new DeploymentId(applicationId, zone))) {
            return new AccessRequestResponse(controller.serviceRegistry().accessControlService().listMembers());
        } else {
            return new MessageResponse(400, "Unable to approve membership request");
        }
    }

    private HttpResponse grantAccess(HttpRequest request, String user) {
        Principal principal = requireUserPrincipal(request);
        Instant now = controller.clock().instant();

        byte[] jsonBytes = toJsonBytes(request.getData());
        Inspector requestObject = SlimeUtils.jsonToSlime(jsonBytes).get();
        X509Certificate certificate = requireField(requestObject, "certificate", X509CertificateUtils::fromPem);
        ApplicationId applicationId = requireField(requestObject, "applicationId", ApplicationId::fromSerializedForm);
        ZoneId zone = requireField(requestObject, "zone", ZoneId::from);
        DeploymentId deployment = new DeploymentId(applicationId, zone);

        // Register grant
        SupportAccess supportAccess = controller.supportAccess().registerGrant(deployment, principal.getName(), certificate);

        // Trigger deployment to include operator cert
        Optional<JobId> jobId = controller.applications().deploymentTrigger().reTriggerOrAddToQueue(deployment, "re-triggered to grant access, by " + request.getJDiscRequest().getUserPrincipal().getName());
        return new MessageResponse(
                jobId.map(id -> Text.format("Operator %s granted access and job %s triggered", principal.getName(), id.type().jobName()))
                        .orElseGet(() -> Text.format("Operator %s granted access and job trigger queued", principal.getName())));
    }

    private <T> T requireField(Inspector inspector, String field, Function<String, T> mapper) {
        return SlimeUtils.optionalString(inspector.field(field))
                .map(mapper::apply)
                .orElseThrow(() -> new IllegalArgumentException("Expected field \"" + field + "\" in request"));
    }

    private HttpResponse delete(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/controller/v1/jobs/upgrader/confidence/{version}")) return removeConfidenceOverride(path.get("version"));
        return notFound(path);
    }

    private HttpResponse patch(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/controller/v1/jobs/upgrader")) return configureUpgrader(request);
        return notFound(path);
    }

    private HttpResponse notFound(Path path) { return ErrorResponse.notFoundError("Nothing at " + path); }

    private HttpResponse root(HttpRequest request) {
        return new ResourceResponse(request, "auditlog", "maintenance", "stats", "jobs/upgrader", "metering/tenant");
    }

    private HttpResponse configureUpgrader(HttpRequest request) {
        String upgradesPerMinuteField = "upgradesPerMinute";

        byte[] jsonBytes = toJsonBytes(request.getData());
        Inspector inspect = SlimeUtils.jsonToSlime(jsonBytes).get();
        Upgrader upgrader = maintenance.upgrader();

        if (inspect.field(upgradesPerMinuteField).valid()) {
            upgrader.setUpgradesPerMinute(inspect.field(upgradesPerMinuteField).asDouble());
        } else {
            return ErrorResponse.badRequest("No such modifiable field(s)");
        }

        return new UpgraderResponse(maintenance.upgrader());
    }

    private HttpResponse removeConfidenceOverride(String version) {
        maintenance.upgrader().removeConfidenceOverride(Version.fromString(version));
        return new UpgraderResponse(maintenance.upgrader());
    }

    private HttpResponse overrideConfidence(HttpRequest request, String version) {
        Confidence confidence = Confidence.valueOf(asString(request.getData()));
        maintenance.upgrader().overrideConfidence(Version.fromString(version), confidence);
        return new UpgraderResponse(maintenance.upgrader());
    }

    private static String asString(InputStream in) {
        Scanner scanner = new Scanner(in).useDelimiter("\\A");
        if (scanner.hasNext()) {
            return scanner.next();
        }
        return "";
    }

    private static byte[] toJsonBytes(InputStream jsonStream) {
        try {
            return IOUtils.readBytes(jsonStream, 1000 * 1000);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Principal requireUserPrincipal(HttpRequest request) {
        Principal principal = request.getJDiscRequest().getUserPrincipal();
        if (principal == null) throw new RestApiException.InternalServerError("Expected a user principal");
        return principal;
    }
}
