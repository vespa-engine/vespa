// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringData;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceAllocation;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringClient;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class MockMeteringClient implements MeteringClient {

    private Collection<ResourceSnapshot> resources = new ArrayList<>();
    private Optional<MeteringData> meteringData;
    private boolean isRefreshed = false;

    @Override
    public void consume(Collection<ResourceSnapshot> resources){
        this.resources = resources;
    }

    @Override
    public MeteringData getMeteringData(TenantName tenantName, ApplicationName applicationName) {
        return meteringData.orElseGet(() -> {
            ResourceAllocation emptyAllocation = new ResourceAllocation(0, 0, 0);
            return new MeteringData(emptyAllocation, emptyAllocation, emptyAllocation, Collections.emptyMap());
        });
    }

    @Override
    public List<ResourceSnapshot> getSnapshotHistoryForTenant(TenantName tenantName, YearMonth yearMonth) {
        return new ArrayList<>(resources);
    }

    @Override
    public void refresh() {
        isRefreshed = true;
    }

    public Collection<ResourceSnapshot> consumedResources() {
        return this.resources;
    }

    public void setMeteringData(MeteringData meteringData) {
        this.meteringData = Optional.of(meteringData);
        this.resources = meteringData.getSnapshotHistory().entrySet().stream().map(Map.Entry::getValue).flatMap(List::stream).collect(Collectors.toList());
    }

    public boolean isRefreshed() {
        return isRefreshed;
    }
}
