// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.Version;
import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.concurrent.StripedExecutor;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.ConfigServerDB;
import com.yahoo.vespa.config.server.MockSecretStore;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.TestConfigDefinitionRepo;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.application.TenantApplicationsTest;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionFactory;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.VespaModelFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TenantRepositoryTest {
    private static final TenantName tenant1 = TenantName.from("tenant1");
    private static final TenantName tenant2 = TenantName.from("tenant2");
    private static final TenantName tenant3 = TenantName.from("tenant3");

    private TenantRepository tenantRepository;
    private TenantApplicationsTest.MockConfigActivationListener listener;
    private MockTenantListener tenantListener;
    private Curator curator;
    private ConfigserverConfig configserverConfig;

    @SuppressWarnings("deprecation")
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setupSessions() throws IOException {
        curator = new MockCurator();
        listener = new TenantApplicationsTest.MockConfigActivationListener();
        tenantListener = new MockTenantListener();
        assertFalse(tenantListener.tenantsLoaded);
        configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        tenantRepository = new TestTenantRepository.Builder().withConfigserverConfig(configserverConfig)
                                                             .withCurator(curator)
                                                             .withConfigActivationListener(listener)
                                                             .withTenantListener(tenantListener)
                                                             .build();
        assertTrue(tenantListener.tenantsLoaded);
        tenantRepository.addTenant(tenant1);
        tenantRepository.addTenant(tenant2);
    }

    @After
    public void closeSessions() {
        tenantRepository.close();
    }

    @Test
    public void testStartUp() {
        assertEquals(tenantRepository.getTenant(tenant1).getName(), tenant1);
        assertEquals(tenantRepository.getTenant(tenant2).getName(), tenant2);
    }

    @Test
    public void testListenersAdded() throws IOException, SAXException {
        TenantApplications applicationRepo = tenantRepository.getTenant(tenant1).getApplicationRepo();
        ApplicationId id = ApplicationId.from(tenant1, ApplicationName.defaultName(), InstanceName.defaultName());
        applicationRepo.createApplication(id);
        applicationRepo.createPutTransaction(id, 4).commit();
        applicationRepo.activateApplication(ApplicationSet.from(new Application(new VespaModel(MockApplicationPackage.createEmpty()),
                                                                                new ServerCache(),
                                                                                4L,
                                                                                new Version(1, 2, 3),
                                                                                MetricUpdater.createTestUpdater(),
                                                                                id)),
                                            4);
        assertEquals(1, listener.activated.get());
    }

    @Test
    public void testTenantListenersNotified() throws Exception {
        tenantRepository.addTenant(tenant3);
        assertEquals("tenant3 not the last created tenant. Tenants: " + tenantRepository.getAllTenantNames() +
                             ", /config/v2/tenants: " + readZKChildren("/config/v2/tenants"),
                     tenant3, tenantListener.tenantCreatedName);
        tenantRepository.deleteTenant(tenant2);
        assertFalse(tenantRepository.getAllTenantNames().contains(tenant2));
        assertEquals(tenant2, tenantListener.tenantDeletedName);
    }

    @Test
    public void testAddTenant() throws Exception {
        Set<TenantName> allTenants = tenantRepository.getAllTenantNames();
        assertTrue(allTenants.contains(tenant1));
        assertTrue(allTenants.contains(tenant2));
        tenantRepository.addTenant(tenant3);
        assertZooKeeperTenantPathExists(tenant3);
        allTenants = tenantRepository.getAllTenantNames();
        assertTrue(allTenants.contains(tenant1));
        assertTrue(allTenants.contains(tenant2));
        assertTrue(allTenants.contains(tenant3));
    }

    @Test
    public void testDeleteTenant() throws Exception {
        assertZooKeeperTenantPathExists(tenant1);
        tenantRepository.deleteTenant(tenant1);
        assertFalse(tenantRepository.getAllTenantNames().contains(tenant1));

        try {
            tenantRepository.deleteTenant(TenantName.from("non-existing"));
            fail("deletion of non-existing tenant should fail");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeletingDefaultTenant()  {
        try {
            assertZooKeeperTenantPathExists(TenantName.defaultName());
        } catch (Exception e) {
            fail("default tenant does not exist");
        }
        tenantRepository.deleteTenant(TenantName.defaultName());
    }
    
    @Test
    public void testTenantWatching() throws Exception {
        TenantName newTenant = TenantName.from("newTenant");
        List<TenantName> expectedTenants = Arrays.asList(TenantName.defaultName(), newTenant);
        try {
            tenantRepository.addTenant(newTenant);
            // Poll for the watcher to pick up the tenant from zk, and add it
            int tries=0;
            while(true) {
                if (tries > 5000) fail("Didn't react on watch");
                if (tenantRepository.getAllTenantNames().containsAll(expectedTenants)) {
                    break;
                }
                tries++;
                Thread.sleep(10);
            }
        } finally {
            assertTrue(tenantRepository.getAllTenantNames().containsAll(expectedTenants));
            tenantRepository.close();
        }
    }

    @Test
    public void testFailingBootstrap() {
        tenantRepository.close(); // stop using the one setup in Before method

        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Could not create all tenants when bootstrapping, failed to create: [default]");
        new FailingDuringBootstrapTenantRepository(configserverConfig);
    }

    private List<String> readZKChildren(String path) throws Exception {
        return curator.framework().getChildren().forPath(path);
    }

    private void assertZooKeeperTenantPathExists(TenantName tenantName) throws Exception {
        assertNotNull(curator.framework().checkExists().forPath(TenantRepository.getTenantPath(tenantName).getAbsolute()));
    }

    private static class FailingDuringBootstrapTenantRepository extends TenantRepository {

        private static final FlagSource flagSource = new InMemoryFlagSource();

        public FailingDuringBootstrapTenantRepository(ConfigserverConfig configserverConfig) {
            super(new HostRegistry(),
                  new MockCurator(),
                  Metrics.createTestMetrics(),
                  new StripedExecutor<>(new InThreadExecutorService()),
                  new StripedExecutor<>(new InThreadExecutorService()),
                  new FileDistributionFactory(configserverConfig, new FileDirectory(configserverConfig)),
                  flagSource,
                  new InThreadExecutorService(),
                  new MockSecretStore(),
                  HostProvisionerProvider.empty(),
                  configserverConfig,
                  new ConfigServerDB(configserverConfig),
                  Zone.defaultZone(),
                  Clock.systemUTC(),
                  new ModelFactoryRegistry(List.of(VespaModelFactory.createTestFactory())),
                  new TestConfigDefinitionRepo(),
                  new TenantApplicationsTest.MockConfigActivationListener(),
                  new MockTenantListener(),
                  new ZookeeperServerConfig.Builder().myid(0).build());
        }

        @Override
        public void bootstrapTenant(TenantName tenantName) {
            throw new RuntimeException("Failed to create: " + tenantName);
        }
    }

}
