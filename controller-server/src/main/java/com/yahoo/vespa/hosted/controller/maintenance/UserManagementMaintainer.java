// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.user.Roles;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Maintains user management resources.
 * For now, ensures there's no discrepnacy between expected tenant/application roles and Auth0 roles
 *
 * @author olaa
 */
public class UserManagementMaintainer extends ControllerMaintainer {

    private final UserManagement userManagement;

    private static final Logger logger = Logger.getLogger(UserManagementMaintainer.class.getName());

    public UserManagementMaintainer(Controller controller, Duration interval, UserManagement userManagement) {
        super(controller, interval, UserManagementMaintainer.class.getSimpleName(), SystemName.allOf(SystemName::isPublic));
        this.userManagement = userManagement;

    }

    @Override
    protected double maintain() {
        findLeftoverRoles().forEach(role -> {
            /*
                Log discrepancy now
                TODO: userManagement.deleteRole(role);
             */
            logger.warning(String.format("Found unexpected role %s - Please investigate", role.toString()));
        });
        return 1.0;
    }

    // protected for testing
    protected List<Role> findLeftoverRoles() {
        var tenantRoles = controller().tenants().asList()
                .stream()
                .flatMap(tenant -> Roles.tenantRoles(tenant.name()).stream())
                .collect(Collectors.toList());

        var applicationRoles = controller().applications().asList()
                .stream()
                .map(Application::id)
                .flatMap(applicationId -> Roles.applicationRoles(applicationId.tenant(), applicationId.application()).stream())
                .collect(Collectors.toList());

        return userManagement.listRoles().stream()
                .filter(role -> role instanceof TenantRole || role instanceof ApplicationRole)
                .filter(role -> !tenantRoles.contains(role) && !applicationRoles.contains(role))
                .collect(Collectors.toList());
    }

}
