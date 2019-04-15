package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class RolesTest {

    private static final Roles roles = new Roles();

    @Test
    public void testSerialization() {
        TenantName tenant = TenantName.from("my-tenant");
        for (TenantRole role : roles.tenantRoles(tenant))
            assertEquals(role, roles.toRole(Roles.valueOf(role)));

        ApplicationName application = ApplicationName.from("my-application");
        for (ApplicationRole role : roles.applicationRoles(tenant, application))
            assertEquals(role, roles.toRole(Roles.valueOf(role)));

        assertEquals(Role.tenantOperator(tenant),
                     roles.toRole("my-tenant.tenantOperator"));
        assertEquals(Role.applicationReader(tenant, application),
                     roles.toRole("my-tenant.my-application.applicationReader"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalTenantName() {
        Roles.valueOf(Role.tenantAdmin(TenantName.from("my.tenant")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalApplicationName() {
        Roles.valueOf(Role.applicationOperator(TenantName.from("my-tenant"), ApplicationName.from("my.app")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalRole() {
        Roles.valueOf(Role.tenantPipeline(TenantName.from("my-tenant"), ApplicationName.from("my-app")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalRoleValue() {
        roles.toRole("my-tenant.awesomePerson");
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalCombination() {
        roles.toRole("my-tenant.my-application.tenantOwner");
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalValue() {
        roles.toRole("everyone");
    }

    @Test
    public void allowHostedOperator() {
        assertEquals(Role.hostedOperator(), roles.toRole("hostedOperator"));
    }

}
