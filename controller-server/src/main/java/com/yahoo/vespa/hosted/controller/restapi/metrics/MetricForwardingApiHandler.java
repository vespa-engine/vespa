// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.metrics;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.Path;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.RotationStatus;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.StringResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This implements the metricforwarding/v1 API which allows feeding
 * MetricsService data.
 * @author olaa
 */
public class MetricForwardingApiHandler extends LoggingRequestHandler {

    private final Controller controller;

    public MetricForwardingApiHandler(Context ctx, Controller controller) {
        super(ctx);
        this.controller = controller;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        switch (request.getMethod()) {
            case POST:
                return post(request);
            default:
                return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
        }
    }

    private HttpResponse post(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/metricforwarding/v1/clusterutilization")) return updateClusterUtilization(request);
        if (path.matches("/metricforwarding/v1/deploymentmetrics")) return updateDeploymentMetrics(request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse updateClusterUtilization(HttpRequest request) {
        try {
            Slime slime = SlimeUtils.jsonToSlime(IOUtils.readBytes(request.getData(), 1000 * 1000));
            Inspector inspector = slime.get();
            inspector.traverse((ArrayTraverser) (index, entry) -> {
                ApplicationId applicationId = ApplicationId.fromSerializedForm(entry.field("applicationId").asString());
                entry.field("deployments").traverse((ArrayTraverser) (i, deployment) -> {
                    ZoneId zoneId = ZoneId.from(deployment.field("zoneId").asString());
                    Map<ClusterSpec.Id, ClusterUtilization> clusterUtilization = getClusterUtilizationsFromInspector(deployment.field("clusterUtil"));
                    controller.applications().lockIfPresent(applicationId, lockedApplication ->
                            controller.applications().store(lockedApplication.withClusterUtilization(zoneId, clusterUtilization)));
                });
            });
        } catch (IOException e) {
            ErrorResponse.badRequest("Unable to parse request for metrics - " + e.getMessage());
        }
        return new StringResponse("Added cluster utilization metrics");
    }

    private HttpResponse updateDeploymentMetrics(HttpRequest request) {
        try {
            Slime slime = SlimeUtils.jsonToSlime(IOUtils.readBytes(request.getData(), 1000 * 1000));
            Inspector inspector = slime.get();
            inspector.traverse((ArrayTraverser) (index, applicationEntry) -> {
                ApplicationId applicationId = ApplicationId.fromSerializedForm(applicationEntry.field("applicationId").asString());
                MetricsService.ApplicationMetrics applicationMetrics = getApplicationMetricsFromInspector(applicationEntry);
                ApplicationController applications = controller.applications();
                applications.lockIfPresent(applicationId, lockedApplication ->
                        applications.store(lockedApplication.with(applicationMetrics)));

                Map<HostName, RotationStatus> rotationStatusMap = getRotationStatusFromInspector(applicationEntry);
                applications.lockIfPresent(applicationId, lockedApplication ->
                        applications.store(lockedApplication.withRotationStatus(rotationStatusMap)));

                for (Map.Entry<ZoneId, DeploymentMetrics> entry : getDeploymentMetricsFromInspector(applicationEntry).entrySet()) {
                    applications.lockIfPresent(applicationId, lockedApplication ->
                            applications.store(lockedApplication.with(entry.getKey(), entry.getValue())
                                    .recordActivityAt(controller.clock().instant(), entry.getKey())));
                }
            });
        } catch (IOException e) {
            ErrorResponse.badRequest("Unable to parse request for metrics - " + e.getMessage());
        }
        return new StringResponse("Added deployment metrics");
    }

    private Map<ClusterSpec.Id, ClusterUtilization> getClusterUtilizationsFromInspector(Inspector inspector) {
        Map<ClusterSpec.Id, ClusterUtilization> clusterUtilizationMap = new HashMap<>();
        inspector.traverse((ArrayTraverser) (index, entry) -> {
            ClusterSpec.Id id = ClusterSpec.Id.from(entry.field("clusterSpecId").asString());
            double memory = entry.field("memory").asDouble();
            double cpu = entry.field("cpu").asDouble();
            double disk = entry.field("disk").asDouble();
            double diskBusy = entry.field("diskBusy").asDouble();
            clusterUtilizationMap.put(id, new ClusterUtilization(memory, cpu, disk, diskBusy));
        });
        return clusterUtilizationMap;
    }

    private Map<ZoneId, DeploymentMetrics> getDeploymentMetricsFromInspector(Inspector inspector){
        Map<ZoneId, DeploymentMetrics> deploymentMetricsMap = new HashMap<>();
        inspector = inspector.field("deploymentMetrics");
        inspector.traverse((ArrayTraverser) (index, entry) -> {
            ZoneId zoneId = ZoneId.from(entry.field("zoneId").asString());
            double queriesPerSecond = entry.field("queriesPerSecond").asDouble();
            double writesPerSecond = entry.field("writesPerSecond").asDouble();
            double documentCount = entry.field("documentCount").asDouble();
            double queryLatencyMillis = entry.field("queryLatencyMillis").asDouble();
            double writeLatencyMillis = entry.field("writeLatencyMillis").asDouble();
            DeploymentMetrics metrics = new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis, writeLatencyMillis);
            deploymentMetricsMap.put(zoneId, metrics);
        });
        return deploymentMetricsMap;
    }

    private MetricsService.ApplicationMetrics getApplicationMetricsFromInspector(Inspector inspector){
        inspector = inspector.field("applicationMetrics");
        double queryServiceQuality = inspector.field("queryServiceQuality").asDouble();
        double writeServiceQuality = inspector.field("writeServiceQuality").asDouble();
        return new MetricsService.ApplicationMetrics(queryServiceQuality, writeServiceQuality);
    }

    private Map<HostName, RotationStatus> getRotationStatusFromInspector(Inspector inspector) {
        Map<HostName, RotationStatus> rotationStatusMap = new HashMap<>();
        inspector = inspector.field("rotationStatus");
        inspector.traverse((ArrayTraverser) (index, entry) -> {
            HostName hostName = HostName.from(entry.field("hostname").asString());
            RotationStatus rotationStatus = RotationStatus.valueOf(entry.field("rotationStatus").asString());
            rotationStatusMap.put(hostName, rotationStatus);
        });
        return rotationStatusMap;
    }
}
