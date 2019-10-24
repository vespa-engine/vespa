// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class ApplicationIdSnapshotTest {
    private static final TenantName tenant1 = TenantName.from("tenant1");
    private static final TenantName tenant2 = TenantName.from("tenant2");
    private static final TenantName tenant3 = TenantName.from("tenant3");
    private static final TenantName tenant4 = TenantName.from("tenant4");
    private static final ApplicationName app1 = ApplicationName.from("app1");
    private static final ApplicationName app2 = ApplicationName.from("app2");
    private static final ApplicationName app3 = ApplicationName.from("app3");
    private static final InstanceName instance1 = InstanceName.defaultName();
    private static final InstanceName instance2 = InstanceName.from("instance2");
    private static final InstanceName instance3 = InstanceName.from("instance3");

    @Test
    public void basic() {
        ApplicationIdSnapshot snapshot = new ApplicationIdSnapshot.Builder()
                .add(tenant1, app1, instance1)
                .add(tenant1, app2, instance1)
                .add(tenant1, app3)
                .add(tenant1, app2, instance2)
                .add(tenant2, app2, instance3)
                .add(tenant3, app1)
                .add(tenant4)
                .build();

        assertEquals(Set.of(tenant1, tenant2, tenant3, tenant4), snapshot.tenants());

        assertEquals(Set.of(app1, app2, app3), snapshot.applications(tenant1));
        assertEquals(Set.of(app2), snapshot.applications(tenant2));
        assertEquals(Set.of(), snapshot.applications(tenant4));

        assertEquals(Set.of(instance1), snapshot.instances(tenant1, app1));
        assertEquals(Set.of(instance1, instance2), snapshot.instances(tenant1, app2));
        assertEquals(Set.of(), snapshot.instances(tenant3, app1));
    }

    @Test
    public void test_missing() {
        ApplicationIdSnapshot snapshot = new ApplicationIdSnapshot.Builder().build();

        assertEquals(Set.of(), snapshot.tenants());
        assertEquals(Set.of(), snapshot.applications(tenant1));
        assertEquals(Set.of(), snapshot.instances(tenant1, app1));
    }
}