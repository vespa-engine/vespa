package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.util.Optional;

/**
 * Helper to track changes to an invoice made by the controller. This should be independent
 * of which external system that is being used.
 *
 * @author gjoranv
 */
public record InvoiceUpdate(Bill.Id billId, Type type, Optional<ItemsUpdate> itemsUpdate) {

    public enum Type {
        UNMODIFIED,    // The invoice was modifiable, but not modified by us
        MODIFIED,      // The invoice was modified by us
        UNMODIFIABLE,  // The invoice was unmodifiable in the external system
        REMOVED,       // Removed from the external system, presumably for a valid reason
        PAID,          // Reported paid from the external system
        VOIDED         // Voided in the external system
    }

    public InvoiceUpdate {
        if (type != Type.MODIFIED && itemsUpdate.isPresent())
            throw new IllegalArgumentException("Items update is only allowed for modified invoices. Update type was " + type);
    }

    public static InvoiceUpdate modifiable(Bill.Id billId, ItemsUpdate itemsUpdate) {
        if (itemsUpdate == null || itemsUpdate.isEmpty()) {
            return new InvoiceUpdate(billId, Type.UNMODIFIED, Optional.empty());
        } else {
            return new InvoiceUpdate(billId, Type.MODIFIED, Optional.of(itemsUpdate));
        }
    }

    public static InvoiceUpdate unmodifiable(Bill.Id billId) {
        return new InvoiceUpdate(billId, Type.UNMODIFIABLE, Optional.empty());
    }

    public static InvoiceUpdate removed(Bill.Id billId) {
        return new InvoiceUpdate(billId, Type.REMOVED, Optional.empty());
    }

    public static InvoiceUpdate paid(Bill.Id billId) {
        return new InvoiceUpdate(billId, Type.PAID, Optional.empty());
    }

    public static InvoiceUpdate voided(Bill.Id billId) {
        return new InvoiceUpdate(billId, Type.VOIDED, Optional.empty());
    }


    public record ItemsUpdate(int itemsAdded, int itemsRemoved, int itemsModified) {

        public boolean isEmpty() {
            return itemsAdded == 0 && itemsRemoved == 0 && itemsModified == 0;
        }

        public static ItemsUpdate empty() {
            return new ItemsUpdate(0, 0, 0);
        }

        public static class Counter {
            private int itemsAdded = 0;
            private int itemsRemoved = 0;
            private int itemsModified = 0;

            public void addedItem() {
                itemsAdded++;
            }

            public void removedItem() {
                itemsRemoved++;
            }

            public void modifiedItem() {
                itemsModified++;
            }

            public ItemsUpdate finish() {
                return new ItemsUpdate(itemsAdded, itemsRemoved, itemsModified);
            }
        }
    }

}
