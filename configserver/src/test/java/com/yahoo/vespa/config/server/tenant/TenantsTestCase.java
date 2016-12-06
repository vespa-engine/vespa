// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.TestWithCurator;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.deploy.MockDeployer;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.model.VespaModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TenantsTestCase extends TestWithCurator {
    private Tenants tenants;
    TestComponentRegistry globalComponentRegistry;
    private TenantRequestHandlerTest.MockReloadListener listener;
    private MockTenantListener tenantListener;
    private final TenantName tenant1 = TenantName.from("tenant1");
    private final TenantName tenant2 = TenantName.from("tenant2");
    private final TenantName tenant3 = TenantName.from("tenant3");

    @Before
    public void setupSessions() throws Exception {
        globalComponentRegistry = new TestComponentRegistry.Builder().curator(curator).build();
        listener = (TenantRequestHandlerTest.MockReloadListener)globalComponentRegistry.getReloadListener();
        tenantListener = (MockTenantListener)globalComponentRegistry.getTenantListener();
        tenantListener.tenantsLoaded = false;
        tenants = new Tenants(globalComponentRegistry, Metrics.createTestMetrics());
        assertTrue(tenantListener.tenantsLoaded);
        tenants.writeTenantPath(tenant1);
        tenants.writeTenantPath(tenant2);
    }

    @After
    public void closeSessions() throws IOException {
        tenants.close();
    }

    @Test
    public void testStartUp() {
        assertEquals(tenants.tenantsCopy().get(tenant1).getName(), tenant1);
        assertEquals(tenants.tenantsCopy().get(tenant2).getName(), tenant2);
    }

    @Test
    public void testListenersAdded() throws IOException, SAXException {
        tenants.tenantsCopy().get(tenant1).getReloadHandler().reloadConfig(ApplicationSet.fromSingle(new Application(new VespaModel(MockApplicationPackage.createEmpty()), new ServerCache(), 4l, Version.fromIntValues(1, 2, 3), MetricUpdater.createTestUpdater(), ApplicationId.defaultId())));
        assertThat(listener.reloaded.get(), is(1));
    }

    private List<String> readZKChildren(String path) throws Exception {
        return curator.framework().getChildren().forPath(path);
    }

    @Test
    public void testTenantListenersNotified() throws Exception {
        tenants.writeTenantPath(tenant3);
        assertThat("tenant3 not the last created tenant. Tenants: " + tenants.tenantsCopy().keySet() + ", /config/v2/tenants: " + readZKChildren("/config/v2/tenants"), tenantListener.tenantCreatedName, is(tenant3));
        tenants.deleteTenant(tenant2);
        assertFalse(tenants.tenantsCopy().containsKey(tenant2));
        assertThat(tenantListener.tenantDeletedName, is(tenant2));
    }

    @Test
    public void testAddTenant() throws Exception {
        Map<TenantName, Tenant> tenantsCopy = tenants.tenantsCopy();
        assertEquals(tenantsCopy.get(tenant1).getName(), tenant1);
        assertEquals(tenantsCopy.get(tenant2).getName(), tenant2);
        tenants.writeTenantPath(tenant3);
        tenantsCopy = tenants.tenantsCopy();
        assertEquals(tenantsCopy.get(tenant1).getName(), tenant1);
        assertEquals(tenantsCopy.get(tenant2).getName(), tenant2);
        assertEquals(tenantsCopy.get(tenant3).getName(), tenant3);
    }

    @Test
    public void testPutAdd() throws Exception {
        tenants.writeTenantPath(tenant3);
        assertNotNull(globalComponentRegistry.getCurator().framework().checkExists().forPath(tenants.tenantZkPath(tenant3)));
    }
    
    @Test
    public void testRemove() throws Exception {
        assertNotNull(globalComponentRegistry.getCurator().framework().checkExists().forPath(tenants.tenantZkPath(tenant1)));
        tenants.deleteTenant(tenant1);
        assertFalse(tenants.tenantsCopy().containsKey(tenant1));
    }
    
    @Test
    public void testTenantsChanged() throws Exception {
        tenants.close(); // close the Tenants instance created in setupSession, we do not want to use one with a PatchChildrenCache listener
        tenants = new Tenants(globalComponentRegistry, Metrics.createTestMetrics(), new ArrayList<>());
        Set<TenantName> newTenants = new LinkedHashSet<>();
        TenantName defaultTenant = TenantName.defaultName();
        newTenants.add(tenant2);
        newTenants.add(defaultTenant);
        tenants.tenantsChanged(newTenants);
        Map<TenantName, Tenant> tenantsCopy = tenants.tenantsCopy();
        assertEquals(tenantsCopy.get(tenant2).getName(), tenant2);
        assertEquals(tenantsCopy.get(defaultTenant).getName().value(), "default");
        assertNull(tenantsCopy.get(tenant1));
        newTenants.clear();
        tenants.tenantsChanged(newTenants);
        tenantsCopy = tenants.tenantsCopy();
        assertNull(tenantsCopy.get(tenant1));
        assertNull(tenantsCopy.get(tenant2));
        assertNull(tenantsCopy.get(defaultTenant));
        newTenants.clear();
        TenantName foo = TenantName.from("foo");
        TenantName bar = TenantName.from("bar");
        newTenants.add(tenant2);
        newTenants.add(foo);
        newTenants.add(bar);
        tenants.tenantsChanged(newTenants);
        tenantsCopy = tenants.tenantsCopy();
        assertNotNull(tenantsCopy.get(tenant2));
        assertNotNull(tenantsCopy.get(foo));
        assertNotNull(tenantsCopy.get(bar));
        assertEquals(tenantsCopy.get(tenant2).getName(), tenant2);
        assertEquals(tenantsCopy.get(foo).getName(), foo);
        assertEquals(tenantsCopy.get(bar).getName(), bar);
    }
    
    @Test
    public void testTenantWatching() throws Exception {
        TestComponentRegistry reg = new TestComponentRegistry.Builder().curator(curator).build();
        Tenants t = new Tenants(reg, Metrics.createTestMetrics());
        try {
            assertEquals(t.tenantsCopy().get(TenantName.defaultName()).getName(), TenantName.defaultName());
            reg.getCurator().framework().create().forPath(tenants.tenantZkPath(TenantName.from("newTenant")));
            // Poll for the watcher to pick up the tenant from zk, and add it
            int tries=0;
            while(true) {
                if (tries > 500) fail("Didn't react on watch");
                Tenant nt = t.tenantsCopy().get(TenantName.from("newTenant"));
                if (nt != null) {
                    assertEquals(nt.getName().value(), "newTenant");
                    return;
                }
                tries++;
                Thread.sleep(100);
            }
        } finally {
            t.close();
        }
    }

    @Test
    public void testTenantRedeployment() throws Exception {
        MockDeployer deployer = new MockDeployer();
        Tenant tenant = tenants.tenantsCopy().get(tenant1);
        ApplicationId id = ApplicationId.from(tenant1, ApplicationName.defaultName(), InstanceName.defaultName());
        tenant.getApplicationRepo().createPutApplicationTransaction(id, 3).commit();
        tenants.redeployApplications(deployer);
        assertThat(deployer.lastDeployed, is(id));
    }

}
