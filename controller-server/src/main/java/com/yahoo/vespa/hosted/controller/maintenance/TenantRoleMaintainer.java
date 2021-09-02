// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
        tenants.stream()
                .map(Tenant::name)
                .forEach(roleService::createTenantRole);

        // Until we have moved to separate athenz service per tenant, make sure we update the shared policy
        // to allow ssh logins for hosts in prod/perf with a separate tenant iam role.
        var tenantsWithRoles = tenants.stream()
                .map(Tenant::name)
                .filter(tenant -> hasProductionDeployment(tenant) || hasPerfDeployment(tenant))
                .collect(Collectors.toList());
        roleService.maintainRoles(tenantsWithRoles);
        return 1.0;
    }

    private boolean hasProductionDeployment(TenantName tenant) {
        return controller().applications().asList(tenant).stream()
                .map(Application::productionInstances)
                .anyMatch(Predicate.not(Map::isEmpty));
    }

    private boolean hasPerfDeployment(TenantName tenant) {
        List<ZoneId> perfZones = controller().zoneRegistry().zones().controllerUpgraded().in(Environment.perf).ids();
        return controller().applications().asList(tenant).stream()
                .map(Application::instances)
                .flatMap(instances -> instances.values().stream())
                .flatMap(instance -> instance.deployments().values().stream())
                .anyMatch(x -> perfZones.contains(x.zone()));
    }
}
