// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author smorgrav
 * @author mpolden
 */
public class DeploymentMetricsMaintainerTest {

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    void updates_metrics() {
        Version version1 = Version.fromString("7.1");
        tester.controllerTester().upgradeSystem(version1);
        var application = tester.newDeploymentContext();
        application.runJob(DeploymentContext.devUsEast1, new ApplicationPackage(new byte[0]), version1);

        DeploymentMetricsMaintainer maintainer = maintainer(tester.controller());
        Supplier<Application> app = application::application;
        Supplier<Deployment> deployment = () -> application.deployment(ZoneId.from("dev", "us-east-1"));

        // No metrics gathered yet
        assertEquals(0, app.get().metrics().queryServiceQuality(), 0);
        assertEquals(0, deployment.get().metrics().documentCount(), 0);
        assertFalse(deployment.get().metrics().instant().isPresent(), "No timestamp set");
        assertFalse(deployment.get().activity().lastQueried().isPresent(), "Never received any queries");
        assertFalse(deployment.get().activity().lastWritten().isPresent(), "Never received any writes");

        // Metrics are gathered and saved to application
        Version version2 = Version.fromString("7.5.5");
        tester.controllerTester().upgradeSystem(version2);
        application.runJob(DeploymentContext.devUsEast1, new ApplicationPackage(new byte[0]), version2);
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

    @Test
    void cluster_metric_aggregation_test() {
        List<ClusterMetrics> clusterMetrics = List.of(
                new ClusterMetrics("niceCluster", "container", Map.of("queriesPerSecond", 23.0, "queryLatency", 1337.0)),
                new ClusterMetrics("alsoNiceCluster", "container", Map.of("queriesPerSecond", 11.0, "queryLatency", 12.0)));

        DeploymentMetrics deploymentMetrics = DeploymentMetricsMaintainer.updateDeploymentMetrics(DeploymentMetrics.none, clusterMetrics);

        assertEquals(23.0 + 11.0, deploymentMetrics.queriesPerSecond(), 0.001);
        assertEquals(908.323, deploymentMetrics.queryLatencyMillis(), 0.001);
        assertEquals(0, deploymentMetrics.documentCount(), 0.001);
        assertEquals(0.0, deploymentMetrics.writeLatencyMillis(), 0.001);
        assertEquals(0.0, deploymentMetrics.writesPerSecond(), 0.001);
    }

    private void setMetrics(ApplicationId application, Map<String, Double> metrics) {
        var clusterMetrics = new ClusterMetrics("default", "container", metrics);
        tester.controllerTester().serviceRegistry().configServerMock().setMetrics(new DeploymentId(application, ZoneId.from("dev", "us-east-1")), clusterMetrics);
    }

    private static DeploymentMetricsMaintainer maintainer(Controller controller) {
        return new DeploymentMetricsMaintainer(controller, Duration.ofDays(1));
    }

}
