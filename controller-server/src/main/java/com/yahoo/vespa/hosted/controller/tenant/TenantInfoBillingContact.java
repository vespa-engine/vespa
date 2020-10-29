package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Objects;

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

    public static TenantInfoBillingContact EmptyBillingContact =
                new TenantInfoBillingContact("","", "", TenantInfoAddress.EmptyAddress);

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
        return (name + email + phone).isEmpty() && address.isEmpty();
    }
}
