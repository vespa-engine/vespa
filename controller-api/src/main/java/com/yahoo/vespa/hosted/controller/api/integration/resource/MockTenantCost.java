// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.TenantName;

import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author olaa
 */
public class MockTenantCost implements TenantCost {

    private Set<YearMonth> monthsOfMetering = Collections.emptySet();
    private List<CostInfo> costInfoList = Collections.emptyList();

    @Override
    public Set<YearMonth> monthsWithMetering(TenantName tenantName) {
        return monthsOfMetering;
    }

    @Override
    public List<CostInfo> getTenantCostOfMonth(TenantName tenantName, YearMonth month) {
        return costInfoList;
    }

    public void setMonthsWithMetering(Set<YearMonth> monthsOfMetering) {
        this.monthsOfMetering = monthsOfMetering;
    }

    public void setCostInfoList(List<CostInfo> costInfoList) {
        this.costInfoList = costInfoList;
    }
}
