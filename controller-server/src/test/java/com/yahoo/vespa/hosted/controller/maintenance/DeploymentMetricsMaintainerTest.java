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

import static org.junit.Assert.assertEquals;

/**
 * @author smorgrav
 */
public class DeploymentMetricsMaintainerTest {

    @Test
    public void maintain() {
        ControllerTester tester = new ControllerTester();
        ApplicationId app = tester.createAndDeploy("tenant1", "domain1", "app1", Environment.dev, 123).id();

        // Pre condition: no metric info on neither application nor deployment
        assertEquals(0, tester.controller().applications().require(app).metrics().queryServiceQuality(), 0);
        Deployment deployment = tester.controller().applications().get(app).get().deployments().values().stream().findAny().get();
        assertEquals(0, deployment.metrics().documentCount(), 0);

        DeploymentMetricsMaintainer maintainer = new DeploymentMetricsMaintainer(tester.controller(), Duration.ofMinutes(10), new JobControl(new MockCuratorDb()));
        maintainer.maintain();

        // Post condition:
        Application application = tester.controller().applications().require(app);
        assertEquals(0.5, application.metrics().queryServiceQuality(), Double.MIN_VALUE);
        assertEquals(0.7, application.metrics().writeServiceQuality(), Double.MIN_VALUE);
        deployment = application.deployments().values().stream().findAny().get();
        assertEquals(1, deployment.metrics().queriesPerSecond(), Double.MIN_VALUE);
        assertEquals(2, deployment.metrics().writesPerSecond(), Double.MIN_VALUE);
        assertEquals(3, deployment.metrics().documentCount(), Double.MIN_VALUE);
        assertEquals(4, deployment.metrics().queryLatencyMillis(), Double.MIN_VALUE);
        assertEquals(5, deployment.metrics().writeLatencyMillis(), Double.MIN_VALUE);
    }

}
