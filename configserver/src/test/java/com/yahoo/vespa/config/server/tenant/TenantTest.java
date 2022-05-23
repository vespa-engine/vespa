// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.TenantName;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author Ulf Lilleengen
 */
public class TenantTest {

    private Tenant t1;
    private Tenant t2;
    private Tenant t3;
    private Tenant t4;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setupTenant() throws IOException {
        t1 = createTenant("foo");
        t2 = createTenant("foo");
        t3 = createTenant("bar");
        t4 = createTenant("baz");
    }

    private Tenant createTenant(String name) throws IOException {
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        TenantRepository tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .build();
        TenantName tenantName = TenantName.from(name);
        tenantRepository.addTenant(tenantName);
        return tenantRepository.getTenant(tenantName);
    }

    @Test
    public void equals() {
        assertEquals(t1, t2);
        assertNotEquals(t1, t3);
        assertNotEquals(t1, t4);
        assertNotEquals(t3, t4);
    }

    @Test
    public void hashcode() {
        assertEquals(t1.hashCode(), t2.hashCode());
        assertNotEquals(t1.hashCode(), t3.hashCode());
        assertNotEquals(t1.hashCode(), t4.hashCode());
    }

}
