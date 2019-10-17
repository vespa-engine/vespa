// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;

/**
 * @author olaa
 */
public class CostInfo {

    private final ApplicationId applicationId;
    private final ZoneId zoneId;
    private final double cpuHours;
    private final double memoryHours;
    private final double diskHours;
    private final int cpuCost;
    private final int memoryCost;
    private final int diskCost;


    public CostInfo(ApplicationId applicationId, ZoneId zoneId,
                        double cpuHours, double memoryHours, double diskHours,
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

    public double getCpuHours() {
        return cpuHours;
    }

    public double getMemoryHours() {
        return memoryHours;
    }

    public double getDiskHours() {
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
