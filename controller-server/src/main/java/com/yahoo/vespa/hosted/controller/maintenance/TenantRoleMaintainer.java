// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
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
        var tenantsWithRoles = tenants.stream()
                .map(Tenant::name)
                .filter(this::hasProductionDeployment)
                .collect(Collectors.toList());
        roleService.maintainRoles(tenantsWithRoles);
        return 1.0;
    }

    private boolean hasProductionDeployment(TenantName tenant) {
        return controller().applications().asList(tenant).stream()
                .map(Application::productionInstances)
                .anyMatch(Predicate.not(Map::isEmpty));
    }
}
