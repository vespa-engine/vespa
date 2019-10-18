// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;

import java.math.BigDecimal;

/**
 * @author olaa
 */
public class CostInfo {

    private final ApplicationId applicationId;
    private final ZoneId zoneId;
    private final BigDecimal cpuHours;
    private final BigDecimal memoryHours;
    private final BigDecimal diskHours;
    private final int cpuCost;
    private final int memoryCost;
    private final int diskCost;


    public CostInfo(ApplicationId applicationId, ZoneId zoneId,
                    BigDecimal cpuHours, BigDecimal memoryHours, BigDecimal diskHours,
                        int cpuCost, int memoryCost, int diskCost) {
        this.applicationId = applicationId;
        this.zoneId = zoneId;
        this.cpuHours = cpuHours;
        this.memoryHours = memoryHours;
        this.diskHours = diskHours;
        this.cpuCost = cpuCost;
        this.memoryCost = memoryCost;
        this.diskCost = diskCost;
    }

    public ApplicationId getApplicationId() {
        return applicationId;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public BigDecimal getCpuHours() {
        return cpuHours;
    }

    public BigDecimal getMemoryHours() {
        return memoryHours;
    }

    public BigDecimal getDiskHours() {
        return diskHours;
    }

    public int getCpuCost() {
        return cpuCost;
    }

    public int getMemoryCost() {
        return memoryCost;
    }

    public int getDiskCost() {
        return diskCost;
    }

}
