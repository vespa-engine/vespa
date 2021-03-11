// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.provision.Host;
import com.yahoo.config.model.provision.Hosts;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.HostedConfigModelRegistry;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;
import com.yahoo.vespa.config.server.model.TestModelFactory;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.model.application.validation.change.VespaReindexAction;
import com.yahoo.vespa.model.application.validation.change.VespaRestartAction;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.yahoo.vespa.config.server.deploy.DeployTester.CountingModelFactory;
import static com.yahoo.vespa.config.server.deploy.DeployTester.createFailingModelFactory;
import static com.yahoo.vespa.config.server.deploy.DeployTester.createHostedModelFactory;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class HostedDeployTest {

    private final Zone prodZone = new Zone(Environment.prod, RegionName.defaultName());
    private final Zone devZone = new Zone(Environment.dev, RegionName.defaultName());

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testRedeployWithVersion() throws IOException {
        DeployTester tester = new DeployTester.Builder()
                .modelFactory(createHostedModelFactory(Version.fromString("4.5.6"), Clock.systemUTC()))
                .configserverConfig(createConfigserverConfig()).build();
        tester.deployApp("src/test/apps/hosted/", "4.5.6");

        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive(tester.applicationId());
        assertTrue(deployment.isPresent());
        deployment.get().activate();
        assertEquals("4.5.6", ((Deployment) deployment.get()).session().getVespaVersion().toString());
    }

    @Test
    public void testRedeploy() throws IOException {
        DeployTester tester = new DeployTester.Builder()
                .modelFactory(createHostedModelFactory())
                .configserverConfig(createConfigserverConfig()).build();
        ApplicationId appId = tester.applicationId();
        tester.deployApp("src/test/apps/hosted/");
        assertFalse(tester.applicationRepository().getActiveSession(appId).getMetaData().isInternalRedeploy());

        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();
        assertTrue(deployment.isPresent());
        deployment.get().activate();
        assertTrue(tester.applicationRepository().getActiveSession(appId).getMetaData().isInternalRedeploy());
    }

    @Test
    public void testReDeployWithWantedDockerImageRepositoryAndAthenzDomain() throws IOException {
        DeployTester tester = new DeployTester.Builder()
                .modelFactory(createHostedModelFactory(Version.fromString("4.5.6"), Clock.systemUTC()))
                .configserverConfig(createConfigserverConfig()).build();
        String dockerImageRepository = "docker.foo.com:4443/bar/baz";
        tester.deployApp("src/test/apps/hosted/", Instant.now(), new PrepareParams.Builder()
                .vespaVersion("4.5.6")
                .dockerImageRepository(dockerImageRepository)
                .athenzDomain("myDomain"));

        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive(tester.applicationId());
        assertTrue(deployment.isPresent());
        deployment.get().activate();
        assertEquals("4.5.6", ((Deployment) deployment.get()).session().getVespaVersion().toString());
        assertEquals(DockerImage.fromString(dockerImageRepository), ((Deployment) deployment.get()).session().getDockerImageRepository().get());
        assertEquals("myDomain", ((Deployment) deployment.get()).session().getAthenzDomain().get().value());
    }

    @Test
    public void testRedeployWithTenantSecretStores() throws IOException {
        List<TenantSecretStore> tenantSecretStores = List.of(new TenantSecretStore("foo", "123", "role"));
        DeployTester tester = new DeployTester.Builder()
                .modelFactory(createHostedModelFactory(Version.fromString("4.5.6"), Clock.systemUTC()))
                .configserverConfig(createConfigserverConfig()).build();
        tester.deployApp("src/test/apps/hosted/", Instant.now(), new PrepareParams.Builder()
                .tenantSecretStores(tenantSecretStores));

        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive(tester.applicationId());
        assertTrue(deployment.isPresent());
        deployment.get().activate();
        assertEquals(tenantSecretStores, ((Deployment) deployment.get()).session().getTenantSecretStores());
    }

    @Test
    public void testDeployMultipleVersions() throws IOException {
        List<ModelFactory> modelFactories = List.of(createHostedModelFactory(Version.fromString("6.1.0")),
                                                    createHostedModelFactory(Version.fromString("6.2.0")),
                                                    createHostedModelFactory(Version.fromString("7.0.0")));
        DeployTester tester = new DeployTester.Builder().modelFactories(modelFactories).configserverConfig(createConfigserverConfig()).build();
        tester.deployApp("src/test/apps/hosted/", "6.2.0");
        assertEquals(7, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());
    }

    /**
     * Test that only the minimal set of models are created (model versions used on hosts, the wanted version
     * and the latest version for the latest major)
     */
    @Test
    public void testCreateOnlyNeededModelVersions() throws IOException {
        List<Host> hosts = createHosts(7, "6.0.0", "6.1.0", null, "6.1.0"); // Use a host without a version as well.

        CountingModelFactory factory600 = createHostedModelFactory(Version.fromString("6.0.0"));
        CountingModelFactory factory610 = createHostedModelFactory(Version.fromString("6.1.0"));
        CountingModelFactory factory620 = createHostedModelFactory(Version.fromString("6.2.0"));
        CountingModelFactory factory700 = createHostedModelFactory(Version.fromString("7.0.0"));
        CountingModelFactory factory710 = createHostedModelFactory(Version.fromString("7.1.0"));
        CountingModelFactory factory720 = createHostedModelFactory(Version.fromString("7.2.0"));
        List<ModelFactory> modelFactories = List.of(factory600, factory610, factory620,
                                                    factory700, factory710, factory720);

        DeployTester tester = createTester(hosts, modelFactories, prodZone);
        // Deploy with version that does not exist on hosts, the model for this version should also be created
        tester.deployApp("src/test/apps/hosted/", "7.0.0");
        assertEquals(7, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());

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
    public void testCreateOnlyNeededModelVersionsNewNodes() throws IOException {
        List<Host> hosts = createHosts(7, (String) null);

        CountingModelFactory factory600 = createHostedModelFactory(Version.fromString("6.0.0"));
        CountingModelFactory factory610 = createHostedModelFactory(Version.fromString("6.1.0"));
        CountingModelFactory factory700 = createHostedModelFactory(Version.fromString("7.0.0"));
        CountingModelFactory factory720 = createHostedModelFactory(Version.fromString("7.2.0"));
        List<ModelFactory> modelFactories = List.of(factory600, factory610, factory700, factory720);

        DeployTester tester = createTester(hosts, modelFactories, prodZone);
        // Deploy with version that does not exist on hosts, the model for this version should also be created
        tester.deployApp("src/test/apps/hosted/", "7.0.0");
        assertEquals(7, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());

        // Check >0 not ==0 as the session watcher thread is running and will redeploy models in the background
        assertTrue(factory700.creationCount() > 0);
        assertTrue("Newest model for latest major version is always included", factory720.creationCount() > 0);
    }

    /**
     * Test that deploying an application in a manually deployed zone creates all needed model versions
     * (not just the latest one, manually deployed apps always have skipOldConfigModels set to true)
     */
    @Test
    public void testCreateNeededModelVersionsForManuallyDeployedApps() throws IOException {
        List<Host> hosts = createHosts(5, "7.0.0");

        CountingModelFactory factory700 = createHostedModelFactory(Version.fromString("7.0.0"), devZone);
        CountingModelFactory factory710 = createHostedModelFactory(Version.fromString("7.1.0"), devZone);
        CountingModelFactory factory720 = createHostedModelFactory(Version.fromString("7.2.0"), devZone);
        List<ModelFactory> modelFactories = List.of(factory700, factory710, factory720);

        DeployTester tester = createTester(hosts, modelFactories, devZone);
        // Deploy with version that does not exist on hosts, the model for this version should also be created
        tester.deployApp("src/test/apps/hosted/", "7.2.0");
        assertEquals(5, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());

        // Check >0 not ==0 as the session watcher thread is running and will redeploy models in the background
        // Nodes are on 7.0.0 (should be created), no nodes on 7.1.0 (should not be created), 7.2.0 should always be created
        assertTrue(factory700.creationCount() > 0);
        assertFalse(factory710.creationCount() > 0);
        assertTrue("Newest model for latest major version is always included", factory720.creationCount() > 0);
    }

    /**
     * Test that deploying an application in a manually deployed zone creates latest model version successfully,
     * even if creating one of the older model fails
     */
    @Test
    public void testCreateModelVersionsForManuallyDeployedAppsWhenCreatingFailsForOneVersion() throws IOException {
        List<Host> hosts = createHosts(5, "7.0.0");

        ModelFactory factory700 = createFailingModelFactory(Version.fromString("7.0.0"));
        CountingModelFactory factory720 = createHostedModelFactory(Version.fromString("7.2.0"), devZone);
        List<ModelFactory> modelFactories = List.of(factory700, factory720);

        DeployTester tester = createTester(hosts, modelFactories, devZone, Clock.systemUTC());
        // Deploy with version that does not exist on hosts, the model for this version should be created even
        // if creating 7.0.0 fails
        tester.deployApp("src/test/apps/hosted/", "7.2.0");
        assertEquals(5, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());

        // Check >0 not ==0 as the session watcher thread is running and will redeploy models in the background
        assertTrue("Newest model for latest major version is always included", factory720.creationCount() > 0);
    }

    /**
     * Tests that we create the minimal set of models and that version 7.x is created
     * if creating version 8.x fails (to support upgrades to new major version for applications
     * that are still using features that do not work on version 8.x)
     **/
    @Test
    public void testCreateLatestMajorOnPreviousMajorIfItFailsOnMajorVersion8() throws IOException {
        deployWithModelForLatestMajorVersionFailing(8);
    }

    /**
     * Tests that we fail deployment for version 7.x if creating version 7.x fails (i.e. that we do not skip
     * building 7.x and only build version 6.x). Skipping creation of models for a major version is only supported
     * for major version >= 8 (see test above) or when major-version=6 is set in application package.
     **/
    @Test(expected = InvalidApplicationException.class)
    public void testFailingToCreateModelVersion7FailsDeployment() throws IOException {
        deployWithModelForLatestMajorVersionFailing(7);
    }

    /**
     * Tests that we create the minimal set of models, but latest model version is created for
     * previous major if creating latest model version on latest major version fails
     **/
    private void deployWithModelForLatestMajorVersionFailing(int newestMajorVersion) throws IOException {
        int oldestMajorVersion = newestMajorVersion - 1;
        String oldestVersion = oldestMajorVersion + ".0.0";
        String newestOnOldMajorVersion = oldestMajorVersion + ".1.0";
        String newestOnNewMajorVersion = newestMajorVersion + ".2.0";
        List<Host> hosts = createHosts(7, oldestVersion, newestOnNewMajorVersion);

        CountingModelFactory factory1 = createHostedModelFactory(Version.fromString(oldestVersion));
        CountingModelFactory factory2 = createHostedModelFactory(Version.fromString(newestOnOldMajorVersion));
        ModelFactory factory3 = createFailingModelFactory(Version.fromString(newestOnNewMajorVersion));
        List<ModelFactory> modelFactories = List.of(factory1, factory2, factory3);

        DeployTester tester = createTester(hosts, modelFactories, prodZone);
        tester.deployApp("src/test/apps/hosted/", oldestVersion);
        assertEquals(7, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());

        // Check >0 not ==0 as the session watcher thread is running and will redeploy models in the background
        assertTrue(factory1.creationCount() > 0);
        assertTrue("Latest model for previous major version is included if latest model for latest major version fails to build",
                   factory2.creationCount() > 0);
    }

    /**
     * Tests that we fail deployment if a needed model version fails to be created
     **/
    @Test(expected = InvalidApplicationException.class)
    public void testDeploymentFailsIfNeededModelVersionFails() throws IOException {
        List<Host> hosts = createHosts(7, "7.0.0");

        List<ModelFactory> modelFactories = List.of(createFailingModelFactory(Version.fromString("7.0.0")),
                                                    createHostedModelFactory(Version.fromString("7.1.0")));

        DeployTester tester = createTester(hosts, modelFactories, prodZone);
        tester.deployApp("src/test/apps/hosted/", "7.1.0");
    }

    /**
     * Test that deploying an application works when there are no allocated hosts in the system
     * (the bootstrap a new zone case, so deploying the routing app since that is the first deployment
     * that will be done)
     **/
    @Test
    public void testCreateOnlyNeededModelVersionsWhenNoHostsAllocated() throws IOException {
        CountingModelFactory factory700 = createHostedModelFactory(Version.fromString("7.0.0"));
        CountingModelFactory factory720 = createHostedModelFactory(Version.fromString("7.2.0"));
        List<ModelFactory> modelFactories = List.of(factory700, factory720);

        DeployTester tester = createTester(createHosts(1, (String) null), modelFactories, prodZone);
        tester.deployApp("src/test/apps/hosted-routing-app/", "7.2.0");
        assertFalse(factory700.creationCount() > 0);
        assertTrue("Newest is always included", factory720.creationCount() > 0);
    }

    @Test
    public void testAccessControlIsOnlyCheckedWhenNoProdDeploymentExists() throws IOException {
        // Provisioner does not reuse hosts, so need twice as many hosts as app requires
        List<Host> hosts = createHosts(14, "6.0.0");

        List<ModelFactory> modelFactories = List.of(createHostedModelFactory(Version.fromString("6.0.0")),
                                                    createHostedModelFactory(Version.fromString("6.1.0")),
                                                    createHostedModelFactory(Version.fromString("6.2.0")));

        DeployTester tester = createTester(hosts, modelFactories, prodZone, Clock.systemUTC());
        ApplicationId applicationId = tester.applicationId();
        // Deploy with oldest version
        tester.deployApp("src/test/apps/hosted/", "6.0.0");
        assertEquals(7, tester.getAllocatedHostsOf(applicationId).getHosts().size());

        // Deploy with version that does not exist on hosts and with app package that has no write access control,
        // validation of access control should not be done, since the app is already deployed in prod
        tester.deployApp("src/test/apps/hosted-no-write-access-control", "6.1.0", Instant.now());
        assertEquals(7, tester.getAllocatedHostsOf(applicationId).getHosts().size());
    }

    @Test
    public void testRedeployAfterExpiredValidationOverride() throws IOException {
        // Old version of model fails, but application disables loading old models until 2016-10-10, so deployment works
        ManualClock clock = new ManualClock("2016-10-09T00:00:00");
        List<ModelFactory> modelFactories = List.of(createHostedModelFactory(clock),
                                                    createFailingModelFactory(Version.fromString("1.0.0"))); // older than default
        DeployTester tester = new DeployTester.Builder().modelFactories(modelFactories).configserverConfig(createConfigserverConfig()).build();
        tester.deployApp("src/test/apps/validationOverride/", clock.instant());

        // Redeployment from local active works
        {
            Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();
            assertTrue(deployment.isPresent());
            deployment.get().activate();
        }

        clock.advance(Duration.ofDays(2)); // validation override expires

        // Redeployment from local active also works after the validation override expires
        {
            Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();
            assertTrue(deployment.isPresent());
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
    public void testThatConfigChangeActionsAreCollectedFromAllModels() throws IOException {
        List<Host> hosts = createHosts(7, "6.1.0", "6.2.0");
        List<ServiceInfo> services = List.of(
                new ServiceInfo("serviceName", "serviceType", null, new HashMap<>(), "configId", "hostName"));

        List<ModelFactory> modelFactories = List.of(
                new ConfigChangeActionsModelFactory(Version.fromString("6.1.0"),
                                                    new VespaRestartAction(ClusterSpec.Id.from("test"), "change", services)),
                new ConfigChangeActionsModelFactory(Version.fromString("6.2.0"),
                                                    new VespaRestartAction(ClusterSpec.Id.from("test"), "other change", services)));

        DeployTester tester = createTester(hosts, modelFactories, prodZone);
        tester.deployApp("src/test/apps/hosted/", "6.2.0");

        assertEquals(7, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());
    }

    @Test
    public void testThatAllowedConfigChangeActionsAreActedUpon() throws IOException {
        List<Host> hosts = createHosts(7, "6.1.0");
        List<ServiceInfo> services = List.of(
                new ServiceInfo("serviceName", "serviceType", null, Map.of("clustername", "cluster"), "configId", "hostName"));

        ManualClock clock = new ManualClock(Instant.EPOCH);
        List<ModelFactory> modelFactories = List.of(
                new ConfigChangeActionsModelFactory(Version.fromString("6.1.0"),
                                                    VespaReindexAction.of(ClusterSpec.Id.from("test"), ValidationId.indexModeChange,
                                                                          "reindex please", services, "music"),
                                                    new VespaRestartAction(ClusterSpec.Id.from("test"), "change", services)));

        DeployTester tester = createTester(hosts, modelFactories, prodZone, clock);
        PrepareResult prepareResult = tester.deployApp("src/test/apps/hosted/", "6.1.0");

        assertEquals(7, tester.getAllocatedHostsOf(tester.applicationId()).getHosts().size());
        assertTrue(prepareResult.configChangeActions().getRestartActions().isEmpty()); // Handled by deployment.
        assertEquals(Optional.of(ApplicationReindexing.empty()
                                                      .withPending("cluster", "music", prepareResult.sessionId())),
                     tester.tenant().getApplicationRepo().database().readReindexingStatus(tester.applicationId()));
    }

    private ConfigserverConfig createConfigserverConfig() throws IOException {
        return createConfigserverConfig(Zone.defaultZone());
    }

    private ConfigserverConfig createConfigserverConfig(Zone zone) throws IOException {
        return new ConfigserverConfig(new ConfigserverConfig.Builder()
                                              .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                                              .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                                              .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                                              .hostedVespa(true)
                                              .multitenant(true)
                                              .region(zone.region().value())
                                              .environment(zone.environment().value())
                                              .system(zone.system().value()));
    }

    /** Create the given number of hosts using the supplied versions--the last version is repeated as needed. */
    private List<Host> createHosts(int count, String... versions) {
        return IntStream.rangeClosed(1, count)
                        .mapToObj(i -> createHost("host" + i, versions[Math.min(i, versions.length) - 1]))
                        .collect(toList());
    }

    private Host createHost(String hostname, String version) {
        return new Host(hostname, Collections.emptyList(), Optional.empty(), Optional.ofNullable(version).map(Version::fromString));
    }

    private DeployTester createTester(List<Host> hosts, List<ModelFactory> modelFactories, Zone zone) throws IOException {
        return createTester(hosts, modelFactories, zone, Clock.systemUTC());
    }

    private DeployTester createTester(List<Host> hosts, List<ModelFactory> modelFactories,
                                      Zone prodZone, Clock clock) throws IOException {
        return new DeployTester.Builder()
                .modelFactories(modelFactories)
                .configserverConfig(createConfigserverConfig(prodZone))
                .clock(clock)
                .zone(prodZone)
                .hostProvisioner(new InMemoryProvisioner(new Hosts(hosts), true, false)).build();
    }

    private static class ConfigChangeActionsModelFactory extends TestModelFactory {

        private final List<ConfigChangeAction> actions;

        ConfigChangeActionsModelFactory(Version vespaVersion, ConfigChangeAction... actions) {
            super(HostedConfigModelRegistry.create(), vespaVersion);
            this.actions = List.of(actions);
        }

        @Override
        public ModelCreateResult createAndValidateModel(ModelContext modelContext, ValidationParameters validationParameters) {
            ModelCreateResult result = super.createAndValidateModel(modelContext, validationParameters);
            return new ModelCreateResult(result.getModel(), actions);
        }
    }

}
