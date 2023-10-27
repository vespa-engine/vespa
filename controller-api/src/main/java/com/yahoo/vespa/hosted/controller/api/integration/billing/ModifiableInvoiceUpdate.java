package com.yahoo.vespa.hosted.controller.api.integration.billing;

/**
 * @author gjoranv
 */
public class ModifiableInvoiceUpdate extends InvoiceUpdate {

    public ModifiableInvoiceUpdate(Bill.Id billId, ItemsUpdate itemsUpdate) {
        super(billId, itemsUpdate);
    }

    public ItemsUpdate itemsUpdate() {
        return itemsUpdate;
    }

    public boolean isEmpty() {
        return itemsUpdate.isEmpty();
    }

    public static ModifiableInvoiceUpdate of(Bill.Id billId, int itemsAdded, int itemsRemoved, int itemsModified) {
        return new ModifiableInvoiceUpdate(billId, new ItemsUpdate(itemsAdded, itemsRemoved, itemsModified));
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }
}
