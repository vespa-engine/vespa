// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Objects;

/**
 * @author smorgrav
 */
public class TenantBilling {

    private final TenantContact contact;
    private final TenantAddress address;

    public TenantBilling(TenantContact contact, TenantAddress address) {
        this.contact = Objects.requireNonNull(contact);
        this.address = Objects.requireNonNull(address);
    }

    public static TenantBilling empty() {
        return new TenantBilling(TenantContact.empty(), TenantAddress.empty());
    }

    public TenantContact contact() {
        return contact;
    }

    public TenantAddress address() {
        return address;
    }

    public TenantBilling withContact(TenantContact updatedContact) {
        return new TenantBilling(updatedContact, this.address);
    }

    public TenantBilling withAddress(TenantAddress updatedAddress) {
        return new TenantBilling(this.contact, updatedAddress);
    }

    public boolean isEmpty() {
        return this.equals(empty());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantBilling that = (TenantBilling) o;
        return Objects.equals(contact, that.contact) && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contact, address);
    }

    @Override
    public String toString() {
        return "TenantInfoBillingContact{" +
                "contact=" + contact +
                ", address=" + address +
                '}';
    }
}
