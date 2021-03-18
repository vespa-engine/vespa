package com.yahoo.vespa.hosted.controller.restapi.changemanagement;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.maintenance.ChangeManagementAssessor;
import com.yahoo.yolean.Exceptions;

import javax.ws.rs.BadRequestException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ChangeManagementApiHandler extends AuditLoggingRequestHandler {

    private final ChangeManagementAssessor assessor;
    private final Controller controller;

    public ChangeManagementApiHandler(LoggingRequestHandler.Context ctx, Controller controller) {
        super(ctx, controller.auditLogger());
        this.assessor = new ChangeManagementAssessor(controller);
        this.controller = controller;
    }

    @Override
    public HttpResponse auditAndHandle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET:
                    return get(request);
                case POST:
                    return post(request);
                default:
                    return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/changemanagement/v1/assessment/{changeRequestId}")) return changeRequestAssessment(path.get("changeRequestId"));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse post(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/changemanagement/v1/assessment")) return new SlimeJsonResponse(doAssessment(request));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private Inspector inspectorOrThrow(HttpRequest request) {
        try {
            return SlimeUtils.jsonToSlime(request.getData().readAllBytes()).get();
        } catch (IOException e) {
            throw new BadRequestException("Failed to parse request body");
        }
    }

    private static Inspector getInspectorFieldOrThrow(Inspector inspector, String field) {
        if (!inspector.field(field).valid())
            throw new BadRequestException("Field " + field + " cannot be null");
        return inspector.field(field);
    }

    private HttpResponse changeRequestAssessment(String changeRequestId) {
        var optionalChangeRequest = controller.serviceRegistry().changeRequestClient()
                .getUpcomingChangeRequests()
                .stream()
                .filter(request -> changeRequestId.equals(request.getChangeRequestSource().getId()))
                .findFirst();

        if (optionalChangeRequest.isEmpty())
            return ErrorResponse.notFoundError("Could not find any upcoming change requests with id " + changeRequestId);

        var changeRequest = optionalChangeRequest.get();
        var zone = affectedZone(changeRequest);

        if (zone.isEmpty())
            return ErrorResponse.notFoundError("Could not find prod zone affected by change request " + changeRequestId);

        var assessment = doAssessment(changeRequest.getImpactedHosts(), zone.get());
        return new SlimeJsonResponse(assessment);
    }

    // The structure here should be
    //
    // {
    //   zone: string
    //   hosts: string[]
    //   switches: string[]
    //   switchInSequence: boolean
    // }
    //
    // Only zone and host are supported right now
    private Slime doAssessment(HttpRequest request) {

        Inspector inspector = inspectorOrThrow(request);

        // For now; mandatory fields
        String zoneStr = getInspectorFieldOrThrow(inspector, "zone").asString();
        Inspector hostArray = getInspectorFieldOrThrow(inspector, "hosts");

        // The impacted hostnames
        List<String> hostNames = new ArrayList<>();
        if (hostArray.valid()) {
            hostArray.traverse((ArrayTraverser) (i, host) -> hostNames.add(host.asString()));
        }

        return doAssessment(hostNames, ZoneId.from(zoneStr));
    }

    private Slime doAssessment(List<String> hostNames, ZoneId zoneId) {
        List<ChangeManagementAssessor.Assessment> assessments = assessor.assessment(hostNames, zoneId);

        Slime slime = new Slime();
        Cursor root = slime.setObject();

        // This is the main structure that might be part of something bigger later
        Cursor assessmentCursor = root.setObject("assessment");

        // Updated gives clue to if the assessment is old
        assessmentCursor.setString("updated", "2021-03-12:12:12:12Z");

        // Assessment on the cluster level
        Cursor clustersCursor = assessmentCursor.setArray("clusters");

        assessments.forEach(assessment -> {
            Cursor oneCluster = clustersCursor.addObject();
            oneCluster.setString("app", assessment.app);
            oneCluster.setString("zone", assessment.zone);
            oneCluster.setString("cluster", assessment.cluster);
            oneCluster.setLong("clusterSize", assessment.clusterSize);
            oneCluster.setLong("clusterImpact", assessment.clusterImpact);
            oneCluster.setLong("groupsTotal", assessment.groupsTotal);
            oneCluster.setLong("groupsImpact", assessment.groupsImpact);
            oneCluster.setString("upgradePolicy", assessment.upgradePolicy);
            oneCluster.setString("suggestedAction", assessment.suggestedAction);
            oneCluster.setString("impact", assessment.impact);
        });

        // Assessment on the host level - TODO
        assessmentCursor.setArray("hosts");

        return slime;
    }

    private Optional<ZoneId> affectedZone(ChangeRequest changeRequest) {
        var affectedHosts = changeRequest.getImpactedHosts()
                .stream()
                .map(HostName::from)
                .collect(Collectors.toList());

        var potentialZones = controller.zoneRegistry()
                .zones()
                .reachable()
                .in(Environment.prod)
                .ids();

        for (var zone : potentialZones) {
            var affectedHostsInZone = controller.serviceRegistry().configServer().nodeRepository().list(zone, affectedHosts);
            if (!affectedHostsInZone.isEmpty())
                return Optional.of(zone);
        }

        return Optional.empty();
    }

}