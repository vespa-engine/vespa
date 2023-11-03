// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.tenant.TaxId;

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

import static com.yahoo.vespa.hosted.controller.api.integration.billing.BillingDatabaseClient.DealModifier.Result;

/**
 * @author olaa
 */
public class MockBillingController implements BillingController {

    private final Clock clock;
    private final BillingDatabaseClient dbClient;

    PlanId defaultPlan = PlanId.from("trial");
    List<TenantName> tenants = new ArrayList<>();
    Map<TenantName, PlanId> plans = new HashMap<>();
    Map<TenantName, List<Bill>> committedBills = new HashMap<>();
    public Map<TenantName, Bill> uncommittedBills = new HashMap<>();
    Map<TenantName, List<Bill.LineItem>> unusedLineItems = new HashMap<>();
    Map<TenantName, CollectionMethod> collectionMethod = new HashMap<>();

    public MockBillingController(Clock clock, BillingDatabaseClient dbClient) {
        this.clock = clock;
        this.dbClient = dbClient;
    }

    @Override
    public Optional<Deal> getCurrentDeal(TenantName tenantName) {
        return dbClient.getDealAt(tenantName, clock.instant());
    }

    @Override
    public List<Deal> listDeals(TenantName tenantName) {
        return dbClient.listDeals(tenantName);
    }

    @Override
    public void addDeal(Deal deal, boolean hasDeployments, boolean isAccountant) {
        dbClient.modifyDeals(deal.tenantName(), currentDeals -> {
            Optional<Deal> toRemove = currentDeals.stream()
                    .filter(d -> d.effectiveAt().equals(deal.effectiveAt()))
                    .findFirst();
            return new Result(toRemove, Optional.of(deal));
        });
    }

    @Override
    public void deleteDeal(TenantName tenantName, Deal.Id dealId, boolean hasDeployments, boolean isAccountant) {
        dbClient.modifyDeals(tenantName, currentDeals -> {
            Optional<Deal> toRemove = currentDeals.stream()
                    .filter(deal -> deal.id().equals(dealId))
                    .findFirst();
            return new Result(toRemove, Optional.empty());
        });
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
                        StatusHistory.open(clock),
                        List.of(),
                        startTime,
                        endTime
                ));
        return billId;
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
    public void addLineItem(TenantName tenant, String description, BigDecimal amount, Optional<Bill.Id> billId, String agent) {
        if (billId.isPresent()) {
            throw new UnsupportedOperationException();
        } else {
            unusedLineItems.computeIfAbsent(tenant, l -> new ArrayList<>())
                    .add(new Bill.LineItem(
                            "line-item-id",
                            description,
                            amount,
                            "paid",
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
    public List<Bill> getBillsForTenant(TenantName tenant) {
        return committedBills.getOrDefault(tenant, List.of());
    }

    @Override
    public Bill getBill(Bill.Id billId) {
        return committedBills.values().stream()
                .flatMap(Collection::stream)
                .filter(bill -> bill.id().equals(billId))
                .findFirst()
                .orElseThrow();
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

    @Override
    public AcceptedCountries getAcceptedCountries() {
        return new AcceptedCountries(List.of(
                new AcceptedCountries.Country(
                        "NO", "Norway", true,
                        List.of(new AcceptedCountries.TaxType("no_vat", "Norwegian VAT number", "[0-9]{9}MVA", "123456789MVA"))),
                new AcceptedCountries.Country(
                        "CA", "Canada", true,
                        List.of(new AcceptedCountries.TaxType("ca_gst_hst", "Canadian GST/HST number", "([0-9]{9}) ?RT ?([0-9]{4})", "123456789RT0002"),
                                new AcceptedCountries.TaxType("ca_pst_bc", "Canadian PST number (British Columbia)", "PST-?([0-9]{4})-?([0-9]{4})", "PST-1234-5678")))
                ));
    }

    @Override
    public void validateTaxId(TaxId id) throws IllegalArgumentException {
        if (id.isEmpty() || id.isLegacy() || id.country().isEmpty()) return;
        if (!List.of("eu_vat", "no_vat").contains(id.type().value()))
            throw new IllegalArgumentException("Unknown tax id type '%s'".formatted(id.type().value()));
        if (!id.code().value().matches("\\w+"))
            throw new IllegalArgumentException("Invalid tax id code '%s'".formatted(id.code().value()));
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
        return new Bill(Bill.Id.of("empty"), TenantName.defaultName(), StatusHistory.open(clock), List.of(), start, end);
    }
}
