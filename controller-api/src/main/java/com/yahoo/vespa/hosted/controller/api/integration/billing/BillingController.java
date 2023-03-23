// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.config.provision.TenantName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A service that controls creation of bills based on the resource usage of a tenant, controls the quota for a
 * tenant, and controls the plan the tenant is on.
 *
 * @author ogronnesby
 * @author olaa
 */
public interface BillingController {

    /**
     * Get the plan ID for the given tenant.
     * This method will not fail if the tenant does not exist, it will return the default plan for that tenant instead.
     */
    PlanId getPlan(TenantName tenant);

    /**
     * Return the list of tenants with the given plan.
     * @param existing All existing tenants in the system
     * @param planId The ID of the plan to filter existing tenants on.
     * @return The tenants that have the given plan.
     */
    List<TenantName> tenantsWithPlan(List<TenantName> existing, PlanId planId);

    /** The display name of the given plan */
    String getPlanDisplayName(PlanId planId);

    /**
     * The quota for the given tenant.
     * This method will return default quota for tenants that do not exist.
     */
    Quota getQuota(TenantName tenant);

    /**
     * Set the plan for the current tenant.  Checks some pre-conditions to see if the tenant is eligible for the
     * given plan.
     * @param tenant The name of the tenant.
     * @param planId The ID of the plan to change to.
     * @param hasDeployments Does the tenant have active deployments.
     * @param isAccountant Is it the hosted accountant that is doing the operation
     * @return String containing error message if something went wrong. Empty otherwise
     */
    PlanResult setPlan(TenantName tenant, PlanId planId, boolean hasDeployments, boolean isAccountant);

    /**
     * Create a bill of unbilled use for the given tenant in the given time period.
     * @param tenant The name of the tenant.
     * @param startTime The start of the billing period
     * @param endTime The end of the billing period
     * @param agent The agent that creates the bill
     * @return The ID of the new bill.
     */
    Bill.Id createBillForPeriod(TenantName tenant, ZonedDateTime startTime, ZonedDateTime endTime, String agent);
    Bill.Id createBillForPeriod(TenantName tenant, LocalDate startDate, LocalDate endDate, String agent);

    /**
     * Create an unpersisted bill of unbilled use for the given tenant from the end of last bill until the given date.
     * This is used to show "unbilled use" in the Console.
     * @param tenant The name of the tenant.
     * @param until The end date of the unbilled use period.
     * @return A bill with the resource use and cost.
     */
    Bill createUncommittedBill(TenantName tenant, LocalDate until);

    /** Run {createUncommittedBill} for all tenants with unbilled use */
    Map<TenantName, Bill> createUncommittedBills(LocalDate until);

    /** Get line items that have been manually added to a tenant, but is not yet part of a bill */
    List<Bill.LineItem> getUnusedLineItems(TenantName tenant);

    /** Get the payment instrument for the given tenant */
    Optional<PaymentInstrument> getDefaultInstrument(TenantName tenant);

    /** Get the auth token needed to talk to payment services */
    String createClientToken(String tenant, String userId);

    /** Delete a payment instrument from the list of the tenant's instruments */
    boolean deleteInstrument(TenantName tenant, String userId, String instrumentId);

    /** Change the status of the given bill */
    void updateBillStatus(Bill.Id billId, String agent, String status);

    /** Add a line item to the given bill */
    void addLineItem(TenantName tenant, String description, BigDecimal amount, Optional<Bill.Id> billId, String agent);

    /** Delete a line item - only available for unused line items */
    void deleteLineItem(String lineItemId);

    /** Set the given payment instrument as the active instrument for the tenant */
    boolean setActivePaymentInstrument(InstrumentOwner paymentInstrument);

    /** List the payment instruments from the tenant */
    InstrumentList listInstruments(TenantName tenant, String userId);

    /** Get all bills for the given tenant */
    List<Bill> getBillsForTenant(TenantName tenant);

    /** Get all bills from the system */
    List<Bill> getBills();

    /** Get the bill collection method for the given tenant */
    default CollectionMethod getCollectionMethod(TenantName tenant) {
        return CollectionMethod.NONE;
    }

    /** Set the bill collection method for the given tenant */
    default CollectionResult setCollectionMethod(TenantName tenant, CollectionMethod method) {
        return CollectionResult.error("Method not implemented");
    }

    /** Test if the number of tenants with the given plan is under the given limit */
    default boolean tenantsWithPlanUnderLimit(Plan plan, int limit) {
        return true;
    }

    default void updateCache(List<TenantName> tenants) {}
}