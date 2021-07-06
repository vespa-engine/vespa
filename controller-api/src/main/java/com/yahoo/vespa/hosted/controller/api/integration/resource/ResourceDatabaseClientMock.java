// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Plan;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class ResourceDatabaseClientMock implements ResourceDatabaseClient {

    PlanRegistry planRegistry;
    Map<TenantName, Plan> planMap = new HashMap<>();
    List<ResourceSnapshot> resourceSnapshots = new ArrayList<>();
    private boolean hasRefreshedMaterializedView = false;

    public ResourceDatabaseClientMock(PlanRegistry planRegistry) {
        this.planRegistry = planRegistry;
    }

    @Override
    public void writeResourceSnapshots(Collection<ResourceSnapshot> items) {
        this.resourceSnapshots.addAll(items);
    }

    @Override
    public List<ResourceSnapshot> getResourceSnapshotsForMonth(TenantName tenantName, ApplicationName applicationName, YearMonth month) {
        return resourceSnapshots.stream()
                .filter(resourceSnapshot -> {
                    LocalDate snapshotDate = LocalDate.ofInstant(resourceSnapshot.getTimestamp(), ZoneId.of("UTC"));
                    return YearMonth.from(snapshotDate).equals(month) &&
                            snapshotDate.getYear() == month.getYear() &&
                            resourceSnapshot.getApplicationId().tenant().equals(tenantName) &&
                            resourceSnapshot.getApplicationId().application().equals(applicationName);
                })
                .collect(Collectors.toList());
    }

    @Override
    public Set<YearMonth> getMonthsWithSnapshotsForTenant(TenantName tenantName) {
        return Collections.emptySet();
    }

    @Override
    public List<ResourceSnapshot> getRawSnapshotHistoryForTenant(TenantName tenantName, YearMonth yearMonth) {
        return resourceSnapshots;
    }

    @Override
    public Set<TenantName> getTenants() {
        return resourceSnapshots.stream()
                .map(snapshot -> snapshot.getApplicationId().tenant())
                .collect(Collectors.toSet());
    }

    @Override
    public List<ResourceUsage> getResourceSnapshotsForPeriod(TenantName tenantName, long start, long end) {
        return resourceSnapshots.stream()
                .filter(snapshot -> snapshot.getTimestamp().isAfter(Instant.ofEpochMilli(start)))
                .filter(snapshot -> snapshot.getTimestamp().isBefore(Instant.ofEpochMilli(end)))
                .filter(snapshot -> snapshot.getApplicationId().tenant().equals(tenantName))
                .map(snapshot -> new ResourceUsage(
                        snapshot.getApplicationId(),
                        snapshot.getZoneId(),
                        planMap.getOrDefault(tenantName, planRegistry.defaultPlan()),
                        BigDecimal.valueOf(snapshot.getCpuCores()),
                        BigDecimal.valueOf(snapshot.getMemoryGb()),
                        BigDecimal.valueOf(snapshot.getDiskGb())))
                .collect(
                        Collectors.groupingBy(
                                (ResourceUsage usage) -> Objects.hash(usage.getApplicationId(), usage.getZoneId(), usage.getPlan()),
                                TreeMap::new,
                                Collectors.reducing(
                                        (a, b) -> new ResourceUsage(
                                                a.getApplicationId(),
                                                b.getZoneId(),
                                                a.getPlan(),
                                                a.getCpuMillis().add(b.getCpuMillis()),
                                                a.getMemoryMillis().add(b.getMemoryMillis()),
                                                a.getDiskMillis().add(b.getDiskMillis())
                                        )
                                )
                        )
                )
                .values()
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void refreshMaterializedView() {
        hasRefreshedMaterializedView = true;
    }

    public void setPlan(TenantName tenant, Plan plan) {
        planMap.put(tenant, plan);
    }

    public boolean hasRefreshedMaterializedView() {
        return hasRefreshedMaterializedView;
    }
}
