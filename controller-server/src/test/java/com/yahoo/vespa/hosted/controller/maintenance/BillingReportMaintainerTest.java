// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.billing.InvoiceUpdate;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistryMock;
import com.yahoo.vespa.hosted.controller.tenant.BillingReference;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BillingReportMaintainerTest {
    private final ControllerTester tester = new ControllerTester(SystemName.PublicCd);
    private final BillingReportMaintainer maintainer = new BillingReportMaintainer(tester.controller(), Duration.ofMinutes(10));

    @Test
    void only_billable_tenants_are_maintained() {
        var t1 = tester.createTenant("t1");
        var t2  = tester.createTenant("t2");

        tester.controller().serviceRegistry().billingController().setPlan(t1, PlanRegistryMock.paidPlan.id(), false, true);
        maintainer.maintain();

        var b1 = billingReference(t1);
        var b2 = billingReference(t2);

        assertFalse(b1.isEmpty());
        assertTrue(b2.isEmpty());

        assertEquals(tester.clock().instant(), b1.orElseThrow().updated());
        assertNotNull(b1.orElseThrow().reference());
    }

    @Test
    void only_bills_with_exported_id_are_maintained() {
        var t1 = tester.createTenant("t1");
        var billingController = tester.controller().serviceRegistry().billingController();
        var billingDb = tester.controller().serviceRegistry().billingDatabase();

        var start = LocalDate.of(2020, 5, 23).atStartOfDay(ZoneOffset.UTC);
        var end = start.toLocalDate().plusDays(6).atStartOfDay(ZoneOffset.UTC);
        var bill1 = billingDb.createBill(t1, start, end, "non-exported");
        var bill2 = billingDb.createBill(t1, start, end, "exported");

        billingController.setPlan(t1, PlanRegistryMock.paidPlan.id(), false, true);

        tester.controller().serviceRegistry().billingReporter().exportBill(billingDb.readBill(bill2).get(), "FOO", cloudTenant(t1));
        var updates = maintainer.maintainInvoices();
        assertEquals(new InvoiceUpdate(0, 0, 1), updates);

        var exportedBill = billingDb.readBill(bill2).get();
        assertEquals("EXT-ID-123", exportedBill.getExportedId().get());
        assertTrue(billingDb.readBill(bill1).get().getExportedId().isEmpty());
    }

    private CloudTenant cloudTenant(TenantName tenantName) {
        return tester.controller().tenants().require(tenantName, CloudTenant.class);
    }

    private Optional<BillingReference> billingReference(TenantName tenantName) {
        return cloudTenant(tenantName).billingReference();
    }

}
