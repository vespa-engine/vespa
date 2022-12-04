// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TenantRoleMaintainer extends ControllerMaintainer {

    public TenantRoleMaintainer(Controller controller, Duration tenantRoleMaintainer) {
        super(controller, tenantRoleMaintainer);
    }

    @Override
    protected double maintain() {
        var roleService = controller().serviceRegistry().roleService();
        var tenants = controller().tenants().asList();

        // Create separate athenz service for all tenants
        tenants.forEach(roleService::createTenantRole);

        var tenantsWithRoles = tenants.stream()
                .map(Tenant::name)
                .collect(Collectors.toList());
        roleService.maintainRoles(tenantsWithRoles);

        var deletedTenants = controller().tenants().asList(true).stream()
                .filter(tenant -> tenant.type() == Tenant.Type.deleted)
                .map(Tenant::name)
                .toList();
        roleService.cleanupRoles(deletedTenants);

        return 1.0;
    }


}
