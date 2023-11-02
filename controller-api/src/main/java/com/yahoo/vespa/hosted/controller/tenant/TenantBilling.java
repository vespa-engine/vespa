// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Objects;

/**
 * @author smorgrav
 */
public class TenantBilling {

    private final TenantContact contact;
    private final TenantAddress address;
    private final TaxId taxId;
    private final PurchaseOrder purchaseOrder;
    private final Email invoiceEmail;
    private final TermsOfServiceApproval tosApproval;

    public TenantBilling(TenantContact contact, TenantAddress address, TaxId taxId, PurchaseOrder purchaseOrder,
                         Email invoiceEmail, TermsOfServiceApproval tosApproval) {
        this.contact = Objects.requireNonNull(contact);
        this.address = Objects.requireNonNull(address);
        this.taxId = Objects.requireNonNull(taxId);
        this.purchaseOrder = Objects.requireNonNull(purchaseOrder);
        this.invoiceEmail = Objects.requireNonNull(invoiceEmail);
        this.tosApproval = Objects.requireNonNull(tosApproval);
    }

    public static TenantBilling empty() {
        return new TenantBilling(TenantContact.empty(), TenantAddress.empty(), TaxId.empty(), PurchaseOrder.empty(),
                                 Email.empty(), TermsOfServiceApproval.empty());
    }

    public TenantContact contact() {
        return contact;
    }

    public TenantAddress address() {
        return address;
    }

    public TaxId getTaxId() {
        return taxId;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public Email getInvoiceEmail() {
        return invoiceEmail;
    }

    public TermsOfServiceApproval getToSApproval() { return tosApproval; }

    public TenantBilling withContact(TenantContact updatedContact) {
        return new TenantBilling(updatedContact, this.address, this.taxId, this.purchaseOrder, this.invoiceEmail, tosApproval);
    }

    public TenantBilling withAddress(TenantAddress updatedAddress) {
        return new TenantBilling(this.contact, updatedAddress, this.taxId, this.purchaseOrder, this.invoiceEmail, tosApproval);
    }

    public TenantBilling withTaxId(TaxId updatedTaxId) {
        return new TenantBilling(this.contact, this.address, updatedTaxId, this.purchaseOrder, this.invoiceEmail, tosApproval);
    }

    public TenantBilling withPurchaseOrder(PurchaseOrder updatedPurchaseOrder) {
        return new TenantBilling(this.contact, this.address, this.taxId, updatedPurchaseOrder, this.invoiceEmail, tosApproval);
    }

    public TenantBilling withInvoiceEmail(Email updatedInvoiceEmail) {
        return new TenantBilling(this.contact, this.address, this.taxId, this.purchaseOrder, updatedInvoiceEmail, tosApproval);
    }

    public TenantBilling withToSApproval(TermsOfServiceApproval approval) {
        return new TenantBilling(contact, address, taxId, purchaseOrder, invoiceEmail, approval);
    }

    public boolean isEmpty() {
        return this.equals(empty());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantBilling that = (TenantBilling) o;
        return Objects.equals(contact, that.contact) && Objects.equals(address, that.address)
                && Objects.equals(taxId, that.taxId) && Objects.equals(purchaseOrder, that.purchaseOrder)
                && Objects.equals(invoiceEmail, that.invoiceEmail) && Objects.equals(tosApproval, that.tosApproval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contact, address, taxId, purchaseOrder, invoiceEmail, tosApproval);
    }

    @Override
    public String toString() {
        return "TenantBilling{" +
                "contact=" + contact +
                ", address=" + address +
                ", taxId=" + taxId +
                ", purchaseOrder=" + purchaseOrder +
                ", invoiceEmail=" + invoiceEmail +
                ", tosApproval=" + tosApproval +
                '}';
    }
}
