// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.vespa.hosted.controller.tenant.BillingReference;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class BillingReporterMock implements BillingReporter {
    private final Clock clock;
    private final BillingDatabaseClient dbClient;

    private final Map<Bill.Id, InvoiceUpdate> exportedBills = new HashMap<>();

    public BillingReporterMock(Clock clock, BillingDatabaseClient dbClient) {
        this.clock = clock;
        this.dbClient = dbClient;
    }

    @Override
    public BillingReference maintainTenant(CloudTenant tenant) {
        return new BillingReference(UUID.randomUUID().toString(), clock.instant());
    }

    @Override
    public InvoiceUpdate maintainInvoice(CloudTenant tenant, Bill bill) {
        if (! exportedBills.containsKey(bill.id())) {
            // Given that it has been exported earlier (caller's responsibility), we can assume it has been removed.
            return InvoiceUpdate.removed(bill.id());
        }
        if (exportedBills.get(bill.id()).type() == InvoiceUpdate.Type.MODIFIED) {
            // modifyInvoice() has been called -> add a marker line item
            if (bill.status() != BillStatus.OPEN) throw new IllegalArgumentException("Bill should be OPEN");
            dbClient.addLineItem(bill.tenant(), maintainedMarkerItem(), Optional.of(bill.id()));
        }
        return exportedBills.get(bill.id());
    }

    @Override
    public String exportBill(Bill bill, String exportMethod, CloudTenant tenant) {
        // Replace bill with a copy with exportedId set
        var exportedId = "EXPORTED-" + bill.id().value();
        exportedBills.put(bill.id(), InvoiceUpdate.modifiable(bill.id(), null));
        dbClient.setExportedInvoiceId(bill.id(), exportedId);
        return exportedId;
    }

    public void modifyInvoice(Bill.Id billId) {
        ensureExported(billId);
        var itemsUpdate = new InvoiceUpdate.ItemsUpdate(1, 0, 0);
        exportedBills.put(billId, InvoiceUpdate.modifiable(billId, itemsUpdate));
    }

    public void freezeInvoice(Bill.Id billId) {
        ensureExported(billId);
        exportedBills.put(billId, InvoiceUpdate.unmodifiable(billId));
    }

    public void payInvoice(Bill.Id billId) {
        ensureExported(billId);
        exportedBills.put(billId, InvoiceUpdate.paid(billId));
    }

    public void voidInvoice(Bill.Id billId) {
        ensureExported(billId);
        exportedBills.put(billId, InvoiceUpdate.voided(billId));
    }

    // Emulates deleting a bill in the external system.
    public void deleteInvoice(Bill.Id billId) {
        ensureExported(billId);
        exportedBills.remove(billId);
    }

    private void ensureExported(Bill.Id billId) {
        if (! exportedBills.containsKey(billId)) throw new IllegalArgumentException("Bill not exported");
    }

    private static Bill.LineItem maintainedMarkerItem() {
        return new Bill.LineItem("maintained", "", BigDecimal.valueOf(0.0), "", "", ZonedDateTime.now());
    }

}
