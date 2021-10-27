// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;

/**
 * @author ogronnesby
 */
public class CostItem {
    private String applicationId;
    private String zoneId;
    private CostItemUsage cpu;
    private CostItemUsage memory;
    private CostItemUsage disk;

    public CostItem() {}

    public ApplicationId getApplicationId() {
        return ApplicationId.fromSerializedForm(applicationId);
    }

    public ZoneId getZoneId() {
        return ZoneId.from(zoneId);
    }

    public CostItemUsage getCpu() {
        return cpu;
    }

    public CostItemUsage getMemory() {
        return memory;
    }

    public CostItemUsage getDisk() {
        return disk;
    }
}
