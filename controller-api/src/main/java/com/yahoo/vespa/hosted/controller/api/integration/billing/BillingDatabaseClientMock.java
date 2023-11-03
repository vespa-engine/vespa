// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.config.provision.TenantName;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class BillingDatabaseClientMock implements BillingDatabaseClient {
    private final Clock clock;
    private final PlanRegistry planRegistry;
    private final Map<TenantName, Plan> tenantPlans = new HashMap<>();
    private final Map<Bill.Id, TenantName> invoices = new HashMap<>();
    private final Map<Bill.Id, List<Bill.LineItem>> lineItems = new HashMap<>();
    private final Map<TenantName, List<Bill.LineItem>> uncommittedLineItems = new HashMap<>();

    private final Map<Bill.Id, StatusHistory> statuses = new HashMap<>();
    private final Map<Bill.Id, ZonedDateTime> startTimes = new HashMap<>();
    private final Map<Bill.Id, ZonedDateTime> endTimes = new HashMap<>();
    private final Map<Bill.Id, String> exportedInvoiceIds = new HashMap<>();

    private final ZonedDateTime startTime = LocalDate.of(2020, 4, 1).atStartOfDay(ZoneId.of("UTC"));
    private final ZonedDateTime endTime = LocalDate.of(2020, 5, 1).atStartOfDay(ZoneId.of("UTC"));

    private final List<InstrumentOwner> paymentInstruments = new ArrayList<>();
    private final Map<TenantName, CollectionMethod> collectionMethods = new HashMap<>();
    private final Map<Deal.Id, Deal> deals = new HashMap<>();

    public BillingDatabaseClientMock(Clock clock, PlanRegistry planRegistry) {
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

    public BillStatus getStatus(Bill.Id invoiceId) {
        return statuses.get(invoiceId).current();
    }

    @Override
    public Bill.Id createBill(TenantName tenant, ZonedDateTime startTime, ZonedDateTime endTime, String agent) {
        var invoiceId = Bill.Id.generate();
        invoices.put(invoiceId, tenant);
        statuses.computeIfAbsent(invoiceId, l -> StatusHistory.open(clock));
        startTimes.put(invoiceId, startTime);
        endTimes.put(invoiceId, endTime);
        return invoiceId;
    }

    @Override
    public Optional<Bill> readBill(Bill.Id billId) {
        var invoice = Optional.ofNullable(invoices.get(billId));
        var lines = lineItems.getOrDefault(billId, List.of());
        var status = statuses.getOrDefault(billId, StatusHistory.open(clock));
        var start = startTimes.getOrDefault(billId, startTime);
        var end = endTimes.getOrDefault(billId, endTime);
        var exportedId = exportedInvoiceId(billId);
        return invoice.map(tenant -> new Bill(billId, tenant, status, lines, start, end, exportedId));
    }

    @Override
    public String addLineItem(TenantName tenantName, Bill.LineItem lineItem, Optional<Bill.Id> invoiceId) {
        var lineItemId = UUID.randomUUID().toString();
        invoiceId.ifPresentOrElse(
                invoice -> lineItems.computeIfAbsent(invoice, l -> new ArrayList<>()).add(lineItem),
                () -> uncommittedLineItems.computeIfAbsent(tenantName, l -> new ArrayList<>()).add(lineItem)
        );
        return lineItemId;
    }

    @Override
    public void setStatus(Bill.Id invoiceId, String agent, BillStatus status) {
        statuses.computeIfAbsent(invoiceId, k -> StatusHistory.open(clock))
                .getHistory()
                .put(ZonedDateTime.now(), status);
    }

    @Override
    public List<Bill.LineItem> getUnusedLineItems(TenantName tenantName) {
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
    public void commitLineItems(TenantName tenantName, Bill.Id invoiceId) {

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
    public Optional<Deal> getDealAt(TenantName tenantName, Instant instant) {
        return deals.values().stream()
                .filter(deal -> deal.tenantName().equals(tenantName))
                .filter(deal -> deal.effectiveAt().isBefore(instant))
                .max(Comparator.comparing(Deal::effectiveAt));
    }

    @Override
    public List<Deal> listDeals(TenantName tenantName) {
        return deals.values().stream()
                .filter(deal -> deal.tenantName().equals(tenantName))
                .toList();
    }

    @Override
    public void modifyDeals(TenantName tenantName, DealModifier modifier) {
        DealModifier.Result result = modifier.modify(listDeals(tenantName));
        result.toRemove().ifPresent(deal -> deals.remove(deal.id()));
        result.toAdd().ifPresent(deal -> deals.put(deal.id(), deal));
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
    public List<Bill> readBillsForTenant(TenantName tenant) {
        return invoices.entrySet().stream()
                .filter(entry -> entry.getValue().equals(tenant))
                .map(Map.Entry::getKey)
                .map(invoiceId -> {
                    var items = lineItems.getOrDefault(invoiceId, List.of());
                    var status = statuses.get(invoiceId);
                    var start = startTimes.get(invoiceId);
                    var end = endTimes.get(invoiceId);
                    return new Bill(invoiceId, tenant, status, items, start, end, exportedInvoiceId(invoiceId));
                })
                .toList();
    }

    @Override
    public List<Bill> readBills() {
        return invoices.keySet().stream()
                .map(invoiceId -> {
                    var tenant = invoices.get(invoiceId);
                    var items = lineItems.getOrDefault(invoiceId, List.of());
                    var status = statuses.get(invoiceId);
                    var start = startTimes.get(invoiceId);
                    var end = endTimes.get(invoiceId);
                    return new Bill(invoiceId, tenant, status, items, start, end, exportedInvoiceId(invoiceId));
                })
                .toList();
    }

    @Override
    public void maintain() {}

    @Override
    public void setExportedInvoiceId(Bill.Id billId, String invoiceId) {
        exportedInvoiceIds.put(billId, invoiceId);
    }

    @Override
    public void setExportedInvoiceItemId(String lineItemId, String invoiceItemId) { }

    private String exportedInvoiceId(Bill.Id billId) {
        return exportedInvoiceIds.getOrDefault(billId, null);
    }
}
