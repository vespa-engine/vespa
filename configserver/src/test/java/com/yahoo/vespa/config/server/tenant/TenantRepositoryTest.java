// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.component.Version;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.model.VespaModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.IOException;
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
    private TestComponentRegistry globalComponentRegistry;
    private TenantRequestHandlerTest.MockReloadListener listener;
    private MockTenantListener tenantListener;
    private Curator curator;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setupSessions() {
        curator = new MockCurator();
        globalComponentRegistry = new TestComponentRegistry.Builder().curator(curator).build();
        listener = (TenantRequestHandlerTest.MockReloadListener)globalComponentRegistry.getReloadListener();
        tenantListener = (MockTenantListener)globalComponentRegistry.getTenantListener();
        assertFalse(tenantListener.tenantsLoaded);
        tenantRepository = new TenantRepository(globalComponentRegistry);
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
        tenantRepository.getTenant(tenant1).getReloadHandler().reloadConfig(ApplicationSet.fromSingle(
                new Application(new VespaModel(MockApplicationPackage.createEmpty()),
                                new ServerCache(),
                                4L,
                                false,
                                new Version(1, 2, 3),
                                MetricUpdater.createTestUpdater(),
                                ApplicationId.defaultId())));
        assertEquals(1, listener.reloaded.get());
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
    public void testFailingBootstrap() throws IOException {
        tenantRepository.close(); // stop using the one setup in Before method

        // No exception if config is false
        boolean throwIfBootstrappingTenantRepoFails = false;
        new FailingDuringBootstrapTenantRepository(createComponentRegistry(throwIfBootstrappingTenantRepoFails));

        // Should get exception if config is true
        throwIfBootstrappingTenantRepoFails = true;
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Could not create all tenants when bootstrapping, failed to create: [default]");
        new FailingDuringBootstrapTenantRepository(createComponentRegistry(throwIfBootstrappingTenantRepoFails));
    }

    private List<String> readZKChildren(String path) throws Exception {
        return curator.framework().getChildren().forPath(path);
    }

    private void assertZooKeeperTenantPathExists(TenantName tenantName) throws Exception {
        assertNotNull(globalComponentRegistry.getCurator().framework().checkExists().forPath(tenantRepository.tenantZkPath(tenantName)));
    }

    private GlobalComponentRegistry createComponentRegistry(boolean throwIfBootstrappingTenantRepoFails) throws IOException {
        return new TestComponentRegistry.Builder()
                .curator(new MockCurator())
                .configServerConfig(new ConfigserverConfig(new ConfigserverConfig.Builder()
                                                                   .throwIfBootstrappingTenantRepoFails(throwIfBootstrappingTenantRepoFails)
                                                                   .configDefinitionsDir(temporaryFolder.newFolder("configdefs" + throwIfBootstrappingTenantRepoFails).getAbsolutePath())
                                                                   .configServerDBDir(temporaryFolder.newFolder("configserverdb" + throwIfBootstrappingTenantRepoFails).getAbsolutePath())))
                .zone(new Zone(SystemName.cd, Environment.prod, RegionName.from("foo")))
                .build();
    }

    private static class FailingDuringBootstrapTenantRepository extends TenantRepository {

        public FailingDuringBootstrapTenantRepository(GlobalComponentRegistry globalComponentRegistry) {
            super(globalComponentRegistry, false);
        }

        @Override
        protected void createTenant(TenantBuilder builder) {
            throw new RuntimeException("Failed to create: " + builder.getTenantName());
        }
    }

}
