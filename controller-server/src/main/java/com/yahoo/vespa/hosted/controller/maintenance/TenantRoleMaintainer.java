// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TenantRoleMaintainer extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(TenantRoleMaintainer.class.getName());

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

        double ok = 0, attempts = 0, total = 0;
        // Create separate athenz service for all tenants
        for (Tenant tenant : tenants) {
            ++attempts;
            try {
                roleService.createTenantRole(tenant);
            }
            catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to create role for " + tenant.name() + ": " + Exceptions.toMessageString(e));
            }
            ++ok;
        }
        total += attempts == 0 ? 1 : ok / attempts;

        total += roleService.maintainRoles(tenants.stream().map(Tenant::name).toList());

        // Update last maintained timestamp
        var updated = controller().clock().instant();
        for (Tenant tenant : tenants) controller().tenants().updateLastTenantRolesMaintained(tenant.name(), updated);

        return total * 0.5;
    }


}
