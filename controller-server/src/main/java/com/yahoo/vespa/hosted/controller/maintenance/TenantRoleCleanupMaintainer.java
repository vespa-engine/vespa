// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;

public class TenantRoleCleanupMaintainer extends ControllerMaintainer {

    public TenantRoleCleanupMaintainer(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        var roleService = controller().serviceRegistry().roleService();

        var deletedTenants = controller().tenants().asList(true).stream()
                .filter(tenant -> tenant.type() == Tenant.Type.deleted)
                .map(Tenant::name)
                .toList();
        roleService.cleanupRoles(deletedTenants);

        if (controller().system().isPublic()) {
            controller().serviceRegistry().tenantSecretService().cleanupSecretStores(deletedTenants);
        }

        return 0.0;
    }
}
