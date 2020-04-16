// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.config.provision.TenantName;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringClient;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;

import java.time.YearMonth;
import java.util.List;

/**
 * @author olaa
 */
public class MeteringResponse extends SlimeJsonResponse {

    public MeteringResponse(MeteringClient meteringClient, String tenantName, String month) {
        super(toSlime(meteringClient, tenantName, month));
    }

    private static Slime toSlime(MeteringClient meteringClient, String tenantName, String month) {
        Slime slime = new Slime();
        Cursor root = slime.setArray();
        List<ResourceSnapshot> snapshots = meteringClient.getSnapshotHistoryForTenant(TenantName.from(tenantName), YearMonth.parse(month));
        snapshots.forEach(snapshot -> {
            Cursor object = root.addObject();
            object.setString("applicationId", snapshot.getApplicationId().toShortString());
            object.setLong("timestamp", snapshot.getTimestamp().toEpochMilli());
            object.setString("zoneId", snapshot.getZoneId().value());
            object.setDouble("cpu", snapshot.getCpuCores());
            object.setDouble("memory", snapshot.getMemoryGb());
            object.setDouble("disk", snapshot.getDiskGb());
        });
        return slime;
    }

}
