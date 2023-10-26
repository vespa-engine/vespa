// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.vespa.hosted.controller.tenant.BillingReference;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;

public interface BillingReporter {
    BillingReference maintainTenant(CloudTenant tenant);

    InvoiceUpdate maintainInvoice(Bill bill);

    /** Export a bill to a payment service. Returns the invoice ID in the external system. */
    default String exportBill(Bill bill, String exportMethod, CloudTenant tenant) {
        return "NOT_IMPLEMENTED";
    }

}
