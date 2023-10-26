package com.yahoo.vespa.hosted.controller.api.integration.billing;

/**
 * Helper to track changes to an invoice.
 *
 * @author gjoranv
 */
public record InvoiceUpdate(int itemsAdded, int itemsRemoved, int itemsModified) {
    public boolean isEmpty() {
        return itemsAdded == 0 && itemsRemoved == 0 && itemsModified == 0;
    }

    public static InvoiceUpdate empty() {
        return new InvoiceUpdate(0, 0, 0);
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

        public void add(InvoiceUpdate other) {
            itemsAdded += other.itemsAdded;
            itemsRemoved += other.itemsRemoved;
            itemsModified += other.itemsModified;
        }

        public InvoiceUpdate finish() {
            return new InvoiceUpdate(itemsAdded, itemsRemoved, itemsModified);
        }
    }

}
