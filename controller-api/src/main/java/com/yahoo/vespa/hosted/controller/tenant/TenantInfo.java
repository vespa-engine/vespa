// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Objects;

/**
 * Tenant information beyond technical tenant id and user authorizations.
 *
 * This info is used to capture generic support information and invoiced billing information.
 *
 * All fields are non null but strings can be empty
 *
 * @author smorgrav
 */
public class TenantInfo {

    private final String name;
    private final String email;
    private final String website;

    private final TenantContact contact;
    private final TenantAddress address;
    private final TenantBilling billingContact;
    private final TenantContacts contacts;

    TenantInfo(String name, String email, String website, String contactName, Email contactEmail,
               TenantAddress address, TenantBilling billingContact, TenantContacts contacts) {
        this(name, email, website, TenantContact.from(contactName, contactEmail), address, billingContact, contacts);
    }

    TenantInfo(String name, String email, String website, TenantContact contact, TenantAddress address, TenantBilling billing, TenantContacts contacts) {
        this.name = Objects.requireNonNull(name);
        this.email = Objects.requireNonNull(email);
        this.website = Objects.requireNonNull(website);
        this.contact = Objects.requireNonNull(contact);
        this.address = Objects.requireNonNull(address);
        this.billingContact = Objects.requireNonNull(billing);
        this.contacts = Objects.requireNonNull(contacts);
    }

    public static TenantInfo empty() {
        return new TenantInfo("", "", "", "", Email.empty(), TenantAddress.empty(), TenantBilling.empty(), TenantContacts.empty());
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String website() {
        return website;
    }

    public TenantContact contact() { return contact; }

    public TenantAddress address() { return address; }

    public TenantBilling billingContact() {
        return billingContact;
    }

    public TenantContacts contacts() { return contacts; }

    public boolean isEmpty() {
        return this.equals(empty());
    }

    public TenantInfo withName(String name) {
        return new TenantInfo(name, email, website, contact, address, billingContact, contacts);
    }

    public TenantInfo withEmail(String email) {
        return new TenantInfo(name, email, website, contact, address, billingContact, contacts);
    }

    public TenantInfo withWebsite(String website) {
        return new TenantInfo(name, email, website, contact, address, billingContact, contacts);
    }

    public TenantInfo withContact(TenantContact contact) {
        return new TenantInfo(name, email, website, contact, address, billingContact, contacts);
    }

    public TenantInfo withAddress(TenantAddress address) {
        return new TenantInfo(name, email, website, contact, address, billingContact, contacts);
    }

    public TenantInfo withBilling(TenantBilling billingContact) {
        return new TenantInfo(name, email, website, contact, address, billingContact, contacts);
    }

    public TenantInfo withContacts(TenantContacts contacts) {
        return new TenantInfo(name, email, website, contact, address, billingContact, contacts);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantInfo that = (TenantInfo) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(email, that.email) &&
                Objects.equals(website, that.website) &&
                Objects.equals(contact, that.contact) &&
                Objects.equals(address, that.address) &&
                Objects.equals(billingContact, that.billingContact) &&
                Objects.equals(contacts, that.contacts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, website, contact, address, billingContact, contacts);
    }

    @Override
    public String toString() {
        return "TenantInfo{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", website='" + website + '\'' +
                ", contact=" + contact +
                ", address=" + address +
                ", billingContact=" + billingContact +
                ", contacts=" + contacts +
                '}';
    }
}
