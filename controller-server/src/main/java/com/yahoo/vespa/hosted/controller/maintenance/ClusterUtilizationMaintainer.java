// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.authority.config.ApiAuthorityConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Fetch utilization metrics and update applications with this data.
 *
 * @author smorgrav
 */
public class ClusterUtilizationMaintainer extends Maintainer {

    private final Controller controller;
    private final List<String> baseUris;

    public ClusterUtilizationMaintainer(Controller controller, Duration duration, JobControl jobControl, ApiAuthorityConfig apiAuthorityConfig) {
        super(controller, duration, jobControl);
        this.controller = controller;
        this.baseUris = apiAuthorityConfig.authorities();
    }

    private Map<ClusterSpec.Id, ClusterUtilization> getUpdatedClusterUtilizations(ApplicationId app, ZoneId zone) {
        Map<String, MetricsService.SystemMetrics> systemMetrics = controller.metricsService().getSystemMetrics(app, zone);

        Map<ClusterSpec.Id, ClusterUtilization> utilizationMap = new HashMap<>();
        for (Map.Entry<String, MetricsService.SystemMetrics> metrics : systemMetrics.entrySet()) {
            MetricsService.SystemMetrics systemMetric = metrics.getValue();
            ClusterUtilization utilization = new ClusterUtilization(systemMetric.memUtil() / 100, systemMetric.cpuUtil() / 100, systemMetric.diskUtil() / 100, 0);
            utilizationMap.put(new ClusterSpec.Id(metrics.getKey()), utilization);
        }

        return utilizationMap;
    }

    @Override
    protected void maintain() {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            String uri = baseUris.get(0) + "metricforwarding/v1/clusterutilization"; // For now, we only feed to one controller
            Slime slime = getMetricSlime();
            ByteArrayEntity entity = new ByteArrayEntity(SlimeUtils.toJsonBytes(slime));
            HttpPost httpPost = new HttpPost(uri);
            httpPost.setEntity(entity);
            httpClient.execute(httpPost);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to update cluster utilization metrics", e);
        }

    }

    private Slime getMetricSlime() {
        Slime slime = new Slime();
        Cursor cursor = slime.setArray();
        for (Application application : controller().applications().asList()) {
            Cursor applicationCursor = cursor.addObject();
            applicationCursor.setString("applicationId", application.id().serializedForm());
            Cursor deploymentArray = applicationCursor.setArray("deployments");
            for (Deployment deployment : application.deployments().values()) {
                Cursor deploymentEntry = deploymentArray.addObject();
                deploymentEntry.setString("zoneId", deployment.zone().value());
                Cursor clusterArray = deploymentEntry.setArray("clusterUtil");
                Map<ClusterSpec.Id, ClusterUtilization> clusterUtilization = getUpdatedClusterUtilizations(application.id(), deployment.zone());
                fillClusterUtilization(clusterArray, clusterUtilization);
            }
        }
        return slime;
    }

    private void fillClusterUtilization(Cursor cursor, Map<ClusterSpec.Id, ClusterUtilization> clusterUtilization) {
        for (Map.Entry<ClusterSpec.Id, ClusterUtilization> entry : clusterUtilization.entrySet()) {
            Cursor clusterUtilCursor = cursor.addObject();
            clusterUtilCursor.setString("clusterSpecId", entry.getKey().value());
            clusterUtilCursor.setDouble("cpu", entry.getValue().getCpu());
            clusterUtilCursor.setDouble("memory", entry.getValue().getMemory());
            clusterUtilCursor.setDouble("disk", entry.getValue().getDisk());
            clusterUtilCursor.setDouble("diskBusy", entry.getValue().getDiskBusy());
        }
    }
}
