// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Plan;

import java.math.BigDecimal;

public class ResourceUsage {

    private final ApplicationId applicationId;
    private final ZoneId zoneId;
    private final Plan plan;
    private final BigDecimal cpuMillis;
    private final BigDecimal memoryMillis;
    private final BigDecimal diskMillis;
    private final NodeResources.Architecture architecture;

    public ResourceUsage(ApplicationId applicationId, ZoneId zoneId, Plan plan, NodeResources.Architecture architecture,
                         BigDecimal cpuMillis, BigDecimal memoryMillis, BigDecimal diskMillis) {
        this.applicationId = applicationId;
        this.zoneId = zoneId;
        this.cpuMillis = cpuMillis;
        this.memoryMillis = memoryMillis;
        this.diskMillis = diskMillis;
        this.plan = plan;
        this.architecture = architecture;
    }

    public ApplicationId getApplicationId() {
        return applicationId;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public BigDecimal getCpuMillis() {
        return cpuMillis;
    }

    public BigDecimal getMemoryMillis() {
        return memoryMillis;
    }

    public BigDecimal getDiskMillis() {
        return diskMillis;
    }

    public Plan getPlan() {
        return plan;
    }

    public NodeResources.Architecture getArchitecture() {
        return architecture;
    }
}
