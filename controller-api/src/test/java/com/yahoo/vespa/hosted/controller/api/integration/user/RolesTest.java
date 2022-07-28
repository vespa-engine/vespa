// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author jonmv
 */
public class RolesTest {

    @Test
    void testSerialization() {
        TenantName tenant = TenantName.from("my-tenant");
        for (TenantRole role : Roles.tenantRoles(tenant))
            assertEquals(role, Roles.toRole(Roles.valueOf(role)));

        ApplicationName application = ApplicationName.from("my-application");
        for (ApplicationRole role : Roles.applicationRoles(tenant, application))
            assertEquals(role, Roles.toRole(Roles.valueOf(role)));

        assertEquals(Role.hostedOperator(),
                Roles.toRole("hostedOperator"));
        assertEquals(Role.hostedSupporter(),
                Roles.toRole("hostedSupporter"));
        assertEquals(Role.administrator(tenant), Roles.toRole("my-tenant.administrator"));
        assertEquals(Role.developer(tenant), Roles.toRole("my-tenant.developer"));
        assertEquals(Role.reader(tenant), Roles.toRole("my-tenant.reader"));
        assertEquals(Role.headless(tenant, application), Roles.toRole("my-tenant.my-application.headless"));
    }

    @Test
    void illegalTenantName() {
        assertThrows(IllegalArgumentException.class, () -> {
            Roles.valueOf(Role.developer(TenantName.from("my.tenant")));
        });
    }

    @Test
    void illegalApplicationName() {
        assertThrows(IllegalArgumentException.class, () -> {
            Roles.valueOf(Role.headless(TenantName.from("my-tenant"), ApplicationName.from("my.app")));
        });
    }

    @Test
    void illegalRoleValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            Roles.toRole("my-tenant.awesomePerson");
        });
    }

    @Test
    void illegalCombination() {
        assertThrows(IllegalArgumentException.class, () -> {
            Roles.toRole("my-tenant.my-application.tenantOwner");
        });
    }

    @Test
    void illegalValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            Roles.toRole("everyone");
        });
    }

}
