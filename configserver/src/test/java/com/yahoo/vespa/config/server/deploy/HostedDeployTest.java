// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Version;
import com.yahoo.test.ManualClock;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class HostedDeployTest {

    private static final String dockerRegistry = "foo.com:4443";
    private static final String dockerVespaBaseImage = "/vespa/ci";

    @Test
    public void testRedeployWithVersion() throws InterruptedException, IOException {
        DeployTester tester = new DeployTester("src/test/apps/hosted/", createConfigserverConfig());
        tester.deployApp("myApp", Optional.of("4.5.6"));

        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();
        assertTrue(deployment.isPresent());
        deployment.get().prepare();
        deployment.get().activate();
        assertEquals("4.5.6", ((Deployment)deployment.get()).session().getVespaVersion().toString());
    }

    @Test
    public void testRedeploy() throws InterruptedException, IOException {
        DeployTester tester = new DeployTester("src/test/apps/hosted/", createConfigserverConfig());
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
        DeployTester tester = new DeployTester("src/test/apps/validationOverride/", modelFactories, createConfigserverConfig());
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
            catch (Exception expected) {
                // success
            }
        }
    }

    @Test
    @Ignore //WIP
    public void testDeployWithDockerImage() throws InterruptedException, IOException {
        final String vespaVersion = "6.51.1";
        DeployTester tester = new DeployTester("src/test/apps/hosted/", createConfigserverConfig());
        ApplicationId applicationId = tester.deployApp("myApp", Optional.of(vespaVersion));
        assertProvisionInfo(vespaVersion, tester, applicationId);

        System.out.println("Redeploy");
        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();
        assertTrue(deployment.isPresent());
        deployment.get().prepare();
        deployment.get().activate();
        //assertProvisionInfo(vespaVersion, tester, applicationId);
    }

    private void assertProvisionInfo(String vespaVersion, DeployTester tester, ApplicationId applicationId) {
        tester.getProvisionInfoFromDeployedApp(applicationId).getHosts().stream()
              .forEach(h -> assertEquals(dockerRegistry + dockerVespaBaseImage + ":" + vespaVersion,
                                         h.membership().get().cluster().dockerImage()));
    }

    private static ConfigserverConfig createConfigserverConfig() {
        return new ConfigserverConfig(new ConfigserverConfig.Builder()
                                              .configServerDBDir(Files.createTempDir()
                                                                      .getAbsolutePath())
                                              .dockerRegistry(dockerRegistry)
                                              .dockerVespaBaseImage(dockerVespaBaseImage)
                                              .hostedVespa(true)
                                              .multitenant(true));
    }

}
