// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Objects;

/**
 * @author ogronnesby
 */
public class TenantContact {
    private String name;
    private String email;
    private String phone;

    private TenantContact(String name, String email, String phone) {
        this.name = Objects.requireNonNull(name);
        this.email = Objects.requireNonNull(email);
        this.phone = Objects.requireNonNull(phone);
    }

    public static TenantContact from(String name, String email, String phone) {
        return new TenantContact(name, email, phone);
    }

    public static TenantContact from(String name, String email) {
        return TenantContact.from(name, email, "");
    }

    public static TenantContact empty() {
        return new TenantContact("", "", "");
    }

    public String name() { return name; }
    public String email() { return email; }
    public String phone() { return phone; }

    public TenantContact withName(String name) {
        return new TenantContact(name, email, phone);
    }

    public TenantContact withEmail(String email) {
        return new TenantContact(name, email, phone);
    }

    public TenantContact withPhone(String phone) {
        return new TenantContact(name, email, phone);
    }

    @Override
    public String toString() {
        return "TenantContact{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantContact that = (TenantContact) o;
        return Objects.equals(name, that.name) && Objects.equals(email, that.email) && Objects.equals(phone, that.phone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, phone);
    }
}
