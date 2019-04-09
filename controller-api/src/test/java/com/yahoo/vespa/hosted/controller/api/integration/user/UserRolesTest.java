package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.Roles;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class UserRolesTest {

    private static final Roles roles = new Roles(SystemName.main);
    private static final UserRoles userRoles = new UserRoles(roles);

    @Test
    public void testSerialization() {
        TenantName tenant = TenantName.from("my-tenant");
        for (TenantRole role : userRoles.tenantRoles(tenant))
            assertEquals(role, userRoles.toRole(UserRoles.valueOf(role)));

        ApplicationName application = ApplicationName.from("my-application");
        for (ApplicationRole role : userRoles.applicationRoles(tenant, application))
            assertEquals(role, userRoles.toRole(UserRoles.valueOf(role)));

        assertEquals(roles.tenantOperator(tenant),
                     userRoles.toRole("my-tenant.tenantOperator"));
        assertEquals(roles.applicationReader(tenant, application),
                     userRoles.toRole("my-tenant.my-application.applicationReader"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalTenantName() {
        UserRoles.valueOf(roles.tenantAdmin(TenantName.from("my.tenant")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalApplicationName() {
        UserRoles.valueOf(roles.applicationOperator(TenantName.from("my-tenant"), ApplicationName.from("my.app")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalRole() {
        UserRoles.valueOf(roles.tenantPipeline(TenantName.from("my-tenant"), ApplicationName.from("my-app")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalRoleValue() {
        userRoles.toRole("my-tenant.awesomePerson");
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalCombination() {
        userRoles.toRole("my-tenant.my-application.tenantOwner");
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalValue() {
        userRoles.toRole("everyone");
    }

    @Test
    public void allowHostedOperator() {
        assertEquals(roles.hostedOperator(), userRoles.toRole("hostedOperator"));
    }

}
