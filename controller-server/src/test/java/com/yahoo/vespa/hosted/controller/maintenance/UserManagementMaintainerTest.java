package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockUserManagement;
import com.yahoo.vespa.hosted.controller.api.integration.user.Roles;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.*;

/**
 * @author olaa
 */
public class UserManagementMaintainerTest {

    private final ControllerTester tester = new ControllerTester();
    private final UserManagement userManagement = new MockUserManagement();
    private final UserManagementMaintainer userManagementMaintainer = new UserManagementMaintainer(tester.controller(), Duration.ofMinutes(1), userManagement);

    private final TenantName tenant = TenantName.from("tenant1");
    private final ApplicationName app = ApplicationName.from("app1");
    private final TenantName deletedTenant = TenantName.from("deleted-tenant");

    @Test
    public void finds_superfluous_roles() {
        tester.createTenant(tenant.value());
        tester.createApplication(tenant.value(), app.value());

        Roles.tenantRoles(tenant).forEach(userManagement::createRole);
        Roles.applicationRoles(tenant, app).forEach(userManagement::createRole);
        Roles.tenantRoles(deletedTenant).forEach(userManagement::createRole);
        userManagement.createRole(Role.hostedSupporter());

        var expectedRoles = Roles.tenantRoles(deletedTenant);
        var actualRoles = userManagementMaintainer.findLeftoverRoles();

        assertEquals(expectedRoles.size(), actualRoles.size());
        assertTrue(expectedRoles.containsAll(actualRoles) && actualRoles.containsAll(expectedRoles));
    }

}