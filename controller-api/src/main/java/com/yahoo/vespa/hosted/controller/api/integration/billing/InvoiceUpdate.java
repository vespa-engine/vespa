package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.util.Objects;

/**
 * Helper to track changes to an invoice.
 *
 * @author gjoranv
 */
public abstract class InvoiceUpdate {

    final Bill.Id billId;
    final ItemsUpdate itemsUpdate;

    InvoiceUpdate(Bill.Id billId, ItemsUpdate itemsUpdate) {
        this.billId = billId;
        this.itemsUpdate = itemsUpdate;
    }

    public Bill.Id billId() {
        return billId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvoiceUpdate that = (InvoiceUpdate) o;
        return Objects.equals(billId, that.billId) && Objects.equals(itemsUpdate, that.itemsUpdate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(billId, itemsUpdate);
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
