// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class DeploymentExpirerTest {

    @Test
    public void testDeploymentExpiry() throws IOException, InterruptedException {
        ControllerTester tester = new ControllerTester();
        tester.zoneRegistry().setDeploymentTimeToLive(new Zone(Environment.dev, RegionName.from("us-east-1")), Duration.ofDays(14));
        DeploymentExpirer expirer = new DeploymentExpirer(tester.controller(), Duration.ofDays(10),
                                                          tester.clock(), new JobControl(new MockCuratorDb()));
        ApplicationId devApp = tester.createAndDeploy("tenant1", "domain1", "app1", Environment.dev, 123).id();
        ApplicationId prodApp = tester.createAndDeploy("tenant2", "domain2", "app2", Environment.prod, 456).id();

        assertEquals(1, tester.controller().applications().get(devApp).get().deployments().size());
        assertEquals(1, tester.controller().applications().get(prodApp).get().deployments().size());

        // Not expired at first
        expirer.maintain();
        assertEquals(1, tester.controller().applications().get(devApp).get().deployments().size());
        assertEquals(1, tester.controller().applications().get(prodApp).get().deployments().size());

        // The dev application is removed
        tester.clock().advance(Duration.ofDays(15));
        expirer.maintain();
        assertEquals(0, tester.controller().applications().get(devApp).get().deployments().size());
        assertEquals(1, tester.controller().applications().get(prodApp).get().deployments().size());
    }

}
