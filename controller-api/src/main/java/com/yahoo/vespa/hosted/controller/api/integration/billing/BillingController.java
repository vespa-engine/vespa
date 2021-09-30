// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.integration.user.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface BillingController {

    PlanId getPlan(TenantName tenant);

    List<TenantName> tenantsWithPlan(List<TenantName> existing, PlanId planId);

    String getPlanDisplayName(PlanId planId);

    Quota getQuota(TenantName tenant);

    /**
     * @return String containing error message if something went wrong. Empty otherwise
     */
    PlanResult setPlan(TenantName tenant, PlanId planId, boolean hasDeployments);

    Invoice.Id createInvoiceForPeriod(TenantName tenant, ZonedDateTime startTime, ZonedDateTime endTime, String agent);

    Invoice createUncommittedInvoice(TenantName tenant, LocalDate until);

    Map<TenantName, Invoice> createUncommittedInvoices(LocalDate until);

    List<Invoice.LineItem> getUnusedLineItems(TenantName tenant);

    Optional<PaymentInstrument> getDefaultInstrument(TenantName tenant);

    String createClientToken(String tenant, String userId);

    boolean deleteInstrument(TenantName tenant, String userId, String instrumentId);

    void updateInvoiceStatus(Invoice.Id invoiceId, String agent, String status);

    void addLineItem(TenantName tenant, String description, BigDecimal amount, String agent);

    void deleteLineItem(String lineItemId);

    boolean setActivePaymentInstrument(InstrumentOwner paymentInstrument);

    InstrumentList listInstruments(TenantName tenant, String userId);

    List<Invoice> getInvoicesForTenant(TenantName tenant);

    List<Invoice> getInvoices();

    void deleteBillingInfo(TenantName tenant, Set<User> users, boolean isPrivileged);

    default CollectionMethod getCollectionMethod(TenantName tenant) {
        return CollectionMethod.NONE;
    }

    default CollectionResult setCollectionMethod(TenantName tenant, CollectionMethod method) {
        return CollectionResult.error("Method not implemented");
    }
}