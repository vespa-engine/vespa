// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
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
    private final BigDecimal gpuHours;
    private final BigDecimal cpuCost;
    private final BigDecimal memoryCost;
    private final BigDecimal diskCost;
    private final BigDecimal gpuCost;
    private final NodeResources.Architecture architecture;
    private final int majorVersion;


    public CostInfo(ApplicationId applicationId, ZoneId zoneId,
                    BigDecimal cpuHours, BigDecimal memoryHours, BigDecimal diskHours, BigDecimal gpuHours,
                    BigDecimal cpuCost, BigDecimal memoryCost, BigDecimal diskCost, BigDecimal gpuCost, NodeResources.Architecture architecture, int majorVersion) {
        this.applicationId = applicationId;
        this.zoneId = zoneId;
        this.cpuHours = cpuHours;
        this.memoryHours = memoryHours;
        this.diskHours = diskHours;
        this.gpuHours = gpuHours;
        this.cpuCost = cpuCost;
        this.memoryCost = memoryCost;
        this.diskCost = diskCost;
        this.gpuCost = gpuCost;
        this.architecture = architecture;
        this.majorVersion = majorVersion;
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

    public BigDecimal getGpuHours() {
        return gpuHours;
    }

    public BigDecimal getCpuCost() {
        return cpuCost;
    }

    public BigDecimal getMemoryCost() {
        return memoryCost;
    }

    public BigDecimal getDiskCost() {
        return diskCost;
    }

    public BigDecimal getGpuCost() {
        return gpuCost;
    }

    public BigDecimal getTotalCost() {
        return cpuCost.add(memoryCost).add(diskCost).add(gpuCost);
    }

    public NodeResources.Architecture getArchitecture() {
        return architecture;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

}
