// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.TenantName;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author olaa
 */
public interface TenantCost {

    Set<YearMonth> monthsWithMetering(TenantName tenantName);

    List<CostInfo> getTenantCostOfPeriod(TenantName tenantName, long startTimestamp, long endTimestamp);

    default List<CostInfo> getTenantCostOfMonth(TenantName tenantName, YearMonth month) {
        return getTenantCostOfPeriod(tenantName, getMonthStartTimeStamp(month), getMonthEndTimeStamp(month));
    }

    static TenantCost empty() {
        return new TenantCost() {
            @Override
            public Set<YearMonth> monthsWithMetering(TenantName tenantName) {
                return Collections.emptySet();
            }

            @Override
            public List<CostInfo> getTenantCostOfPeriod(TenantName tenantName, long startTime, long endTime) {
                return Collections.emptyList();
            }
        };
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
