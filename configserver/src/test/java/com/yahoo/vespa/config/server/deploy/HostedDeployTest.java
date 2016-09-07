// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.provision.Version;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class HostedDeployTest {

    @Test
    public void testRedeploy() throws InterruptedException, IOException {
        DeployTester tester = new DeployTester("src/test/apps/hosted/");
        tester.deployApp("myApp");

        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();                
        assertTrue(deployment.isPresent());
        deployment.get().prepare();
        deployment.get().activate();
    }

    @Test
    public void testRedeployAfterExpiredValidationOverride() throws InterruptedException, IOException {
        // Old version of model fails, but application disables loading old models until 2016-10-10, so deployment works
        ManualClock clock = new ManualClock("2016-10-09T00:00:00");
        List<ModelFactory> modelFactories = new ArrayList<>();
        modelFactories.add(DeployTester.createDefaultModelFactory(clock));
        modelFactories.add(DeployTester.createFailingModelFactory(Version.fromIntValues(1, 0, 0))); // older than default
        DeployTester tester = new DeployTester("src/test/apps/validationOverride/", modelFactories);
        tester.deployApp("myApp");

        // Redeployment from local active works
        {
            Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();
            assertTrue(deployment.isPresent());
            deployment.get().prepare();
            deployment.get().activate();
        }

        clock.advance(Duration.ofDays(2)); // validation override expires

        // Redeployment from local active also works after the validation override expires
        {
            Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();
            assertTrue(deployment.isPresent());
            deployment.get().prepare();
            deployment.get().activate();
        }
        
        // However, redeployment from the outside fails after this date
        {
            try {
                tester.deployApp("myApp");
                fail("Expected redeployment to fail");
            }
            catch (IllegalArgumentException expected) {
                // success
            }
        }
    }

}
