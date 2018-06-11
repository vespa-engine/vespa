// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author smorgrav
 * @author mpolden
 */
public class DeploymentMetricsMaintainerTest {

    @Test
    public void maintain() {
        ControllerTester tester = new ControllerTester();
        ApplicationId appId = tester.createAndDeploy("tenant1", "domain1", "app1",
                                                  Environment.dev, 123).id();
        DeploymentMetricsMaintainer maintainer = new DeploymentMetricsMaintainer(tester.controller(),
                                                                                 Duration.ofDays(1),
                                                                                 new JobControl(new MockCuratorDb()));
        Supplier<Application> app = tester.application(appId);
        Supplier<Deployment> deployment = () -> app.get().deployments().values().stream().findFirst().get();

        // No metrics gathered yet
        assertEquals(0, app.get().metrics().queryServiceQuality(), 0);
        assertEquals(0, deployment.get().metrics().documentCount(), 0);
        assertFalse("Never received any queries", deployment.get().activity().lastQueried().isPresent());
        assertFalse("Never received any writes", deployment.get().activity().lastWritten().isPresent());

        // Metrics are gathered and saved to application
        maintainer.maintain();
        assertEquals(0.5, app.get().metrics().queryServiceQuality(), Double.MIN_VALUE);
        assertEquals(0.7, app.get().metrics().writeServiceQuality(), Double.MIN_VALUE);
        assertEquals(1, deployment.get().metrics().queriesPerSecond(), Double.MIN_VALUE);
        assertEquals(2, deployment.get().metrics().writesPerSecond(), Double.MIN_VALUE);
        assertEquals(3, deployment.get().metrics().documentCount(), Double.MIN_VALUE);
        assertEquals(4, deployment.get().metrics().queryLatencyMillis(), Double.MIN_VALUE);
        assertEquals(5, deployment.get().metrics().writeLatencyMillis(), Double.MIN_VALUE);
        Instant t1 = tester.clock().instant();
        assertEquals(t1, deployment.get().activity().lastQueried().get());
        assertEquals(t1, deployment.get().activity().lastWritten().get());

        // Time passes. Activity is updated as app is still receiving traffic
        tester.clock().advance(Duration.ofHours(1));
        Instant t2 = tester.clock().instant();
        maintainer.maintain();
        assertEquals(t2, deployment.get().activity().lastQueried().get());
        assertEquals(t2, deployment.get().activity().lastWritten().get());
        assertEquals(1, deployment.get().activity().lastQueriesPerSecond().getAsDouble(), Double.MIN_VALUE);
        assertEquals(2, deployment.get().activity().lastWritesPerSecond().getAsDouble(), Double.MIN_VALUE);

        // Query traffic disappears. Query activity stops updating
        tester.clock().advance(Duration.ofHours(1));
        Instant t3 = tester.clock().instant();
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

}
