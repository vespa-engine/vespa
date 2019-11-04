// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author smorgrav
 * @author mpolden
 */
public class DeploymentMetricsMaintainerTest {

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    public void updates_metrics() {
        var application = tester.newDeploymentContext();
        application.runJob(JobType.devUsEast1, new ApplicationPackage(new byte[0]), Version.fromString("7.1"));

        DeploymentMetricsMaintainer maintainer = maintainer(tester.controller());
        Supplier<Application> app = application::application;
        Supplier<Instance> instance = application::instance;
        Supplier<Deployment> deployment = () -> application.deployment(ZoneId.from("dev", "us-east-1"));

        // No metrics gathered yet
        assertEquals(0, app.get().metrics().queryServiceQuality(), 0);
        assertEquals(0, deployment.get().metrics().documentCount(), 0);
        assertFalse("No timestamp set", deployment.get().metrics().instant().isPresent());
        assertFalse("Never received any queries", deployment.get().activity().lastQueried().isPresent());
        assertFalse("Never received any writes", deployment.get().activity().lastWritten().isPresent());

        // Only get application metrics for old version
        application.runJob(JobType.devUsEast1, new ApplicationPackage(new byte[0]), Version.fromString("6.3.3"));
        maintainer.maintain();
        assertEquals(0, app.get().metrics().queryServiceQuality(), 0);
        assertEquals(0, deployment.get().metrics().documentCount(), 0);
        assertFalse("No timestamp set", deployment.get().metrics().instant().isPresent());
        assertFalse("Never received any queries", deployment.get().activity().lastQueried().isPresent());
        assertFalse("Never received any writes", deployment.get().activity().lastWritten().isPresent());

        // Metrics are gathered and saved to application
        application.runJob(JobType.devUsEast1, new ApplicationPackage(new byte[0]), Version.fromString("7.5.5"));
        var metrics0 = Map.of(ClusterMetrics.QUERIES_PER_SECOND, 1D,
                              ClusterMetrics.FEED_PER_SECOND, 2D,
                              ClusterMetrics.DOCUMENT_COUNT, 3D,
                              ClusterMetrics.QUERY_LATENCY, 4D,
                              ClusterMetrics.FEED_LATENCY, 5D);
        setMetrics(application.application().id().defaultInstance(), metrics0);
        maintainer.maintain();
        Instant t1 = tester.clock().instant().truncatedTo(MILLIS);
        assertEquals(0.0, app.get().metrics().queryServiceQuality(), Double.MIN_VALUE);
        assertEquals(0.0, app.get().metrics().writeServiceQuality(), Double.MIN_VALUE);
        assertEquals(1, deployment.get().metrics().queriesPerSecond(), Double.MIN_VALUE);
        assertEquals(2, deployment.get().metrics().writesPerSecond(), Double.MIN_VALUE);
        assertEquals(3, deployment.get().metrics().documentCount(), Double.MIN_VALUE);
        assertEquals(4, deployment.get().metrics().queryLatencyMillis(), Double.MIN_VALUE);
        assertEquals(5, deployment.get().metrics().writeLatencyMillis(), Double.MIN_VALUE);
        assertEquals(t1, deployment.get().metrics().instant().get());
        assertEquals(t1, deployment.get().activity().lastQueried().get());
        assertEquals(t1, deployment.get().activity().lastWritten().get());

        // Time passes. Activity is updated as app is still receiving traffic
        tester.clock().advance(Duration.ofHours(1));
        Instant t2 = tester.clock().instant().truncatedTo(MILLIS);
        maintainer.maintain();
        assertEquals(t2, deployment.get().metrics().instant().get());
        assertEquals(t2, deployment.get().activity().lastQueried().get());
        assertEquals(t2, deployment.get().activity().lastWritten().get());
        assertEquals(1, deployment.get().activity().lastQueriesPerSecond().getAsDouble(), Double.MIN_VALUE);
        assertEquals(2, deployment.get().activity().lastWritesPerSecond().getAsDouble(), Double.MIN_VALUE);

        // Query traffic disappears. Query activity stops updating
        tester.clock().advance(Duration.ofHours(1));
        Instant t3 = tester.clock().instant().truncatedTo(MILLIS);
        var metrics1 = new HashMap<>(metrics0);
        metrics1.put(ClusterMetrics.QUERIES_PER_SECOND, 0D);
        metrics1.put(ClusterMetrics.FEED_PER_SECOND, 5D);
        setMetrics(application.application().id().defaultInstance(), metrics1);
        maintainer.maintain();
        assertEquals(t2, deployment.get().activity().lastQueried().get());
        assertEquals(t3, deployment.get().activity().lastWritten().get());
        assertEquals(1, deployment.get().activity().lastQueriesPerSecond().getAsDouble(), Double.MIN_VALUE);
        assertEquals(5, deployment.get().activity().lastWritesPerSecond().getAsDouble(), Double.MIN_VALUE);

        // Feed traffic disappears. Feed activity stops updating
        tester.clock().advance(Duration.ofHours(1));
        var metrics2 = new HashMap<>(metrics1);
        metrics2.put(ClusterMetrics.FEED_PER_SECOND, 0D);
        setMetrics(application.application().id().defaultInstance(), metrics2);
        maintainer.maintain();
        assertEquals(t2, deployment.get().activity().lastQueried().get());
        assertEquals(t3, deployment.get().activity().lastWritten().get());
        assertEquals(1, deployment.get().activity().lastQueriesPerSecond().getAsDouble(), Double.MIN_VALUE);
        assertEquals(5, deployment.get().activity().lastWritesPerSecond().getAsDouble(), Double.MIN_VALUE);
    }

    private void setMetrics(ApplicationId application, Map<String, Double> metrics) {
        var clusterMetrics = new ClusterMetrics("default", ClusterMetrics.ClusterType.container);
        for (var kv : metrics.entrySet()) {
            clusterMetrics = clusterMetrics.addMetric(kv.getKey(), kv.getValue());
        }
        tester.controllerTester().serviceRegistry().configServerMock().setMetrics(new DeploymentId(application, ZoneId.from("dev", "us-east-1")), clusterMetrics);
    }

    private static DeploymentMetricsMaintainer maintainer(Controller controller) {
        return new DeploymentMetricsMaintainer(controller, Duration.ofDays(1), new JobControl(controller.curator()));
    }

}
