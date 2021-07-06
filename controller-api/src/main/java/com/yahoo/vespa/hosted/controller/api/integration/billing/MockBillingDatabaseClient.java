// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.config.provision.TenantName;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class MockBillingDatabaseClient implements BillingDatabaseClient {
    private final Clock clock;
    private final PlanRegistry planRegistry;
    private final Map<TenantName, Plan> tenantPlans = new HashMap<>();
    private final Map<Invoice.Id, TenantName> invoices = new HashMap<>();
    private final Map<Invoice.Id, List<Invoice.LineItem>> lineItems = new HashMap<>();
    private final Map<TenantName, List<Invoice.LineItem>> uncommittedLineItems = new HashMap<>();

    private final Map<Invoice.Id, Invoice.StatusHistory> statuses = new HashMap<>();
    private final Map<Invoice.Id, ZonedDateTime> startTimes = new HashMap<>();
    private final Map<Invoice.Id, ZonedDateTime> endTimes = new HashMap<>();

    private final ZonedDateTime startTime = LocalDate.of(2020, 4, 1).atStartOfDay(ZoneId.of("UTC"));
    private final ZonedDateTime endTime = LocalDate.of(2020, 5, 1).atStartOfDay(ZoneId.of("UTC"));

    private final List<InstrumentOwner> paymentInstruments = new ArrayList<>();
    private final Map<TenantName, CollectionMethod> collectionMethods = new HashMap<>();

    public MockBillingDatabaseClient(Clock clock, PlanRegistry planRegistry) {
        this.clock = clock;
        this.planRegistry = planRegistry;
    }

    @Override
    public boolean setActivePaymentInstrument(InstrumentOwner paymentInstrument) {
        return paymentInstruments.add(paymentInstrument);
    }

    @Override
    public Optional<InstrumentOwner> getDefaultPaymentInstrumentForTenant(TenantName tenantName) {
        return paymentInstruments.stream()
                .filter(paymentInstrument -> paymentInstrument.getTenantName().equals(tenantName))
                .findFirst();
    }

    public String getStatus(Invoice.Id invoiceId) {
        return statuses.get(invoiceId).current();
    }

    @Override
    public Invoice.Id createInvoice(TenantName tenant, ZonedDateTime startTime, ZonedDateTime endTime, String agent) {
        var invoiceId = Invoice.Id.generate();
        invoices.put(invoiceId, tenant);
        statuses.computeIfAbsent(invoiceId, l -> Invoice.StatusHistory.open(clock));
        startTimes.put(invoiceId, startTime);
        endTimes.put(invoiceId, endTime);
        return invoiceId;
    }

    @Override
    public Optional<Invoice> readInvoice(Invoice.Id invoiceId) {
        var invoice = Optional.ofNullable(invoices.get(invoiceId));
        var lines = lineItems.getOrDefault(invoiceId, List.of());
        var status = statuses.getOrDefault(invoiceId, Invoice.StatusHistory.open(clock));
        var start = startTimes.getOrDefault(invoiceId, startTime);
        var end = endTimes.getOrDefault(invoiceId, endTime);
        return invoice.map(tenant -> new Invoice(invoiceId, tenant, status, lines, start, end));
    }

    @Override
    public String addLineItem(TenantName tenantName, Invoice.LineItem lineItem, Optional<Invoice.Id> invoiceId) {
        var lineItemId = UUID.randomUUID().toString();
        invoiceId.ifPresentOrElse(
                invoice -> lineItems.computeIfAbsent(invoice, l -> new ArrayList<>()).add(lineItem),
                () -> uncommittedLineItems.computeIfAbsent(tenantName, l -> new ArrayList<>()).add(lineItem)
        );
        return lineItemId;
    }

    @Override
    public void setStatus(Invoice.Id invoiceId, String agent, String status) {
        statuses.computeIfAbsent(invoiceId, k -> Invoice.StatusHistory.open(clock))
                .getHistory()
                .put(ZonedDateTime.now(), status);
    }

    @Override
    public List<Invoice.LineItem> getUnusedLineItems(TenantName tenantName) {
        return uncommittedLineItems.getOrDefault(tenantName, new ArrayList<>());
    }

    @Override
    public void deleteLineItem(String lineItemId) {
        uncommittedLineItems.values()
                .forEach(list ->
                        list.removeIf(lineItem -> lineItem.id().equals(lineItemId))
                );
    }

    @Override
    public void commitLineItems(TenantName tenantName, Invoice.Id invoiceId) {

    }

    @Override
    public Optional<Plan> getPlan(TenantName tenantName) {
        return Optional.ofNullable(tenantPlans.get(tenantName));
    }

    @Override
    public Map<TenantName, Optional<Plan>> getPlans(List<TenantName> tenants) {
        return tenantPlans.entrySet().stream()
                .filter(entry -> tenants.contains(entry.getKey()))
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> planRegistry.plan(entry.getValue().id())
                ));
    }

    @Override
    public void setPlan(TenantName tenantName, Plan plan) {
        tenantPlans.put(tenantName, plan);
    }

    @Override
    public void deactivateDefaultPaymentInstrument(TenantName tenant) {
        paymentInstruments.removeIf(instrumentOwner -> instrumentOwner.getTenantName().equals(tenant));
    }

    @Override
    public Optional<CollectionMethod> getCollectionMethod(TenantName tenantName) {
        return Optional.ofNullable(collectionMethods.get(tenantName));
    }

    @Override
    public void setCollectionMethod(TenantName tenantName, CollectionMethod collectionMethod) {
        collectionMethods.put(tenantName, collectionMethod);
    }

    @Override
    public List<Invoice> readInvoicesForTenant(TenantName tenant) {
        return invoices.entrySet().stream()
                .filter(entry -> entry.getValue().equals(tenant))
                .map(Map.Entry::getKey)
                .map(invoiceId -> {
                    var items = lineItems.getOrDefault(invoiceId, List.of());
                    var status = statuses.get(invoiceId);
                    var start = startTimes.get(invoiceId);
                    var end = endTimes.get(invoiceId);
                    return new Invoice(invoiceId, tenant, status, items, start, end);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Invoice> readInvoices() {
        return invoices.keySet().stream()
                .map(invoiceId -> {
                    var tenant = invoices.get(invoiceId);
                    var items = lineItems.getOrDefault(invoiceId, List.of());
                    var status = statuses.get(invoiceId);
                    var start = startTimes.get(invoiceId);
                    var end = endTimes.get(invoiceId);
                    return new Invoice(invoiceId, tenant, status, items, start, end);
                })
                .collect(Collectors.toList());
    }
}
