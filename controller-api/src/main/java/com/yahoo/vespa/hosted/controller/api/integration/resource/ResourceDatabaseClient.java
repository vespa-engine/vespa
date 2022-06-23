// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.identifiers.ClusterId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;


import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author olaa
 */
public interface ResourceDatabaseClient {

    void writeResourceSnapshots(Collection<ResourceSnapshot> snapshots);

    List<ResourceUsage> getResourceSnapshotsForPeriod(TenantName tenantName, long startTimestamp, long endTimestamp);

    void refreshMaterializedView();

    void writeScalingEvents(ClusterId clusterId, Collection<Cluster.ScalingEvent> scalingEvents);

    Map<ClusterId, List<Cluster.ScalingEvent>> scalingEvents(Instant from, Instant to, DeploymentId deploymentId);

    Set<YearMonth> getMonthsWithSnapshotsForTenant(TenantName tenantName);

    List<ResourceSnapshot> getRawSnapshotHistoryForTenant(TenantName tenantName, YearMonth yearMonth);

    Set<TenantName> getTenants();

    Instant getOldestSnapshotTimestamp(Set<DeploymentId> deployments);

    default List<ResourceUsage> getResourceSnapshotsForMonth(TenantName tenantName, YearMonth month) {
        return getResourceSnapshotsForPeriod(tenantName, getMonthStartTimeStamp(month), getMonthEndTimeStamp(month));
    }

    private long getMonthStartTimeStamp(YearMonth month) {
        LocalDate startOfMonth = LocalDate.of(month.getYear(), month.getMonth(), 1);
        return startOfMonth.atStartOfDay(java.time.ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli();
    }
    private long getMonthEndTimeStamp(YearMonth month) {
        LocalDate startOfMonth = LocalDate.of(month.getYear(), month.getMonth(), 1);
        return startOfMonth.plus(1, ChronoUnit.MONTHS)
                .atStartOfDay(java.time.ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli();
    }

}
