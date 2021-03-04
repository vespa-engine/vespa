// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.util.stream.Collectors;

public class TenantRoleMaintainer extends ControllerMaintainer {

    private final BooleanFlag provisionTenantRoles;

    public TenantRoleMaintainer(Controller controller, Duration tenantRoleMaintainer) {
        super(controller, tenantRoleMaintainer);
        provisionTenantRoles = Flags.PROVISION_TENANT_ROLES.bindTo(controller.flagSource());
    }

    @Override
    protected boolean maintain() {
        var roleService = controller().serviceRegistry().roleService();
        var tenants = controller().tenants().asList();
        var tenantsWithRoles = tenants.stream()
                .map(Tenant::name)
                // Only maintain a subset of the tenants
                .filter(name -> provisionTenantRoles.with(FetchVector.Dimension.TENANT_ID, name.value()).value())
                .collect(Collectors.toList());
        roleService.maintainRoles(tenantsWithRoles);
        return true;
    }
}
