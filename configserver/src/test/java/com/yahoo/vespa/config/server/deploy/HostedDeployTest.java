// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.provision.Host;
import com.yahoo.config.model.provision.Hosts;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Version;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import static com.yahoo.vespa.config.server.deploy.DeployTester.CountingModelFactory;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class HostedDeployTest {

    @Test
    public void testRedeployWithVersion() {
        DeployTester tester = new DeployTester("src/test/apps/hosted/", createConfigserverConfig());
        tester.deployApp("myApp", "4.5.6", Instant.now());

        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();
        assertTrue(deployment.isPresent());
        deployment.get().prepare();
        deployment.get().activate();
        assertEquals("4.5.6", ((Deployment)deployment.get()).session().getVespaVersion().toString());
    }

    @Test
    public void testRedeploy() {
        DeployTester tester = new DeployTester("src/test/apps/hosted/", createConfigserverConfig());
        tester.deployApp("myApp", Instant.now());

        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();
        assertTrue(deployment.isPresent());
        deployment.get().prepare();
        deployment.get().activate();
    }

    @Test
    public void testDeployMultipleVersions() {
        ManualClock clock = new ManualClock("2016-10-09T00:00:00");
        List<ModelFactory> modelFactories = new ArrayList<>();
        modelFactories.add(DeployTester.createModelFactory(Version.fromString("6.1.0"), clock));
        modelFactories.add(DeployTester.createModelFactory(Version.fromString("6.2.0"), clock));
        modelFactories.add(DeployTester.createModelFactory(Version.fromString("7.0.0"), clock));
        DeployTester tester = new DeployTester("src/test/apps/hosted/", modelFactories, createConfigserverConfig(), clock, Zone.defaultZone());
        ApplicationId app = tester.deployApp("myApp", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(app).getHosts().size());

    }

    /** Test that unused versions are skipped in dev */
    @Test
    public void testDeployMultipleVersionsInDev() {
        List<Host> hosts = new ArrayList<>();
        hosts.add(createHost("host1", "6.0.0"));
        hosts.add(createHost("host2", "6.0.2"));
        hosts.add(createHost("host3", "7.1.0"));
        InMemoryProvisioner provisioner = new InMemoryProvisioner(new Hosts(hosts), true);
        ManualClock clock = new ManualClock("2016-10-09T00:00:00");

        CountingModelFactory factory600 = DeployTester.createModelFactory(Version.fromString("6.0.0"), clock);
        CountingModelFactory factory610 = DeployTester.createModelFactory(Version.fromString("6.1.0"), clock);
        CountingModelFactory factory620 = DeployTester.createModelFactory(Version.fromString("6.2.0"), clock);
        CountingModelFactory factory700 = DeployTester.createModelFactory(Version.fromString("7.0.0"), clock);
        CountingModelFactory factory710 = DeployTester.createModelFactory(Version.fromString("7.1.0"), clock);
        CountingModelFactory factory720 = DeployTester.createModelFactory(Version.fromString("7.2.0"), clock);
        List<ModelFactory> modelFactories = new ArrayList<>();
        modelFactories.add(factory600);
        modelFactories.add(factory610);
        modelFactories.add(factory620);
        modelFactories.add(factory700);
        modelFactories.add(factory710);
        modelFactories.add(factory720);

        DeployTester tester = new DeployTester("src/test/apps/hosted/", modelFactories, createConfigserverConfig(),
                                               clock, new Zone(Environment.dev, RegionName.defaultName()), provisioner);
        ApplicationId app = tester.deployApp("myApp", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(app).getHosts().size());

        assertEquals(1, factory600.creationCount());
        assertEquals(0, factory610.creationCount());
        assertEquals(1, factory620.creationCount());
        assertEquals(0, factory700.creationCount());
        assertEquals(1, factory710.creationCount());
        assertEquals("Newest is always included", 1, factory720.creationCount());
    }

    @Test
    public void testRedeployAfterExpiredValidationOverride() {
        // Old version of model fails, but application disables loading old models until 2016-10-10, so deployment works
        ManualClock clock = new ManualClock("2016-10-09T00:00:00");
        List<ModelFactory> modelFactories = new ArrayList<>();
        modelFactories.add(DeployTester.createModelFactory(clock));
        modelFactories.add(DeployTester.createFailingModelFactory(Version.fromIntValues(1, 0, 0))); // older than default
        DeployTester tester = new DeployTester("src/test/apps/validationOverride/", modelFactories, createConfigserverConfig());
        tester.deployApp("myApp", clock.instant());

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
                tester.deployApp("myApp", Instant.now());
                fail("Expected redeployment to fail");
            }
            catch (Exception expected) {
                // success
            }
        }
    }

    private static ConfigserverConfig createConfigserverConfig() {
        return new ConfigserverConfig(new ConfigserverConfig.Builder()
                                              .configServerDBDir(Files.createTempDir().getAbsolutePath())
                                              .configDefinitionsDir(Files.createTempDir().getAbsolutePath())
                                              .hostedVespa(true)
                                              .multitenant(true));
    }

    private Host createHost(String hostname, String version) {
        return new Host(hostname, Collections.emptyList(), Optional.empty(), Optional.of(com.yahoo.component.Version.fromString(version)));
    }

}
