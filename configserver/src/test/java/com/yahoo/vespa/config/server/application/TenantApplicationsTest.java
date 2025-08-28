// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.concurrent.StripedExecutor;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.server.ConfigActivationListener;
import com.yahoo.vespa.config.server.ConfigServerDB;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.model.TestModelFactory;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import com.yahoo.vespa.curator.CompletionTimeoutException;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.VespaModelFactory;
import org.apache.curator.framework.CuratorFramework;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class TenantApplicationsTest {

    private static final TenantName tenantName = TenantName.from("tenant");
    private static final Version vespaVersion = VespaModelFactory.createTestFactory().version();

    private Curator curator;
    private CuratorFramework curatorFramework;
    private ConfigserverConfig configserverConfig;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        curator = new MockCurator();
        curatorFramework = curator.framework();
        configserverConfig = new ConfigserverConfig.Builder()
                .payloadCompressionType(ConfigserverConfig.PayloadCompressionType.Enum.UNCOMPRESSED)
                .configServerDBDir(tempFolder.newFolder("configserverdb").getAbsolutePath())
                .configDefinitionsDir(tempFolder.newFolder("configdefinitions").getAbsolutePath())
                .build();
        TenantRepository tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .withCurator(curator)
                .withModelFactoryRegistry(createRegistry())
                .build();
        tenantRepository.addTenant(TenantRepository.HOSTED_VESPA_TENANT);
        tenantRepository.addTenant(tenantName);
    }

    @Test
    public void require_that_applications_are_read_from_zookeeper() throws Exception {
        writeApplicationData(createApplicationId("foo"), 3L);
        writeApplicationData(createApplicationId("bar"), 4L);
        TenantApplications repo = createZKAppRepo();
        List<ApplicationId> applications = repo.activeApplications();
        assertEquals(2, applications.size());
        assertEquals("bar", applications.get(0).application().value());
        assertEquals("foo", applications.get(1).application().value());
        assertEquals(4, repo.requireActiveSessionOf(applications.get(0)));
        assertEquals(3, repo.requireActiveSessionOf(applications.get(1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_requesting_session_for_unknown_application_throws_exception() {
        TenantApplications repo = createZKAppRepo();
        repo.requireActiveSessionOf(createApplicationId("nonexistent"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_requesting_session_for_empty_application_throws_exception() throws Exception {
        ApplicationId baz = createApplicationId("baz");
        // No data in node
        curatorFramework.create().creatingParentsIfNeeded()
                .forPath(TenantRepository.getApplicationsPath(tenantName).append(baz.serializedForm()).getAbsolute());
        TenantApplications repo = createZKAppRepo();
        repo.requireActiveSessionOf(baz);
    }

    @Test
    public void require_that_application_ids_can_be_written() throws Exception {
        TenantApplications repo = createZKAppRepo();
        ApplicationId myapp = createApplicationId("myapp");
        repo.createApplication(myapp);
        writeActiveTransaction(repo, myapp, 3);
        String path = TenantRepository.getApplicationsPath(tenantName).append(myapp.serializedForm()).getAbsolute();
        assertNotNull(curatorFramework.checkExists().forPath(path));
        assertEquals("{\"applicationId\":\"tenant:myapp:myinst\",\"activeSession\":3,\"lastDeployedSession\":3}",
                     Utf8.toString(curatorFramework.getData().forPath(path)));
        writeActiveTransaction(repo, myapp, 5);
        assertNotNull(curatorFramework.checkExists().forPath(path));
        assertEquals("{\"applicationId\":\"tenant:myapp:myinst\",\"activeSession\":5,\"lastDeployedSession\":5}",
                     Utf8.toString(curatorFramework.getData().forPath(path)));
    }

    @Test
    public void require_that_application_ids_can_be_deleted() {
        TenantApplications repo = createZKAppRepo();
        ApplicationId id1 = createApplicationId("myapp");
        ApplicationId id2 = createApplicationId("myapp2");
        repo.createApplication(id1);
        repo.createApplication(id2);
        writeActiveTransaction(repo, id1, 1);
        writeActiveTransaction(repo, id2, 1);
        assertEquals(2, repo.activeApplications().size());
        deleteApplication(repo, id1);
        assertEquals(1, repo.activeApplications().size());
        deleteApplication(repo, id2);
        assertEquals(0, repo.activeApplications().size());
    }

    private static ApplicationVersions createApplicationVersions(ApplicationId id, Version version) throws IOException, SAXException {
        VespaModel model = new VespaModel(new NullConfigModelRegistry(),
                                          new DeployState.Builder().wantedNodeVespaVersion(version)
                                                                   .applicationPackage(FilesApplicationPackage.fromDir(new File("src/test/apps/app"), Map.of()))
                                                                   .build());
        return ApplicationVersions.from(new Application(model,
                                                        new ServerCache(),
                                                        1,
                                                        Version.emptyVersion,
                                                        MetricUpdater.createTestUpdater(),
                                                        id));
    }

    @Test
    public void major_version_compatibility() throws Exception {
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        TenantApplications applications = createZKAppRepo(flagSource);
        ApplicationId app1 = createApplicationId("myapp");
        applications.createApplication(app1);
        writeActiveTransaction(applications, app1, 1);

        Version deployedVersion0 = Version.fromString("6.1");
        applications.activateApplication(createApplicationVersions(app1, deployedVersion0), 1);
        assertTrue("Empty version is compatible", applications.compatibleWith(Optional.empty(), app1));

        Version nodeVersion0 = Version.fromString("6.0");
        assertTrue("Lower version is compatible", applications.compatibleWith(Optional.of(nodeVersion0), app1));

        Version deployedVersion1 = Version.fromString("7.1");
        applications.activateApplication(createApplicationVersions(app1, deployedVersion1), 1);
        assertTrue("New major is compatible", applications.compatibleWith(Optional.of(nodeVersion0), app1));

        flagSource.withListFlag(PermanentFlags.INCOMPATIBLE_VERSIONS.id(), List.of("8"), String.class);
        Version deployedVersion2 = Version.fromString("8.1");
        applications.activateApplication(createApplicationVersions(app1, deployedVersion2), 1);
        assertFalse("New major is incompatible", applications.compatibleWith(Optional.of(nodeVersion0), app1));

        Version nodeVersion1 = Version.fromString("8.0");
        assertTrue("Node is compatible after upgrading", applications.compatibleWith(Optional.of(nodeVersion1), app1));
    }

    public static class MockConfigActivationListener implements ConfigActivationListener {
        public final AtomicInteger activated = new AtomicInteger(0);
        final AtomicInteger removed = new AtomicInteger(0);

        @Override
        public void configActivated(ApplicationVersions application) {
            activated.incrementAndGet();
        }

        @Override
        public void applicationRemoved(ApplicationId applicationId) {
            removed.incrementAndGet();
        }
    }

    @Test
    public void testListConfigs() throws IOException, SAXException {
        TenantApplications applications = createTenantApplications(TenantName.defaultName(), curator, configserverConfig, new MockConfigActivationListener(), new InMemoryFlagSource());
        assertFalse(applications.hasApplication(ApplicationId.defaultId(), Optional.of(vespaVersion)));

        VespaModel model = new VespaModel(FilesApplicationPackage.fromDir(new File("src/test/apps/app"), Map.of()));
        ApplicationId applicationId = ApplicationId.defaultId();
        applications.createApplication(applicationId);
        writeActiveTransaction(applications, applicationId, 1);
        applications.activateApplication(ApplicationVersions.from(new Application(model,
                                                                                  new ServerCache(),
                                                                                  1,
                                                                                  vespaVersion,
                                                                                  MetricUpdater.createTestUpdater(),
                                                                                  applicationId)),
                                         1);
        Set<ConfigKey<?>> configNames = applications.listConfigs(applicationId, Optional.of(vespaVersion), false);
        assertTrue(configNames.contains(new ConfigKey<>("sentinel", "hosts", "cloud.config")));

        configNames = applications.listConfigs(ApplicationId.defaultId(), Optional.of(vespaVersion), true);
        assertTrue(configNames.contains(new ConfigKey<>("documentmanager", "container", "document.config")));
        assertTrue(configNames.contains(new ConfigKey<>("documentmanager", "", "document.config")));
        assertTrue(configNames.contains(new ConfigKey<>("documenttypes", "", "document.config")));
        assertTrue(configNames.contains(new ConfigKey<>("documentmanager", "container", "document.config")));
        assertTrue(configNames.contains(new ConfigKey<>("health-monitor", "container", "container.jdisc.config")));
        assertTrue(configNames.contains(new ConfigKey<>("specific", "container", "project")));
    }

    @Test
    public void testAppendIdsInNonRecursiveListing() {
        TenantApplications applications = createTenantApplications(tenantName, curator, configserverConfig, new MockConfigActivationListener(), new InMemoryFlagSource());
        assertEquals(applications.appendOneLevelOfId("search/music", "search/music/container/default/qr.0"), "search/music/container");
        assertEquals(applications.appendOneLevelOfId("search", "search/music/container/default/qr.0"), "search/music");
        assertEquals(applications.appendOneLevelOfId("search/music/container/default/qr.0", "search/music/container/default/qr.0"), "search/music/container/default/qr.0");
        assertEquals(applications.appendOneLevelOfId("", "search/music/container/default/qr.0"), "search");
    }

    @Test
    public void testRemoveApplication2of3Respond() throws InterruptedException {
        TenantApplications applications = createZKAppRepo(new InMemoryFlagSource());
        Thread t1 = setupWaiter(applications);
        notifyCompletion(applications, 2);
        t1.join();
    }

    @Test
    public void testRemoveApplicationAllRespond() throws InterruptedException {
        TenantApplications applications = createZKAppRepo(new InMemoryFlagSource());
        Thread t1 = setupWaiter(applications);
        notifyCompletion(applications, 3);
        t1.join();
    }

    private Thread setupWaiter(TenantApplications applications) {
        Curator.CompletionWaiter waiter = applications.getRemoveApplicationWaiter(createApplicationId());
        Thread t1 = new Thread(() -> {
            try {
                waiter.awaitCompletion(Duration.ofSeconds(120));
            } catch (CompletionTimeoutException e) {
                fail("Waiting failed due to timeout");
            }
        });
        t1.start();
        return t1;
    }

    private void notifyCompletion(TenantApplications applications, int respondentCount) {
        IntStream.range(0, respondentCount)
                 .forEach(i -> applications.createRemoveApplicationWaiter(createApplicationId())
                                           .notifyCompletion());
    }

    private TenantApplications createZKAppRepo() {
        return createZKAppRepo(new InMemoryFlagSource());
    }

    private TenantApplications createZKAppRepo(InMemoryFlagSource flagSource) {
        return createTenantApplications(tenantName, curator, configserverConfig, new MockConfigActivationListener(), flagSource);
    }

    private static ApplicationId createApplicationId() {
        return createApplicationId("foo");
    }

    private static ApplicationId createApplicationId(String name) {
        return ApplicationId.from(tenantName.value(), name, "myinst");
    }

    private void writeApplicationData(ApplicationId applicationId, long sessionId) throws Exception {
        writeApplicationData(applicationId.serializedForm(), sessionId);
    }

    private void writeApplicationData(String applicationId, long sessionId) throws Exception {
        curatorFramework
                .create()
                .creatingParentsIfNeeded()
                .forPath(TenantRepository.getApplicationsPath(tenantName).append(applicationId).getAbsolute(),
                         Utf8.toAsciiBytes(sessionId));
    }

    private ModelFactoryRegistry createRegistry() {
        return new ModelFactoryRegistry(List.of(new TestModelFactory(vespaVersion),
                                                      new TestModelFactory(new Version(3, 2, 1))));
    }


    // For testing only
    private TenantApplications createTenantApplications(TenantName tenantName,
                                                        Curator curator,
                                                        ConfigserverConfig configserverConfig,
                                                        ConfigActivationListener configActivationListener, InMemoryFlagSource flagSource) {
        return new TenantApplications(tenantName,
                                      curator,
                                      new StripedExecutor<>(new InThreadExecutorService()),
                                      new InThreadExecutorService(),
                                      Metrics.createTestMetrics(),
                                      configActivationListener,
                                      configserverConfig,
                                      new HostRegistry(),
                                      new TenantFileSystemDirs(new ConfigServerDB(configserverConfig), tenantName),
                                      Clock.systemUTC(),
                                      flagSource);
    }

    private static void deleteApplication(TenantApplications repo, ApplicationId id1) {
        try (var transaction = repo.createDeleteTransaction(id1)) {
            transaction.commit();
        }
    }

    private void writeActiveTransaction(TenantApplications repo, ApplicationId id1, int x) {
        try (var transaction = new CuratorTransaction(curator)) {
            repo.createWriteActiveTransaction(transaction, id1, x).commit();
        }
    }

}
