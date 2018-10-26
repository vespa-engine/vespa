// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;

/**
 * @author smorgrav
 */
public class ClusterUtilizationMaintainerTest {

    @Test
    public void maintain() {
        ControllerTester tester = new ControllerTester();
        ApplicationId app = tester.createAndDeploy("tenant1", "domain1", "app1", Environment.dev, 123).id();

        // Precondition: no cluster info attached to the deployments
        Deployment deployment = tester.controller().applications().get(app).get().deployments().values().stream().findAny().get();
        Assert.assertEquals(0, deployment.clusterUtils().size());

        ClusterUtilizationMaintainer mainainer = new ClusterUtilizationMaintainer(tester.controller(), Duration.ofHours(1), new JobControl(new MockCuratorDb()));
        mainainer.maintain();

        deployment = tester.controller().applications().get(app).get().deployments().values().stream().findAny().get();
        Assert.assertEquals(1, deployment.clusterUtils().size());
        Assert.assertEquals(0.5554, deployment.clusterUtils().get(ClusterSpec.Id.from("default")).getCpu(), Double.MIN_VALUE);
    }

}
