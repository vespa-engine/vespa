package com.yahoo.vespa.hosted.controller.api.integration.billing;

/**
 * @author gjoranv
 */
public class FailedInvoiceUpdate extends InvoiceUpdate {

    public enum Reason {
        UNMODIFIABLE,
        REMOVED
    }

    public final Reason reason;

    public FailedInvoiceUpdate(Bill.Id billId, Reason reason) {
        super(billId, ItemsUpdate.empty());
        this.reason = reason;
    }

    public static FailedInvoiceUpdate unmodifiable(Bill.Id billId) {
        return new FailedInvoiceUpdate(billId, Reason.UNMODIFIABLE);
    }

    public static FailedInvoiceUpdate removed(Bill.Id billId) {
        return new FailedInvoiceUpdate(billId, Reason.REMOVED);
    }

}
