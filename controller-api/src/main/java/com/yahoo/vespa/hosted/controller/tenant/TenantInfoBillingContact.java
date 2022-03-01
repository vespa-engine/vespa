// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Objects;

/**
 * @author smorgrav
 */
public class TenantInfoBillingContact {

    private final TenantContact contact;
    private final TenantAddress address;

    TenantInfoBillingContact(String name, String email, String phone, TenantAddress address) {
        this(TenantContact.from(name, email, phone), address);
    }

    TenantInfoBillingContact(TenantContact contact, TenantAddress address) {
        this.contact = Objects.requireNonNull(contact);
        this.address = Objects.requireNonNull(address);
    }

    public static TenantInfoBillingContact empty() {
        return new TenantInfoBillingContact("", "", "", TenantAddress.empty());
    }

    public TenantContact contact() {
        return contact;
    }

    public TenantAddress address() {
        return address;
    }

    public TenantInfoBillingContact withContact(TenantContact updatedContact) {
        return new TenantInfoBillingContact(updatedContact, this.address);
    }

    public TenantInfoBillingContact withAddress(TenantAddress updatedAddress) {
        return new TenantInfoBillingContact(this.contact, updatedAddress);
    }

    public boolean isEmpty() {
        return this.equals(empty());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantInfoBillingContact that = (TenantInfoBillingContact) o;
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
