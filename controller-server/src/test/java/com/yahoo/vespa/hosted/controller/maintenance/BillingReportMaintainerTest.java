// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Bill;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillStatus;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingDatabaseClient;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingReporterMock;
import com.yahoo.vespa.hosted.controller.api.integration.billing.FailedInvoiceUpdate;
import com.yahoo.vespa.hosted.controller.api.integration.billing.ModifiableInvoiceUpdate;
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
    private final BillingDatabaseClient billingDb = tester.controller().serviceRegistry().billingDatabase();
    private final BillingReporterMock reporter = (BillingReporterMock) tester.controller().serviceRegistry().billingReporter();

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
    void only_open_bills_with_exported_id_are_maintained() {
        var t1 = tester.createTenant("t1");

        var bill1 = createBill(t1, "non-exported", billingDb);
        var bill2 = createBill(t1, "exported", billingDb);
        var bill3 = createBill(t1, "exported-and-frozen", billingDb);
        billingDb.setStatus(bill3, "foo", BillStatus.FROZEN);

        exportBills(t1, bill2, bill3);
        var updates = maintainer.maintainInvoices();

        assertTrue(billingDb.readBill(bill1).get().getExportedId().isEmpty());

        // Only the exported open bill is maintained
        assertEquals(1, updates.size());
        assertEquals(bill2, updates.get(0).billId());
        assertEquals(ModifiableInvoiceUpdate.class, updates.get(0).getClass());
        var exportedBill = billingDb.readBill(bill2).get();
        assertEquals("EXPORTED-" + exportedBill.id().value(), exportedBill.getExportedId().get());
        // Verify that the bill has been updated with a marker line item by the mock
        var lineItems = exportedBill.lineItems();
        assertEquals(1, lineItems.size());
        assertEquals("maintained", lineItems.get(0).id());

        // The frozen bill is untouched by the maintainer
        var frozenBill = billingDb.readBill(bill3).get();
        assertEquals("EXPORTED-" + frozenBill.id().value(), frozenBill.getExportedId().get());
        assertEquals(0, frozenBill.lineItems().size());
    }

    @Test
    void bills_whose_invoice_has_been_deleted_in_the_external_system_are_no_longer_maintained() {
        var t1 = tester.createTenant("t1");
        var bill1 = createBill(t1, "exported-then-deleted", billingDb);
        exportBills(t1, bill1);

        var updates = maintainer.maintainInvoices();
        assertEquals(1, updates.size());
        assertEquals(ModifiableInvoiceUpdate.class, updates.get(0).getClass());

        // Delete invoice from the external system
        reporter.deleteExportedBill(bill1);

        // Maintainer should report that the invoice has been removed
        updates = maintainer.maintainInvoices();
        assertEquals(1, updates.size());
        assertEquals(FailedInvoiceUpdate.class, updates.get(0).getClass());
        assertEquals(FailedInvoiceUpdate.Reason.REMOVED, ((FailedInvoiceUpdate)updates.get(0)).reason);

        // The bill should no longer be maintained
        updates = maintainer.maintainInvoices();
        assertEquals(0, updates.size());
    }

    @Test
    void it_is_allowed_to_re_export_bills_whose_invoice_has_been_deleted_in_the_external_system() {
        var t1 = tester.createTenant("t1");

        var bill1 = createBill(t1, "exported-then-deleted", billingDb);

        // Export the bill, then delete it in the external system
        exportBills(t1, bill1);
        maintainer.maintainInvoices();
        reporter.deleteExportedBill(bill1);
        maintainer.maintainInvoices();

        // Ensure it is currently ignored by the maintainer
        var updates = maintainer.maintainInvoices();
        assertEquals(0, updates.size());

        // Re-export the bill and verify that it is maintained again
        exportBills(t1, bill1);
        updates = maintainer.maintainInvoices();
        assertEquals(1, updates.size());
        assertEquals(ModifiableInvoiceUpdate.class, updates.get(0).getClass());
    }

    private static Bill.Id createBill(TenantName tenantName, String agent, BillingDatabaseClient billingDb) {
        var start = LocalDate.of(2020, 5, 23).atStartOfDay(ZoneOffset.UTC);
        var end = start.toLocalDate().plusDays(6).atStartOfDay(ZoneOffset.UTC);
        return billingDb.createBill(tenantName, start, end, agent);
    }

    private void exportBills(TenantName tenantName, Bill.Id... billIds) {
        for (var billId : billIds) {
            var bill = billingDb.readBill(billId).get();
            reporter.exportBill(bill, "FOO", cloudTenant(tenantName));
        }
    }

    private CloudTenant cloudTenant(TenantName tenantName) {
        return tester.controller().tenants().require(tenantName, CloudTenant.class);
    }

    private Optional<BillingReference> billingReference(TenantName tenantName) {
        return cloudTenant(tenantName).billingReference();
    }

}
