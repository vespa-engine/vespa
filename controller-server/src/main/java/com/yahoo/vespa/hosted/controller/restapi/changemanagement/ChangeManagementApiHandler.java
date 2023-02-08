// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.changemanagement;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.maintenance.ChangeManagementAssessor;
import com.yahoo.vespa.hosted.controller.persistence.ChangeRequestSerializer;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ChangeManagementApiHandler extends AuditLoggingRequestHandler {

    private final ChangeManagementAssessor assessor;
    private final Controller controller;

    public ChangeManagementApiHandler(ThreadedHttpRequestHandler.Context ctx, Controller controller) {
        super(ctx, controller.auditLogger());
        this.assessor = new ChangeManagementAssessor(controller.serviceRegistry().configServer().nodeRepository());
        this.controller = controller;
    }

    @Override
    public HttpResponse auditAndHandle(HttpRequest request) {
        try {
            return switch (request.getMethod()) {
                case GET -> get(request);
                case POST -> post(request);
                case PATCH -> patch(request);
                case DELETE -> delete(request);
                default -> ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
            };
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/changemanagement/v1/assessment/{changeRequestId}")) return changeRequestAssessment(path.get("changeRequestId"));
        if (path.matches("/changemanagement/v1/vcmr")) return getVCMRs();
        if (path.matches("/changemanagement/v1/vcmr/{vcmrId}")) return getVCMR(path.get("vcmrId"));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse post(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/changemanagement/v1/assessment")) return doAssessment(request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse patch(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/changemanagement/v1/vcmr/{vcmrId}")) return patchVCMR(request, path.get("vcmrId"));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse delete(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/changemanagement/v1/vcmr/{vcmrId}")) return deleteVCMR(path.get("vcmrId"));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private Inspector inspectorOrThrow(HttpRequest request) {
        try {
            return SlimeUtils.jsonToSlime(request.getData().readAllBytes()).get();
        } catch (IOException e) {
            throw new RestApiException.BadRequest("Failed to parse request body");
        }
    }

    private static Inspector getInspectorFieldOrThrow(Inspector inspector, String field) {
        if (!inspector.field(field).valid())
            throw new RestApiException.BadRequest("Field " + field + " cannot be null");
        return inspector.field(field);
    }

    private HttpResponse changeRequestAssessment(String changeRequestId) {
        var optionalChangeRequest = controller.curator().readChangeRequests()
                .stream()
                .filter(request -> changeRequestId.equals(request.getChangeRequestSource().getId()))
                .findFirst();

        if (optionalChangeRequest.isEmpty())
            return ErrorResponse.notFoundError("Could not find any upcoming change requests with id " + changeRequestId);

        var changeRequest = optionalChangeRequest.get();

        return doAssessment(changeRequest.getImpactedHosts());
    }

    // The structure here should be
    //
    // {
    //   hosts: string[]
    //   switches: string[]
    //   switchInSequence: boolean
    // }
    //
    // Only hosts is supported right now
    private HttpResponse doAssessment(HttpRequest request) {

        Inspector inspector = inspectorOrThrow(request);

        // For now; mandatory fields
        Inspector hostArray = inspector.field("hosts");
        Inspector switchArray = inspector.field("switches");


        // The impacted hostnames
        List<String> hostNames = new ArrayList<>();
        if (hostArray.valid()) {
            hostArray.traverse((ArrayTraverser) (i, host) -> hostNames.add(host.asString()));
        }

        if (switchArray.valid()) {
            List<String> switchNames = new ArrayList<>();
            switchArray.traverse((ArrayTraverser) (i, switchName) -> switchNames.add(switchName.asString()));
            hostNames.addAll(hostsOnSwitch(switchNames));
        }

        if (hostNames.isEmpty())
            return ErrorResponse.badRequest("No prod hosts in provided host/switch list");

        return doAssessment(hostNames);
    }

    private HttpResponse doAssessment(List<String> hostNames) {
        var zone = affectedZone(hostNames);
        if (zone.isEmpty())
            return ErrorResponse.notFoundError("Could not infer prod zone from host list:  " + hostNames);

        ChangeManagementAssessor.Assessment assessments = assessor.assessment(hostNames, zone.get());

        Slime slime = new Slime();
        Cursor root = slime.setObject();

        // This is the main structure that might be part of something bigger later
        Cursor assessmentCursor = root.setObject("assessment");

        // Updated gives clue to if the assessment is old
        assessmentCursor.setString("updated", "2021-03-12:12:12:12Z");

        // Assessment on the cluster level
        Cursor clustersCursor = assessmentCursor.setArray("clusters");

        assessments.getClusterAssessments().forEach(assessment -> {
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

        Cursor hostsCursor = assessmentCursor.setArray("hosts");
        assessments.getHostAssessments().forEach(assessment -> {
            Cursor hostObject = hostsCursor.addObject();
            hostObject.setString("hostname", assessment.hostName);
            hostObject.setString("switchName", assessment.switchName);
            hostObject.setLong("numberOfChildren", assessment.numberOfChildren);
            hostObject.setLong("numberOfProblematicChildren", assessment.numberOfProblematicChildren);
        });

        return new SlimeJsonResponse(slime);
    }

    private HttpResponse getVCMRs() {
        var changeRequests = controller.curator().readChangeRequests();
        var slime = new Slime();
        var cursor = slime.setObject().setArray("vcmrs");
        changeRequests.forEach(changeRequest -> {
            var changeCursor = cursor.addObject();
            ChangeRequestSerializer.writeChangeRequest(changeCursor, changeRequest);
        });
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse getVCMR(String vcmrId) {
        var changeRequest = controller.curator().readChangeRequest(vcmrId);

        if (changeRequest.isEmpty()) {
            return ErrorResponse.notFoundError("No VCMR with id: " + vcmrId);
        }

        var slime = new Slime();
        var cursor = slime.setObject();

        ChangeRequestSerializer.writeChangeRequest(cursor, changeRequest.get());
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse patchVCMR(HttpRequest request, String vcmrId) {
        var optionalChangeRequest = controller.curator().readChangeRequest(vcmrId);

        if (optionalChangeRequest.isEmpty()) {
            return ErrorResponse.notFoundError("No VCMR with id: " + vcmrId);
        }

        var changeRequest = optionalChangeRequest.get();
        var inspector = inspectorOrThrow(request);

        if (inspector.field("approval").valid()) {
            var approval = ChangeRequest.Approval.valueOf(inspector.field("approval").asString());
            changeRequest = changeRequest.withApproval(approval);
        }

        if (inspector.field("actionPlan").valid()) {
            var actionPlan = ChangeRequestSerializer.readHostActionPlan(inspector.field("actionPlan"));
            changeRequest = changeRequest.withActionPlan(actionPlan);
        }

        if (inspector.field("status").valid()) {
            var status = VespaChangeRequest.Status.valueOf(inspector.field("status").asString());
            changeRequest = changeRequest.withStatus(status);
        }

        try (var lock = controller.curator().lockChangeRequests()) {
            controller.curator().writeChangeRequest(changeRequest);
        }

        var slime = new Slime();
        var cursor = slime.setObject();
        ChangeRequestSerializer.writeChangeRequest(cursor, changeRequest);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse deleteVCMR(String vcmrId) {
        var changeRequest = controller.curator().readChangeRequest(vcmrId);

        if (changeRequest.isEmpty()) {
            return ErrorResponse.notFoundError("No VCMR with id: " + vcmrId);
        }

        try (var lock = controller.curator().lockChangeRequests()) {
            controller.curator().deleteChangeRequest(changeRequest.get());
        }

        var slime = new Slime();
        var cursor = slime.setObject();
        ChangeRequestSerializer.writeChangeRequest(cursor, changeRequest.get());
        return new SlimeJsonResponse(slime);
    }

    private Optional<ZoneId> affectedZone(List<String> hosts) {
        NodeFilter affectedHosts = NodeFilter.all().hostnames(hosts.stream()
                                                                   .map(HostName::of)
                                                                   .collect(Collectors.toSet()));
        for (var zone : getProdZones()) {
            var affectedHostsInZone = controller.serviceRegistry().configServer().nodeRepository().list(zone, affectedHosts);
            if (!affectedHostsInZone.isEmpty())
                return Optional.of(zone);
        }

        return Optional.empty();
    }

    private List<String> hostsOnSwitch(List<String> switches) {
        return getProdZones().stream()
                .flatMap(zone -> controller.serviceRegistry().configServer().nodeRepository().list(zone, NodeFilter.all()).stream())
                .filter(node -> node.switchHostname().map(switches::contains).orElse(false))
                .map(node -> node.hostname().value())
                .toList();
    }

    private List<ZoneId> getProdZones() {
        return controller.zoneRegistry()
                .zones()
                .reachable()
                .in(Environment.prod)
                .ids();
    }

}
