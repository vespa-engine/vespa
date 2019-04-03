// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.RotationStatus;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.MetricsServiceMock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author smorgrav
 * @author mpolden
 */
public class DeploymentMetricsMaintainerTest {

    @Test
    public void updates_metrics() {
        ControllerTester tester = new ControllerTester();
        ApplicationId appId = tester.createAndDeploy("tenant1", "domain1", "app1",
                                                  Environment.dev, 123).id();
        DeploymentMetricsMaintainer maintainer = maintainer(tester.controller());
        Supplier<Application> app = tester.application(appId);
        Supplier<Deployment> deployment = () -> app.get().deployments().values().stream().findFirst().get();

        // No metrics gathered yet
        assertEquals(0, app.get().metrics().queryServiceQuality(), 0);
        assertEquals(0, deployment.get().metrics().documentCount(), 0);
        assertFalse("No timestamp set", deployment.get().metrics().instant().isPresent());
        assertFalse("Never received any queries", deployment.get().activity().lastQueried().isPresent());
        assertFalse("Never received any writes", deployment.get().activity().lastWritten().isPresent());

        // Metrics are gathered and saved to application
        maintainer.maintain();
        Instant t1 = tester.clock().instant().truncatedTo(MILLIS);
        assertEquals(0.5, app.get().metrics().queryServiceQuality(), Double.MIN_VALUE);
        assertEquals(0.7, app.get().metrics().writeServiceQuality(), Double.MIN_VALUE);
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
        tester.metricsService().setMetric("queriesPerSecond", 0D);
        tester.metricsService().setMetric("writesPerSecond", 5D);
        maintainer.maintain();
        assertEquals(t2, deployment.get().activity().lastQueried().get());
        assertEquals(t3, deployment.get().activity().lastWritten().get());
        assertEquals(1, deployment.get().activity().lastQueriesPerSecond().getAsDouble(), Double.MIN_VALUE);
        assertEquals(5, deployment.get().activity().lastWritesPerSecond().getAsDouble(), Double.MIN_VALUE);

        // Feed traffic disappears. Feed activity stops updating
        tester.clock().advance(Duration.ofHours(1));
        tester.metricsService().setMetric("writesPerSecond", 0D);
        maintainer.maintain();
        assertEquals(t2, deployment.get().activity().lastQueried().get());
        assertEquals(t3, deployment.get().activity().lastWritten().get());
        assertEquals(1, deployment.get().activity().lastQueriesPerSecond().getAsDouble(), Double.MIN_VALUE);
        assertEquals(5, deployment.get().activity().lastWritesPerSecond().getAsDouble(), Double.MIN_VALUE);
    }

    @Test
    public void updates_rotation_status() {
        DeploymentTester tester = new DeploymentTester();
        MetricsServiceMock metricsService = tester.controllerTester().metricsService();
        DeploymentMetricsMaintainer maintainer = maintainer(tester.controller());
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);
        ZoneId zone1 = ZoneId.from("prod", "us-west-1");
        ZoneId zone2 = ZoneId.from("prod", "us-east-3");

        // Deploy application with global rotation
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .globalServiceId("foo")
                .region(zone1.region().value())
                .region(zone2.region().value())
                .build();
        tester.deployCompletely(application, applicationPackage);

        Supplier<Application> app = () -> tester.application(application.id());
        Supplier<Deployment> deployment1 = () -> app.get().deployments().get(zone1);
        Supplier<Deployment> deployment2 = () -> app.get().deployments().get(zone2);
        String assignedRotation = "rotation-fqdn-01";
        tester.controllerTester().metricsService().addRotation(assignedRotation);

        // No status gathered yet
        assertEquals(RotationStatus.unknown, app.get().rotationStatus(deployment1.get()));
        assertEquals(RotationStatus.unknown, app.get().rotationStatus(deployment2.get()));

        // One rotation out, one in
        metricsService.setZoneIn(assignedRotation, "proxy.prod.us-west-1.vip.test");
        metricsService.setZoneOut(assignedRotation,"proxy.prod.us-east-3.vip.test");
        maintainer.maintain();
        assertEquals(RotationStatus.in, app.get().rotationStatus(deployment1.get()));
        assertEquals(RotationStatus.out, app.get().rotationStatus(deployment2.get()));

        // All rotations in
        metricsService.setZoneIn(assignedRotation,"proxy.prod.us-east-3.vip.test");
        maintainer.maintain();
        assertEquals(RotationStatus.in, app.get().rotationStatus(deployment1.get()));
        assertEquals(RotationStatus.in, app.get().rotationStatus(deployment2.get()));
    }

    private static DeploymentMetricsMaintainer maintainer(Controller controller) {
        return new DeploymentMetricsMaintainer(controller, Duration.ofDays(1), new JobControl(controller.curator()));
    }

}
