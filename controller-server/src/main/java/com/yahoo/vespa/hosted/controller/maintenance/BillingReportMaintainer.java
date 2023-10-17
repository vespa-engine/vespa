// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Bill;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingDatabaseClient;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingReporter;
import com.yahoo.vespa.hosted.controller.api.integration.billing.InvoiceUpdate;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Plan;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistry;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BillingReportMaintainer extends ControllerMaintainer {

    private final BillingReporter reporter;
    private final BillingController billing;
    private final BillingDatabaseClient databaseClient;

    private final PlanRegistry plans;

    public BillingReportMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, Set.of(SystemName.PublicCd));
        reporter = controller.serviceRegistry().billingReporter();
        billing = controller.serviceRegistry().billingController();
        databaseClient = controller.serviceRegistry().billingDatabase();
        plans = controller.serviceRegistry().planRegistry();
    }

    @Override
    protected double maintain() {
        maintainTenants();

        var updates = maintainInvoices();
        log.fine("Updated invoices: " + updates);

        return 0.0;
    }

    private void maintainTenants() {
        var tenants = cloudTenants();
        var tenantNames = List.copyOf(tenants.keySet());
        var billableTenants = billableTenants(tenantNames);

        billableTenants.forEach(tenant -> {
            controller().tenants().lockIfPresent(tenant, LockedTenant.Cloud.class, locked -> {
                var ref = reporter.maintainTenant(locked.get());
                if (locked.get().billingReference().isEmpty() || ! locked.get().billingReference().get().equals(ref)) {
                    controller().tenants().store(locked.with(ref));
                }
            });
        });
    }

    InvoiceUpdate maintainInvoices() {
        var billsNeedingMaintenance = databaseClient.readBills().stream()
                .filter(bill -> bill.getExportedId().isPresent())
                .filter(exported -> ! exported.status().equals("ISSUED")) // TODO: This status does not yet exist.
                .toList();

        var updates = new InvoiceUpdate.Counter();
        for (var bill : billsNeedingMaintenance) {
            updates.add(reporter.maintainInvoice(bill));
        }
        return updates.finish();
    }

    private Map<TenantName, CloudTenant> cloudTenants() {
        return controller().tenants().asList()
                .stream()
                .filter(CloudTenant.class::isInstance)
                .map(CloudTenant.class::cast)
                .collect(Collectors.toMap(
                        Tenant::name,
                        Function.identity()));
    }

    private List<Plan> billablePlans() {
        return plans.all().stream()
                .filter(Plan::isBilled)
                .toList();
    }

    private List<TenantName> billableTenants(List<TenantName> tenants) {
        return billablePlans().stream()
                .flatMap(p -> billing.tenantsWithPlan(tenants, p.id()).stream())
                .toList();
    }

}
