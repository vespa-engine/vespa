package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.Roles;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class GroupIdTest {

    @Test
    public void testSerialization() {
        Roles roles = new Roles(SystemName.main);

        TenantName tenant = TenantName.from("my-tenant");
        for (TenantRole role : List.of(roles.tenantOwner(tenant),
                                       roles.tenantAdmin(tenant),
                                       roles.tenantOperator(tenant)))
            assertEquals(role, GroupId.fromRole(role).toRole(roles));

        ApplicationName application = ApplicationName.from("my-application");
        for (ApplicationRole role : List.of(roles.applicationOwner(tenant, application),
                                            roles.applicationAdmin(tenant, application),
                                            roles.applicationOperator(tenant, application),
                                            roles.applicationDeveloper(tenant, application),
                                            roles.applicationReader(tenant, application)))
            assertEquals(role, GroupId.fromRole(role).toRole(roles));

        assertEquals(roles.tenantOperator(tenant),
                     GroupId.fromValue("my-tenant.tenantOperator").toRole(roles));
        assertEquals(roles.applicationReader(tenant, application),
                     GroupId.fromValue("my-tenant.my-application.applicationReader").toRole(roles));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalTenantName() {
        GroupId.fromRole(new Roles(SystemName.main).tenantAdmin(TenantName.from("my.tenant")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalApplicationName() {
        GroupId.fromRole(new Roles(SystemName.main).applicationOperator(TenantName.from("my-tenant"), ApplicationName.from("my.app")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalRole() {
        GroupId.fromRole(new Roles(SystemName.main).tenantPipeline(TenantName.from("my-tenant"), ApplicationName.from("my-app")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalRoleValue() {
        GroupId.fromValue("my-tenant.awesomePerson").toRole(new Roles(SystemName.cd));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalCombination() {
        GroupId.fromValue("my-tenant.my-application.tenantOwner").toRole(new Roles(SystemName.cd));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalValue() {
        GroupId.fromValue("hostedOperator").toRole(new Roles(SystemName.Public));
    }

}
