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

    public static CostItem zeroItem(String applicationId, String zoneId) {
        var item = new CostItem();
        item.applicationId = applicationId;
        item.zoneId = zoneId;
        item.cpu = CostItemUsage.zero();
        item.memory = CostItemUsage.zero();
        item.disk = CostItemUsage.zero();
        return item;
    }

    public static CostItem add(CostItem a, CostItem b) {
        var added = new CostItem();
        added.applicationId = a.applicationId;
        added.zoneId = a.zoneId;
        added.cpu = CostItemUsage.add(a.cpu, b.cpu);
        added.memory = CostItemUsage.add(a.memory, b.memory);
        added.disk = CostItemUsage.add(a.disk, b.disk);
        return added;
    }
}
