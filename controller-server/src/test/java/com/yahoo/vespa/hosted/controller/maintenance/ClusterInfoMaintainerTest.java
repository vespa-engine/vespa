// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.NodeRepositoryClientMock;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

/**
 * @author smorgrav
 */
public class ClusterInfoMaintainerTest {

    @Test
    public void maintain() {
        ControllerTester tester = new ControllerTester();
        ApplicationId app = tester.createAndDeploy("tenant1", "domain1", "app1",
                                                   Environment.dev, 123).id();

        // Precondition: no cluster info attached to the deployments
        Deployment deployment = tester.controller().applications().get(app).get().deployments().values().stream()
                                      .findFirst()
                                      .get();
        assertEquals(0, deployment.clusterInfo().size());

        ClusterInfoMaintainer maintainer = new ClusterInfoMaintainer(tester.controller(), Duration.ofHours(1),
                                                                     new JobControl(new MockCuratorDb()),
                                                                     new NodeRepositoryClientMock());
        maintainer.maintain();

        deployment = tester.controller().applications().get(app).get().deployments().values().stream()
                           .findFirst()
                           .get();
        assertEquals(2, deployment.clusterInfo().size());
        assertEquals(10, deployment.clusterInfo().get(ClusterSpec.Id.from("clusterA")).getFlavorCost());
    }

}
