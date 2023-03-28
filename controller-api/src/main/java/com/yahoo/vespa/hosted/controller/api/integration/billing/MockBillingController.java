// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.config.provision.TenantName;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author olaa
 */
public class MockBillingController implements BillingController {

    private final Clock clock;

    PlanId defaultPlan = PlanId.from("trial");
    List<TenantName> tenants = new ArrayList<>();
    Map<TenantName, PlanId> plans = new HashMap<>();
    Map<TenantName, PaymentInstrument> activeInstruments = new HashMap<>();
    Map<TenantName, List<Bill>> committedBills = new HashMap<>();
    Map<TenantName, Bill> uncommittedBills = new HashMap<>();
    Map<TenantName, List<Bill.LineItem>> unusedLineItems = new HashMap<>();
    Map<TenantName, CollectionMethod> collectionMethod = new HashMap<>();

    public MockBillingController(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PlanId getPlan(TenantName tenant) {
        return plans.getOrDefault(tenant, PlanId.from("trial"));
    }

    @Override
    public List<TenantName> tenantsWithPlan(List<TenantName> tenants, PlanId planId) {
        return tenants.stream()
                .filter(t -> plans.getOrDefault(t, PlanId.from("trial")).equals(planId))
                .toList();
    }

    @Override
    public String getPlanDisplayName(PlanId planId) {
        return "Plan with id: " + planId.value();
    }

    @Override
    public Quota getQuota(TenantName tenant) {
        return Quota.unlimited().withMaxClusterSize(5);
    }

    @Override
    public PlanResult setPlan(TenantName tenant, PlanId planId, boolean hasDeployments, boolean isAccountant) {
        plans.put(tenant, planId);
        return PlanResult.success();
    }

    @Override
    public Bill.Id createBillForPeriod(TenantName tenant, ZonedDateTime startTime, ZonedDateTime endTime, String agent) {
        var billId = Bill.Id.of("id-123");
        committedBills.computeIfAbsent(tenant, l -> new ArrayList<>())
                .add(new Bill(
                        billId,
                        tenant,
                        Bill.StatusHistory.open(clock),
                        List.of(),
                        startTime,
                        endTime
                ));
        return billId;
    }

    @Override
    public Bill.Id createBillForPeriod(TenantName tenant, LocalDate startDate, LocalDate endDate, String agent) {
        return createBillForPeriod(tenant, startDate.atStartOfDay(ZoneOffset.UTC), endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC), agent);
    }

    @Override
    public Bill createUncommittedBill(TenantName tenant, LocalDate until) {
        return uncommittedBills.getOrDefault(tenant, emptyBill());
    }

    @Override
    public Map<TenantName, Bill> createUncommittedBills(LocalDate until) {
        return uncommittedBills;
    }

    @Override
    public List<Bill.LineItem> getUnusedLineItems(TenantName tenant) {
        return unusedLineItems.getOrDefault(tenant, List.of());
    }

    @Override
    public Optional<PaymentInstrument> getDefaultInstrument(TenantName tenant) {
        return Optional.ofNullable(activeInstruments.get(tenant));
    }

    @Override
    public String createClientToken(String tenant, String userId) {
        return "some-token";
    }

    @Override
    public boolean deleteInstrument(TenantName tenant, String userId, String instrumentId) {
        activeInstruments.remove(tenant);
        return true;
    }

    @Override
    public void updateBillStatus(Bill.Id billId, String agent, String status) {
        var now = clock.instant().atZone(ZoneOffset.UTC);
        committedBills.values().stream()
                .flatMap(List::stream)
                .filter(bill -> billId.equals(bill.id()))
                .forEach(bill -> bill.statusHistory().history.put(now, status));
    }

    @Override
    public void addLineItem(TenantName tenant, String description, BigDecimal amount, Optional<Bill.Id> billId, String agent) {
        if (billId.isPresent()) {
            throw new UnsupportedOperationException();
        } else {
            unusedLineItems.computeIfAbsent(tenant, l -> new ArrayList<>())
                    .add(new Bill.LineItem(
                            "line-item-id",
                            description,
                            amount,
                            "some-plan",
                            agent,
                            ZonedDateTime.now()));
        }
    }

    @Override
    public void deleteLineItem(String lineItemId) {
        unusedLineItems.values()
                .forEach(lineItems -> lineItems.
                        removeIf(lineItem -> lineItem.id().equals(lineItemId))
                );
    }

    @Override
    public boolean setActivePaymentInstrument(InstrumentOwner paymentInstrument) {
        var instrumentId = paymentInstrument.getPaymentInstrumentId();
        activeInstruments.put(paymentInstrument.getTenantName(), createInstrument(instrumentId));
        return true;
    }

    @Override
    public InstrumentList listInstruments(TenantName tenant, String userId) {
        return null;
    }

    @Override
    public List<Bill> getBillsForTenant(TenantName tenant) {
        return committedBills.getOrDefault(tenant, List.of());
    }

    @Override
    public List<Bill> getBills() {
        return committedBills.values().stream().flatMap(Collection::stream).toList();
    }

    @Override
    public CollectionMethod getCollectionMethod(TenantName tenant) {
        return collectionMethod.getOrDefault(tenant, CollectionMethod.AUTO);
    }

    @Override
    public CollectionResult setCollectionMethod(TenantName tenant, CollectionMethod method) {
        collectionMethod.put(tenant, method);
        return CollectionResult.success();
    }

    @Override
    public boolean tenantsWithPlanUnderLimit(Plan plan, int limit) {
        if (limit < 0) return true;

        var count = Stream.concat(tenants.stream(), plans.keySet().stream())
                .distinct()
                .map(tenant -> plans.getOrDefault(tenant, defaultPlan))
                .filter(p -> p.equals(plan.id()))
                .count();

        return count < limit;
    }

    public void setTenants(List<TenantName> tenants) {
        this.tenants = tenants;
    }

    private PaymentInstrument createInstrument(String id) {
        return new PaymentInstrument(id,
                "name",
                "displayText",
                "brand",
                "type",
                "endingWith",
                "expiryDate",
                "addressLine1",
                "addressLine2",
                "zip",
                "city",
                "state",
                "country");
    }

    public void addBill(TenantName tenantName, Bill bill, boolean committed) {
        if (committed)
            committedBills.computeIfAbsent(tenantName, i -> new ArrayList<>())
                    .add(bill);
        else
            uncommittedBills.put(tenantName, bill);
    }

    private Bill emptyBill() {
        var start = clock.instant().atZone(ZoneOffset.UTC);
        var end = clock.instant().atZone(ZoneOffset.UTC);
        return new Bill(Bill.Id.of("empty"), TenantName.defaultName(), Bill.StatusHistory.open(clock), List.of(), start, end);
    }
}
