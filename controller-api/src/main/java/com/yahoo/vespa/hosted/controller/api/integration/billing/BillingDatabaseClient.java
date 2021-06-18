// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.config.provision.TenantName;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface that talks about invoices in the billing API.  It is a layer on top of the SQL
 * database where we store data about bills.
 *
 * @author olaa
 * @author ogronnesby
 */
public interface BillingDatabaseClient {

    boolean setActivePaymentInstrument(InstrumentOwner paymentInstrument);

    Optional<InstrumentOwner> getDefaultPaymentInstrumentForTenant(TenantName from);

    /**
     * Create a completely new Invoice in the open state with no LineItems.
     *
     * @param tenant The name of the tenant the invoice is for
     * @param agent  The agent that created the invoice
     * @return The Id of the new invoice
     */
    Invoice.Id createInvoice(TenantName tenant, ZonedDateTime startTime, ZonedDateTime endTime, String agent);

    /**
     * Read the given invoice from the data source
     *
     * @param invoiceId The Id of the invoice to retrieve
     * @return The Invoice if it exists, Optional.empty() if not.
     */
    Optional<Invoice> readInvoice(Invoice.Id invoiceId);

    /**
     * Get all invoices for a given tenant, ordered by date
     *
     * @param tenant The name of the tenant
     * @return List of all invoices ordered by date
     */
    List<Invoice> readInvoicesForTenant(TenantName tenant);

    /**
     * Read all invoices, ordered by date
     * @return List of all invoices ordered by date
     */
    List<Invoice> readInvoices();

    /**
     * Add a line item to an open Invoice
     *
     * @param lineItem
     * @param invoiceId    The optional ID of the invoice this line item is for
     * @return The Id of the new line item
     * @throws RuntimeException if the invoice is not in OPEN state
     */
    String addLineItem(TenantName tenantName, Invoice.LineItem lineItem, Optional<Invoice.Id> invoiceId);

    /**
     * Set status for the given invoice
     *
     * @param invoiceId The ID of the invoice this status is for
     * @param agent     The agent that added the status
     * @param status    The new status of the invoice
     */
    void setStatus(Invoice.Id invoiceId, String agent, String status);

    List<Invoice.LineItem> getUnusedLineItems(TenantName tenantName);

    /**
     * Delete a line item
     * This is only allowed if the line item has not yet been associated with an invoice
     *
     * @param lineItemId The ID of the line item
     * @throws RuntimeException if the line item is associated with an invoice
     */
    void deleteLineItem(String lineItemId);

    /**
     * Associate all uncommitted line items to a given invoice
     * This is only allowed if the line item has not already been associated with an invoice
     *
     * @param tenantName The tenant we want to commit line items for
     * @param invoiceId  The ID of the line item
     * @throws RuntimeException if the line item is already associated with an invoice
     */
    void commitLineItems(TenantName tenantName, Invoice.Id invoiceId);

    /**
     * Return the plan for the given tenant
     *
     * @param tenantName The tenant to retrieve the plan for
     * @return Optional.of the plan if present in DN, else Optional.empty
     */
    Optional<Plan> getPlan(TenantName tenantName);

    /**
     * Return the plan for the given tenants if present.
     * If the database does not know of the tenant, the tenant is not included in the result.
     */
    Map<TenantName, Optional<Plan>> getPlans(List<TenantName> tenants);

    /**
     * Set the current plan for the given tenant
     *
     * @param tenantName The tenant to set the plan for
     * @param plan The plan to use
     */
    void setPlan(TenantName tenantName, Plan plan);

    /**
     * Deactivates the default payment instrument for a tenant, if it exists.
     * Used during tenant deletion
     */
    void deactivateDefaultPaymentInstrument(TenantName tenant);

    /**
     * Get the current collection method for the tenant - if one has persisted
     * @return Optional.empty if no collection method has been persisted for the tenant
     */
    Optional<CollectionMethod> getCollectionMethod(TenantName tenantName);

    /**
     * Set the collection method for the tenant
     * @param tenantName The name of the tenant to set collection method for
     * @param collectionMethod The collection method for the tenant
     */
    void setCollectionMethod(TenantName tenantName, CollectionMethod collectionMethod);
}
