// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Bill;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillStatus;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingDatabaseClient;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingReporterMock;
import com.yahoo.vespa.hosted.controller.api.integration.billing.InvoiceUpdate;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistryMock;
import com.yahoo.vespa.hosted.controller.tenant.BillingReference;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    void only_non_final_bills_with_exported_id_are_maintained() {
        var t1 = tester.createTenant("t1");

        var bill1 = createBill(t1, "non-exported", billingDb);
        var bill2 = createBill(t1, "exported-and-modified", billingDb);
        var bill3 = createBill(t1, "exported-and-frozen", billingDb);
        var bill4 = createBill(t1, "exported-and-successful", billingDb);
        var bill5 = createBill(t1, "exported-and-void", billingDb);
        billingDb.setStatus(bill3, "foo", BillStatus.FROZEN);
        billingDb.setStatus(bill4, "foo", BillStatus.SUCCESSFUL);
        billingDb.setStatus(bill5, "foo", BillStatus.VOID);

        exportBills(t1, bill2, bill3);
        reporter.modifyInvoice(bill2);
        var updates = toMap(maintainer.maintainInvoices());

        assertTrue(billingDb.readBill(bill1).get().getExportedId().isEmpty());

        // Only the exported non-final bills are maintained
        assertEquals(2, updates.size());
        assertEquals(Set.of(bill2, bill3), updates.keySet());

        var bill2Update = updates.get(bill2);
        assertEquals(InvoiceUpdate.Type.MODIFIED, bill2Update.type());
        var exportedBill = billingDb.readBill(bill2).get();
        assertEquals("EXPORTED-" + exportedBill.id().value(), exportedBill.getExportedId().get());
        // Verify that the bill has been updated with a marker line item by the mock
        var lineItems = exportedBill.lineItems();
        assertEquals(1, lineItems.size());
        assertEquals("maintained", lineItems.get(0).id());

        // Verify that the frozen bill is unmodified and has not changed state.
        var bill3Update = updates.get(bill3);
        assertEquals(InvoiceUpdate.Type.UNMODIFIED, bill3Update.type());
        var frozenBill = billingDb.readBill(bill3).get();
        assertEquals(BillStatus.FROZEN, frozenBill.status());
    }

    @Test
    void bills_whose_invoice_has_been_deleted_in_the_external_system_are_no_longer_maintained() {
        var t1 = tester.createTenant("t1");
        var bill1 = createBill(t1, "exported-then-deleted", billingDb);
        exportBills(t1, bill1);

        var updates = maintainer.maintainInvoices();
        assertEquals(1, updates.size());
        assertEquals(InvoiceUpdate.Type.UNMODIFIED, updates.get(0).type());

        // Delete invoice from the external system
        reporter.deleteInvoice(bill1);

        // Maintainer should report that the invoice has been removed
        updates = maintainer.maintainInvoices();
        assertEquals(1, updates.size());
        assertEquals(InvoiceUpdate.Type.REMOVED, updates.get(0).type());

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
        reporter.deleteInvoice(bill1);
        maintainer.maintainInvoices();

        // Ensure it is currently ignored by the maintainer
        var updates = maintainer.maintainInvoices();
        assertEquals(0, updates.size());

        // Re-export the bill and verify that it is maintained again
        exportBills(t1, bill1);
        updates = maintainer.maintainInvoices();
        assertEquals(1, updates.size());
        assertEquals(InvoiceUpdate.Type.UNMODIFIED, updates.get(0).type());
    }

    @Test
    void bill_state_is_updated_upon_changes_in_the_external_system() {
        var t1 = tester.createTenant("t1");
        var frozen = createBill(t1, "foo", billingDb);
        var paid   = createBill(t1, "foo", billingDb);
        var voided = createBill(t1, "foo", billingDb);
        exportBills(t1, frozen, paid, voided);

        var updates = toMap(maintainer.maintainInvoices());
        assertEquals(3, updates.size());
        updates.forEach((id, update) -> {
            assertEquals(InvoiceUpdate.Type.UNMODIFIED, update.type());
            assertEquals(BillStatus.OPEN, billingDb.readBill(id).get().status());
        });

        reporter.freezeInvoice(frozen);
        reporter.payInvoice(paid);
        reporter.voidInvoice(voided);
        updates = toMap(maintainer.maintainInvoices());

        assertEquals(3, updates.size());

        assertEquals(InvoiceUpdate.Type.UNMODIFIABLE, updates.get(frozen).type());
        assertEquals(BillStatus.FROZEN, billingDb.readBill(frozen).get().status());

        assertEquals(InvoiceUpdate.Type.PAID, updates.get(paid).type());
        assertEquals(BillStatus.SUCCESSFUL, billingDb.readBill(paid).get().status());

        assertEquals(InvoiceUpdate.Type.VOIDED, updates.get(voided).type());
        assertEquals(BillStatus.VOID, billingDb.readBill(voided).get().status());
    }

    private static Map<Bill.Id, InvoiceUpdate> toMap(Iterable<InvoiceUpdate> updates) {
        var map = new HashMap<Bill.Id, InvoiceUpdate>();
        for (var update : updates) {
            map.put(update.billId(), update);
        }
        return map;
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
