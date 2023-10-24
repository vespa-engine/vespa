// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Objects;

/**
 * @author smorgrav
 */
public class TenantBilling {

    private final TenantContact contact;
    private final TenantAddress address;
    private final TaxCode taxCode;
    private final PurchaseOrder purchaseOrder;
    private final Email invoiceEmail;

    public TenantBilling(TenantContact contact, TenantAddress address, TaxCode taxCode, PurchaseOrder purchaseOrder, Email invoiceEmail) {
        this.contact = Objects.requireNonNull(contact);
        this.address = Objects.requireNonNull(address);
        this.taxCode = Objects.requireNonNull(taxCode);
        this.purchaseOrder = Objects.requireNonNull(purchaseOrder);
        this.invoiceEmail = Objects.requireNonNull(invoiceEmail);
    }

    public static TenantBilling empty() {
        return new TenantBilling(TenantContact.empty(), TenantAddress.empty(), TaxCode.empty(), PurchaseOrder.empty(), Email.empty());
    }

    public TenantContact contact() {
        return contact;
    }

    public TenantAddress address() {
        return address;
    }

    public TaxCode getTaxCode() {
        return taxCode;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public Email getInvoiceEmail() {
        return invoiceEmail;
    }

    public TenantBilling withContact(TenantContact updatedContact) {
        return new TenantBilling(updatedContact, this.address, this.taxCode, this.purchaseOrder, this.invoiceEmail);
    }

    public TenantBilling withAddress(TenantAddress updatedAddress) {
        return new TenantBilling(this.contact, updatedAddress, this.taxCode, this.purchaseOrder, this.invoiceEmail);
    }

    public TenantBilling withTaxCode(TaxCode updatedTaxCode) {
        return new TenantBilling(this.contact, this.address, updatedTaxCode, this.purchaseOrder, this.invoiceEmail);
    }

    public TenantBilling withPurchaseOrder(PurchaseOrder updatedPurchaseOrder) {
        return new TenantBilling(this.contact, this.address, this.taxCode, updatedPurchaseOrder, this.invoiceEmail);
    }

    public TenantBilling withInvoiceEmail(Email updatedInvoiceEmail) {
        return new TenantBilling(this.contact, this.address, this.taxCode, this.purchaseOrder, updatedInvoiceEmail);
    }

    public boolean isEmpty() {
        return this.equals(empty());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantBilling that = (TenantBilling) o;
        return Objects.equals(contact, that.contact) &&
                Objects.equals(address, that.address) &&
                Objects.equals(taxCode, that.taxCode) &&
                Objects.equals(purchaseOrder, that.purchaseOrder) &&
                Objects.equals(invoiceEmail, that.invoiceEmail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contact, address, taxCode, purchaseOrder, invoiceEmail);
    }

    @Override
    public String toString() {
        return "TenantBilling{" +
                "contact=" + contact +
                ", address=" + address +
                ", taxCode='" + taxCode + '\'' +
                ", purchaseOrder='" + purchaseOrder + '\'' +
                ", invoiceEmail=" + invoiceEmail +
                '}';
    }
}
