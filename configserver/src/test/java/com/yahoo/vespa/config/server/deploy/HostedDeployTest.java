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

import com.yahoo.vespa.config.server.session.LocalSession;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class HostedDeployTest {

    @Test
    public void testRedeployWithVersion() {
        CountingModelFactory modelFactory = DeployTester.createModelFactory(Version.fromString("4.5.6"), Clock.systemUTC());
        DeployTester tester = new DeployTester(Collections.singletonList(modelFactory), createConfigserverConfig());
        tester.deployApp("src/test/apps/hosted/", "myApp", "4.5.6", Instant.now());

        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();
        assertTrue(deployment.isPresent());
        deployment.get().prepare();
        deployment.get().activate();
        assertEquals("4.5.6", ((Deployment)deployment.get()).session().getVespaVersion().toString());
    }

    @Test
    public void testRedeploy() {
        DeployTester tester = new DeployTester(createConfigserverConfig());
        ApplicationId appId = tester.deployApp("src/test/apps/hosted/", "myApp", Instant.now());
        LocalSession s1 = tester.applicationRepository().getActiveSession(appId);
        System.out.println("First session: " + s1.getSessionId());
        assertFalse(tester.applicationRepository().getActiveSession(appId).getMetaData().isInternalRedeploy());

        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();
        assertTrue(deployment.isPresent());
        deployment.get().prepare();
        deployment.get().activate();
        LocalSession s2 = tester.applicationRepository().getActiveSession(appId);
        System.out.println("Second session: " + s2.getSessionId());
        assertTrue(tester.applicationRepository().getActiveSession(appId).getMetaData().isInternalRedeploy());
    }

    @Test
    public void testDeployMultipleVersions() {
        ManualClock clock = new ManualClock("2016-10-09T00:00:00");
        List<ModelFactory> modelFactories = new ArrayList<>();
        modelFactories.add(DeployTester.createModelFactory(Version.fromString("6.1.0"), clock));
        modelFactories.add(DeployTester.createModelFactory(Version.fromString("6.2.0"), clock));
        modelFactories.add(DeployTester.createModelFactory(Version.fromString("7.0.0"), clock));
        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig(), clock, Zone.defaultZone());
        ApplicationId app = tester.deployApp("src/test/apps/hosted/", "myApp", "6.2.0", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(app).getHosts().size());
    }

    /** Test that only the minimal set of models are created (model versions used on hosts, the wanted version and the latest version) */
    @Test
    public void testCreateOnlyNeededModelVersions() {
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

        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig(),
                                               clock, new Zone(Environment.dev, RegionName.defaultName()), provisioner);
        // Deploy with version that does not exist on hosts, the model for this version should also be created
        ApplicationId app = tester.deployApp("src/test/apps/hosted/", "myApp", "7.0.0", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(app).getHosts().size());

        // Check >0 not ==0 as the session watcher thread is running and will redeploy models in the background
        assertTrue(factory600.creationCount() > 0);
        assertFalse(factory610.creationCount() > 0);
        assertTrue(factory620.creationCount() > 0);
        assertTrue(factory700.creationCount() > 0);
        assertTrue(factory710.creationCount() > 0);
        assertTrue("Newest is always included", factory720.creationCount() > 0);
    }

    // TODO: Test works now, but will fail when only building the minimal set of config model
    // versions in prod, see ModelsBuilder.buildModelVersions()
    @Test
    public void testAccessControlIsOnlyCheckedWhenNoProdDeploymentExists() {
        // Provisioner does not reuse hosts, so need twice as many hosts as app requires
        List<Host> hosts = IntStream.rangeClosed(1,6).mapToObj(i -> createHost("host" + i, "6.0.0")).collect(Collectors.toList());
        InMemoryProvisioner provisioner = new InMemoryProvisioner(new Hosts(hosts), true);

        CountingModelFactory factory600 = DeployTester.createModelFactory(Version.fromString("6.0.0"));
        CountingModelFactory factory610 = DeployTester.createModelFactory(Version.fromString("6.1.0"));
        CountingModelFactory factory620 = DeployTester.createModelFactory(Version.fromString("6.2.0"));
        List<ModelFactory> modelFactories = Arrays.asList(factory600, factory610, factory620);

        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig(),
                                               Clock.systemUTC(), new Zone(Environment.prod, RegionName.defaultName()), provisioner);
        // Deploy with oldest version
        ApplicationId app = tester.deployApp("src/test/apps/hosted/", "myApp", "6.0.0", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(app).getHosts().size());

        // Deploy with version that does not exist on hosts and with app package that has no write access control,
        // validation of access control should not be done, since the app is already deployed in prod
        app = tester.deployApp("src/test/apps/hosted-no-write-access-control", "myApp", "6.1.0", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(app).getHosts().size());
    }

    @Test
    public void testRedeployAfterExpiredValidationOverride() {
        // Old version of model fails, but application disables loading old models until 2016-10-10, so deployment works
        ManualClock clock = new ManualClock("2016-10-09T00:00:00");
        List<ModelFactory> modelFactories = new ArrayList<>();
        modelFactories.add(DeployTester.createModelFactory(clock));
        modelFactories.add(DeployTester.createFailingModelFactory(Version.fromIntValues(1, 0, 0))); // older than default
        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig());
        tester.deployApp("src/test/apps/validationOverride/", "myApp", clock.instant());

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
                tester.deployApp("src/test/apps/validationOverride/", "myApp", Instant.now());
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
