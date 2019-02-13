// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.provision.Host;
import com.yahoo.config.model.provision.Hosts;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.server.configchange.MockRestartAction;
import com.yahoo.vespa.config.server.configchange.RestartActions;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;
import com.yahoo.vespa.config.server.model.TestModelFactory;
import com.yahoo.vespa.config.server.session.LocalSession;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.vespa.config.server.deploy.DeployTester.CountingModelFactory;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
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
        tester.deployApp("src/test/apps/hosted/", "4.5.6", Instant.now());

        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive(tester.applicationId());
        assertTrue(deployment.isPresent());
        deployment.get().prepare();
        deployment.get().activate();
        assertEquals("4.5.6", ((Deployment)deployment.get()).session().getVespaVersion().toString());
    }

    @Test
    public void testRedeploy() {
        DeployTester tester = new DeployTester(createConfigserverConfig());
        ApplicationId appId = tester.applicationId();
        tester.deployApp("src/test/apps/hosted/");
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
        List<ModelFactory> modelFactories =
                Arrays.asList(DeployTester.createModelFactory(Version.fromString("6.1.0"), clock),
                              DeployTester.createModelFactory(Version.fromString("6.2.0"), clock),
                              DeployTester.createModelFactory(Version.fromString("7.0.0"), clock));
        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig(), clock, Zone.defaultZone());
        tester.deployApp("src/test/apps/hosted/", "6.2.0", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());
    }

    /**
     * Test that only the minimal set of models are created (model versions used on hosts, the wanted version
     * and the latest version for the latest major)
     */
    @Test
    public void testCreateOnlyNeededModelVersions() {
        List<Host> hosts = Arrays.asList(createHost("host1", "6.0.0"),
                                         createHost("host2", "6.1.0"),
                                         createHost("host3")); //Use a host with no version as well
        InMemoryProvisioner provisioner = new InMemoryProvisioner(new Hosts(hosts), true);

        CountingModelFactory factory600 = DeployTester.createModelFactory(Version.fromString("6.0.0"));
        CountingModelFactory factory610 = DeployTester.createModelFactory(Version.fromString("6.1.0"));
        CountingModelFactory factory620 = DeployTester.createModelFactory(Version.fromString("6.2.0"));
        CountingModelFactory factory700 = DeployTester.createModelFactory(Version.fromString("7.0.0"));
        CountingModelFactory factory710 = DeployTester.createModelFactory(Version.fromString("7.1.0"));
        CountingModelFactory factory720 = DeployTester.createModelFactory(Version.fromString("7.2.0"));
        List<ModelFactory> modelFactories = Arrays.asList(factory600,
                                                          factory610,
                                                          factory620,
                                                          factory700,
                                                          factory710,
                                                          factory720);

        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig(), Clock.systemUTC(), provisioner);
        // Deploy with version that does not exist on hosts, the model for this version should also be created
        tester.deployApp("src/test/apps/hosted/", "7.0.0", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());

        // Check >0 not ==0 as the session watcher thread is running and will redeploy models in the background
        assertTrue(factory600.creationCount() > 0);
        assertTrue(factory610.creationCount() > 0);
        assertFalse(factory620.creationCount() > 0); // Latest model version on a major, but not for the latest major
        assertTrue(factory700.creationCount() > 0);  // Wanted version, also needs to be built
        assertFalse(factory710.creationCount() > 0);
        assertTrue("Newest is always included", factory720.creationCount() > 0);
    }


    /**
     * Test that only the minimal set of models are created (the wanted version and the latest version for
     * the latest major, since nodes are without version)
     */
    @Test
    public void testCreateOnlyNeededModelVersionsNewNodes() {
        List<Host> hosts = Arrays.asList(createHost("host1"), createHost("host2"), createHost("host3"));
        InMemoryProvisioner provisioner = new InMemoryProvisioner(new Hosts(hosts), true);

        CountingModelFactory factory600 = DeployTester.createModelFactory(Version.fromString("6.0.0"));
        CountingModelFactory factory610 = DeployTester.createModelFactory(Version.fromString("6.1.0"));
        CountingModelFactory factory700 = DeployTester.createModelFactory(Version.fromString("7.0.0"));
        CountingModelFactory factory720 = DeployTester.createModelFactory(Version.fromString("7.2.0"));
        List<ModelFactory> modelFactories = Arrays.asList(factory600, factory610, factory700, factory720);

        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig(), Clock.systemUTC(), provisioner);
        // Deploy with version that does not exist on hosts, the model for this version should also be created
        tester.deployApp("src/test/apps/hosted/", "7.0.0", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());

        // Check >0 not ==0 as the session watcher thread is running and will redeploy models in the background
        assertTrue(factory700.creationCount() > 0);
        assertTrue("Newest model for latest major version is always included", factory720.creationCount() > 0);
    }

    /**
     * Tests that we create the minimal set of models, but latest model version is created for
     * previous major if creating latest model version on latest major version fails
     **/
    @Test
    public void testCreateLatestMajorOnPreviousMajorIfItFailsOnNewestMajor() {
        List<Host> hosts = Arrays.asList(createHost("host1", "6.0.0"),
                                         createHost("host2", "6.1.0"),
                                         createHost("host3", "6.1.0"));
        InMemoryProvisioner provisioner = new InMemoryProvisioner(new Hosts(hosts), true);

        CountingModelFactory factory600 = DeployTester.createModelFactory(Version.fromString("6.0.0"));
        CountingModelFactory factory610 = DeployTester.createModelFactory(Version.fromString("6.1.0"));
        ModelFactory factory720 = DeployTester.createFailingModelFactory(Version.fromString("7.2.0"));
        List<ModelFactory> modelFactories = Arrays.asList(factory600, factory610, factory720);

        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig(), Clock.systemUTC(), provisioner);
        tester.deployApp("src/test/apps/hosted/", "6.0.0", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());

        // Check >0 not ==0 as the session watcher thread is running and will redeploy models in the background
        assertTrue(factory600.creationCount() > 0);
        assertTrue("Latest model for previous major version is included if latest model for latest major version fails to build",
                   factory610.creationCount() > 0);
    }

    /**
     * Tests that we fail deployment if a needed model version fails to be created
     **/
    @Test(expected = InvalidApplicationException.class)
    public void testDeploymentFailsIfNeededModelVersionFails() {
        List<Host> hosts = Arrays.asList(createHost("host1", "7.0.0"),
                                         createHost("host2", "7.0.0"),
                                         createHost("host3", "7.0.0"));
        InMemoryProvisioner provisioner = new InMemoryProvisioner(new Hosts(hosts), true);

        ModelFactory factory700 = DeployTester.createFailingModelFactory(Version.fromString("7.0.0"));
        CountingModelFactory factory710 = DeployTester.createModelFactory(Version.fromString("7.1.0"));
        List<ModelFactory> modelFactories = Arrays.asList(factory700, factory710);

        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig(), Clock.systemUTC(), provisioner);
        tester.deployApp("src/test/apps/hosted/", "7.1.0", Instant.now());
    }

    /**
     * Test that deploying an application works when there are no allocated hosts in the system
     * (the bootstrap a new zone case, so deploying the routing app since that is the first deployment
     * that will be done)
     **/
    @Test
    public void testCreateOnlyNeededModelVersionsWhenNoHostsAllocated() {
        List<Host> hosts = Collections.singletonList(createHost("host1"));
        InMemoryProvisioner provisioner = new InMemoryProvisioner(new Hosts(hosts), true);
        ManualClock clock = new ManualClock("2016-10-09T00:00:00");

        CountingModelFactory factory700 = DeployTester.createModelFactory(Version.fromString("7.0.0"), clock);
        CountingModelFactory factory720 = DeployTester.createModelFactory(Version.fromString("7.2.0"), clock);
        List<ModelFactory> modelFactories = Arrays.asList(factory700, factory720);

        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig(),
                                               clock, new Zone(Environment.dev, RegionName.defaultName()), provisioner);
        tester.deployApp("src/test/apps/hosted-routing-app/", "7.2.0", Instant.now());
        assertFalse(factory700.creationCount() > 0);
        assertTrue("Newest is always included", factory720.creationCount() > 0);
    }

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
        ApplicationId applicationId = tester.applicationId();
        // Deploy with oldest version
        tester.deployApp("src/test/apps/hosted/", "6.0.0", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(applicationId).getHosts().size());

        // Deploy with version that does not exist on hosts and with app package that has no write access control,
        // validation of access control should not be done, since the app is already deployed in prod
        tester.deployApp("src/test/apps/hosted-no-write-access-control", "6.1.0", Instant.now());
        assertEquals(3, tester.getAllocatedHostsOf(applicationId).getHosts().size());
    }

    @Test
    public void testRedeployAfterExpiredValidationOverride() {
        // Old version of model fails, but application disables loading old models until 2016-10-10, so deployment works
        ManualClock clock = new ManualClock("2016-10-09T00:00:00");
        List<ModelFactory> modelFactories = new ArrayList<>();
        modelFactories.add(DeployTester.createModelFactory(clock));
        modelFactories.add(DeployTester.createFailingModelFactory(new Version(1, 0, 0))); // older than default
        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig());
        tester.deployApp("src/test/apps/validationOverride/", clock.instant());

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

    @Test
    public void testThatConfigChangeActionsAreCollectedFromAllModels() {
        List<Host> hosts = Arrays.asList(createHost("host1", "6.1.0"),
                                         createHost("host2", "6.2.0"),
                                         createHost("host3", "6.2.0"));
        InMemoryProvisioner provisioner = new InMemoryProvisioner(new Hosts(hosts), true);
        List<ServiceInfo> services = Collections.singletonList(
                new ServiceInfo("serviceName", "serviceType", null, new HashMap<>(), "configId", "hostName"));

        List<ModelFactory> modelFactories = Arrays.asList(
                new ConfigChangeActionsModelFactory(new Version(6, 1, 0), new MockRestartAction("change", services)),
                new ConfigChangeActionsModelFactory(new Version(6, 2, 0), new MockRestartAction("other change", services)));

        DeployTester tester = new DeployTester(modelFactories, createConfigserverConfig(), Clock.systemUTC(), provisioner);
        PrepareResult prepareResult = tester.deployApp("src/test/apps/hosted/", "6.2.0", Instant.now());

        assertEquals(3, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());
        List<RestartActions.Entry> actions = prepareResult.configChangeActions().getRestartActions().getEntries();
        assertThat(actions.size(), is(1));
        assertThat(actions.get(0).getMessages(), equalTo(ImmutableSet.of("change", "other change")));
    }

    @Test
    public void testDeployWithClusterRotations() {
        CountingModelFactory modelFactory = DeployTester.createModelFactory(Version.fromString("4.5.6"), Clock.systemUTC());
        DeployTester tester = new DeployTester(Collections.singletonList(modelFactory), createConfigserverConfig());
        ApplicationId applicationId = tester.applicationId();

        tester.deployApp("src/test/apps/hosted/", "4.5.6", Instant.now());
        Set<HostSpec> containers = tester.getAllocatedHostsOf(applicationId).getHosts().stream()
                                         .filter(h -> h.membership().get().cluster().type() == ClusterSpec.Type.container)
                                         .collect(Collectors.toSet());
        assertFalse("Allocated container hosts", containers.isEmpty());

        Set<RotationName> expected = Set.of(RotationName.from("eu-cluster"), RotationName.from("us-cluster"));
        for (HostSpec container : containers) {
            assertEquals(expected, container.membership().get().cluster().rotations());
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
        return new Host(hostname, Collections.emptyList(), Optional.empty(), Optional.of(Version.fromString(version)));
    }

    private Host createHost(String hostname) {
        return new Host(hostname, Collections.emptyList(), Optional.empty(), Optional.empty());
    }

    private static class ConfigChangeActionsModelFactory extends TestModelFactory {

        private final ConfigChangeAction action;

        ConfigChangeActionsModelFactory(Version vespaVersion, ConfigChangeAction action) {
            super(vespaVersion);
            this.action = action;
        }

        @Override
        public ModelCreateResult createAndValidateModel(ModelContext modelContext, ValidationParameters validationParameters) {
            ModelCreateResult result = super.createAndValidateModel(modelContext, validationParameters);
            return new ModelCreateResult(result.getModel(), Arrays.asList(action));
        }
    }

}
