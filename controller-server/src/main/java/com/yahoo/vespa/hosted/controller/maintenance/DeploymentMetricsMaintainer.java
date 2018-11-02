// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.RotationStatus;
import com.yahoo.vespa.hosted.controller.authority.config.ApiAuthorityConfig;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retrieve deployment metrics such as QPS and document count from the metric service and
 * update applications with this info.
 *
 * @author smorgrav
 * @author mpolden
 */
public class DeploymentMetricsMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(DeploymentMetricsMaintainer.class.getName());

    private static final int applicationsToUpdateInParallel = 10;

    private final ApplicationController applications;
    private final List<String> baseUris;

    public DeploymentMetricsMaintainer(Controller controller, Duration duration, JobControl jobControl, ApiAuthorityConfig apiAuthorityConfig) {
        super(controller, duration, jobControl);
        this.applications = controller.applications();
        baseUris = apiAuthorityConfig.authorities();
    }

    @Override
    protected void maintain() {
        AtomicInteger failures = new AtomicInteger(0);
        AtomicReference<Exception> lastException = new AtomicReference<>(null);
        List<Application> applicationList = applications.asList();

        // Run parallel stream inside a custom ForkJoinPool so that we can control the number of threads used
        ForkJoinPool pool = new ForkJoinPool(applicationsToUpdateInParallel);
        Slime slime = new Slime();
        Cursor cursor = slime.setArray();
        pool.submit(() -> {
            applicationList.parallelStream().forEach(application -> {
                try {
                    MetricsService.ApplicationMetrics applicationMetrics = controller().metricsService()
                            .getApplicationMetrics(application.id());
                    Map<HostName, RotationStatus> rotationStatus = getRotationStatus(application);
                    Map<ZoneId, MetricsService.DeploymentMetrics> deploymentMetrics = getDeploymentMetrics(application);


                    Cursor applicationCursor = cursor.addObject();
                    applicationCursor.setString("applicationId", application.id().serializedForm());
                    Cursor applicationMetricsCursor = applicationCursor.setObject("applicationMetrics");
                    fillApplicationMetrics(applicationMetricsCursor, applicationMetrics);
                    Cursor rotationStatusCursor = applicationCursor.setArray("rotationStatus");
                    fillRotationStatus(rotationStatusCursor, rotationStatus);
                    Cursor deploymentArray = applicationCursor.setArray("deploymentMetrics");
                    for (Map.Entry<ZoneId, MetricsService.DeploymentMetrics> entry : deploymentMetrics.entrySet()) {
                        Cursor deploymentEntry = deploymentArray.addObject();
                        fillDeploymentMetrics(deploymentEntry, entry.getKey().value(), entry.getValue());
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                    lastException.set(e);
                }
            });
        });

        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.MINUTES);
            feedMetrics(slime);
            if (lastException.get() != null) {
                log.log(Level.WARNING, String.format("Failed to query metrics service for %d/%d applications. Retrying in %s. Stacktrace of last error: ",
                                                     failures.get(),
                                                     applicationList.size(),
                                                     maintenanceInterval()),lastException.get());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.log(Level.WARNING, "Unable to feed metrics to API", e);
        }
    }

    /** Get global rotation status for application */
    private Map<HostName, RotationStatus> getRotationStatus(Application application) {
        return applications.rotationRepository().getRotation(application)
                           .map(rotation -> controller().metricsService().getRotationStatus(rotation.name()))
                           .map(rotationStatus -> {
                               Map<HostName, RotationStatus> result = new TreeMap<>();
                               rotationStatus.forEach((hostname, status) -> result.put(hostname, from(status)));
                               return result;
                           })
                           .orElseGet(Collections::emptyMap);
    }

    private Map<ZoneId, MetricsService.DeploymentMetrics> getDeploymentMetrics(Application application) {
        Map<ZoneId, MetricsService.DeploymentMetrics> deploymentMetrics = new HashMap<>();
        for (Deployment deployment : application.deployments().values()) {
            ZoneId zone = deployment.zone();
            MetricsService.DeploymentMetrics zoneDeploymentMetrics = controller().metricsService()
                    .getDeploymentMetrics(application.id(), zone);
            deploymentMetrics.put(zone, zoneDeploymentMetrics);
        }
        return deploymentMetrics;
    }

    private void fillApplicationMetrics(Cursor applicationCursor, MetricsService.ApplicationMetrics applicationMetrics) {
        applicationCursor.setDouble("queryServiceQuality", applicationMetrics.queryServiceQuality());
        applicationCursor.setDouble("writeServiceQuality", applicationMetrics.writeServiceQuality());
    }

    private void fillRotationStatus(Cursor rotationStatusCursor, Map<HostName, RotationStatus> rotationStatus) {
        for (Map.Entry<HostName, RotationStatus> entry : rotationStatus.entrySet()) {
            Cursor rotationStatusEntry = rotationStatusCursor.addObject();
            rotationStatusEntry.setString("hostname", entry.getKey().value());
            rotationStatusEntry.setString("rotationStatus", entry.getValue().toString());
        }
    }

    private void fillDeploymentMetrics(Cursor deploymentCursor, String zone, MetricsService.DeploymentMetrics deploymentMetrics) {
        deploymentCursor.setString("zoneId", zone);
        deploymentCursor.setDouble("queriesPerSecond", deploymentMetrics.queriesPerSecond());
        deploymentCursor.setDouble("writesPerSecond", deploymentMetrics.writesPerSecond());
        deploymentCursor.setDouble("documentCount", deploymentMetrics.documentCount());
        deploymentCursor.setDouble("queryLatencyMillis", deploymentMetrics.queryLatencyMillis());
        deploymentCursor.setDouble("writeLatencyMillis", deploymentMetrics.writeLatencyMillis());
    }

    private void feedMetrics(Slime slime) throws IOException {
        String uri = baseUris.get(0) + "/metricforwarding/v1/deploymentmetrics"; // For now, we only feed to one controller
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(uri);
        httpPost.setEntity(new ByteArrayEntity(SlimeUtils.toJsonBytes(slime)));
        HttpResponse response = httpClient.execute(httpPost);
        if (response.getStatusLine().getStatusCode() != 200) {
            log.log(Level.WARNING, "Could not feed metrics. Reason: " + response.getStatusLine().getReasonPhrase());
        }
    }

    private static RotationStatus from(com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus status) {
        switch (status) {
            case IN: return RotationStatus.in;
            case OUT: return RotationStatus.out;
            case UNKNOWN: return RotationStatus.unknown;
            default: throw new IllegalArgumentException("Unknown API value for rotation status: " + status);
        }
    }

}
