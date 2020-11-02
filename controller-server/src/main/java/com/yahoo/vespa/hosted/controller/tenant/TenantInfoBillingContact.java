// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Objects;

/**
 * @author smorgrav
 */
public class TenantInfoBillingContact {
    private final String name;
    private final String email;
    private final String phone;
    private final TenantInfoAddress address;

    TenantInfoBillingContact(String name, String email, String phone, TenantInfoAddress address) {
        this.name = Objects.requireNonNull(name);
        this.email = Objects.requireNonNull(email);
        this.phone = Objects.requireNonNull(phone);
        this.address = Objects.requireNonNull(address);
    }

    public static final TenantInfoBillingContact EMPTY =
                new TenantInfoBillingContact("","", "", TenantInfoAddress.EMPTY);

    public String name() {
        return name;
    }

    public String email() { return email; }

    public String phone() {
        return phone;
    }

    public TenantInfoAddress address() {
        return address;
    }

    public TenantInfoBillingContact withName(String newName) {
        return new TenantInfoBillingContact(newName, email, phone, address);
    }

    public TenantInfoBillingContact withEmail(String newEmail) {
        return new TenantInfoBillingContact(name, newEmail, phone, address);
    }

    public TenantInfoBillingContact withPhone(String newPhone) {
        return new TenantInfoBillingContact(name, email, newPhone, address);
    }

    public TenantInfoBillingContact withAddress(TenantInfoAddress newAddress) {
        return new TenantInfoBillingContact(name, email, phone, newAddress);
    }

    public boolean isEmpty() {
        return this.equals(EMPTY);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantInfoBillingContact that = (TenantInfoBillingContact) o;
        return name.equals(that.name) &&
                email.equals(that.email) &&
                phone.equals(that.phone) &&
                address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, phone, address);
    }
}
