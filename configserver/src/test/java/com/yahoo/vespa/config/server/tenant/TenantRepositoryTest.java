// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
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
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
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

    @Before
    public void setupSessions() {
        curator = new MockCurator();
        globalComponentRegistry = new TestComponentRegistry.Builder().curator(curator).build();
        listener = (TenantRequestHandlerTest.MockReloadListener)globalComponentRegistry.getReloadListener();
        tenantListener = (MockTenantListener)globalComponentRegistry.getTenantListener();
        tenantListener.tenantsLoaded = false;
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
                                Version.fromIntValues(1, 2, 3),
                                MetricUpdater.createTestUpdater(),
                                ApplicationId.defaultId())));
        assertThat(listener.reloaded.get(), is(1));
    }

    private List<String> readZKChildren(String path) throws Exception {
        return curator.framework().getChildren().forPath(path);
    }

    @Test
    public void testTenantListenersNotified() throws Exception {
        tenantRepository.addTenant(tenant3);
        assertThat("tenant3 not the last created tenant. Tenants: " + tenantRepository.getAllTenantNames() +
                        ", /config/v2/tenants: " + readZKChildren("/config/v2/tenants"),
                tenantListener.tenantCreatedName, is(tenant3));
        tenantRepository.deleteTenant(tenant2);
        assertFalse(tenantRepository.getAllTenantNames().contains(tenant2));
        assertThat(tenantListener.tenantDeletedName, is(tenant2));
    }

    @Test
    public void testAddTenant() {
        Set<TenantName> allTenants = tenantRepository.getAllTenantNames();
        assertTrue(allTenants.contains(tenant1));
        assertTrue(allTenants.contains(tenant2));
        tenantRepository.addTenant(tenant3);
        allTenants = tenantRepository.getAllTenantNames();
        assertTrue(allTenants.contains(tenant1));
        assertTrue(allTenants.contains(tenant2));
        assertTrue(allTenants.contains(tenant3));
    }

    @Test
    public void testPutAdd() throws Exception {
        tenantRepository.addTenant(tenant3);
        assertNotNull(globalComponentRegistry.getCurator().framework().checkExists().forPath(tenantRepository.tenantZkPath(tenant3)));
    }
    
    @Test
    public void testDelete() throws Exception {
        assertNotNull(globalComponentRegistry.getCurator().framework().checkExists().forPath(tenantRepository.tenantZkPath(tenant1)));
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
    public void testDeleteOfDefaultTenant()  {
        try {
            assertNotNull(globalComponentRegistry.getCurator().framework().checkExists().forPath(tenantRepository.tenantZkPath(TenantName.defaultName())));
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

}
