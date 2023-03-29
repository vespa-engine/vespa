// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

public class TenantRoleMaintainer extends ControllerMaintainer {

    public TenantRoleMaintainer(Controller controller, Duration tenantRoleMaintainer) {
        super(controller, tenantRoleMaintainer);
    }

    @Override
    protected double maintain() {
        var roleService = controller().serviceRegistry().roleService();
        var tenants = controller().tenants().asList().stream()
                .sorted(Comparator.comparing(Tenant::tenantRolesLastMaintained))
                .limit(5)
                .toList();

        // Create separate athenz service for all tenants
        tenants.forEach(roleService::createTenantRole);

        var tenantsWithRoles = tenants.stream()
                .map(Tenant::name)
                .toList();
        roleService.maintainRoles(tenantsWithRoles);

        // Update last maintained timestamp
        var updated = Instant.now(controller().clock());
        tenants.forEach(t -> {
            controller().tenants().updateLastTenantRolesMaintained(t.name(), updated);
        });

        return 0.0;
    }


}
