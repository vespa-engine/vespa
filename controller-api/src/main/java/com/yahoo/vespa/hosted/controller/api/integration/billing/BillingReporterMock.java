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

    private final Map<Bill.Id, String> exportedBills = new HashMap<>();

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
        if (exportedBills.containsKey(bill.id())) {
            dbClient.addLineItem(bill.tenant(), maintainedMarkerItem(), Optional.of(bill.id()));
            return ModifiableInvoiceUpdate.of(bill.id(), 1, 0, 0);
        } else {
            return FailedInvoiceUpdate.removed(bill.id());
        }
    }

    @Override
    public String exportBill(Bill bill, String exportMethod, CloudTenant tenant) {
        // Replace bill with a copy with exportedId set
        var exportedId = "EXPORTED-" + bill.id().value();
        exportedBills.put(bill.id(), exportedId);
        dbClient.setExportedInvoiceId(bill.id(), exportedId);
        return exportedId;
    }

    // Emulates deleting a bill in the external system.
    public void deleteExportedBill(Bill.Id billId) {
        exportedBills.remove(billId);
    }

    private static Bill.LineItem maintainedMarkerItem() {
        return new Bill.LineItem("maintained", "", BigDecimal.valueOf(0.0), "", "", ZonedDateTime.now());
    }

}
