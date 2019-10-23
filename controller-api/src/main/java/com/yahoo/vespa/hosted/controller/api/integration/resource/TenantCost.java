// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.TenantName;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author olaa
 */
public interface TenantCost {

    Set<YearMonth> monthsWithMetering(TenantName tenantName);

    List<CostInfo> getTenantCostOfMonth(TenantName tenantName, YearMonth month);

    static TenantCost empty() {
        return new TenantCost() {
            @Override
            public Set<YearMonth> monthsWithMetering(TenantName tenantName) {
                return Collections.emptySet();
            }

            @Override
            public List<CostInfo> getTenantCostOfMonth(TenantName tenantName, YearMonth month) {
                return Collections.emptyList();
            }
        };
    }
}
