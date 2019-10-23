// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringInfo;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceAllocation;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author olaa
 */
public class MockMeteringClient implements MeteringClient {

    private Collection<ResourceSnapshot> resources = new ArrayList<>();
    private Optional<MeteringInfo> meteringInfo;

    @Override
    public void consume(Collection<ResourceSnapshot> resources){
        this.resources = resources;
    }

    @Override
    public MeteringInfo getResourceSnapshots(String tenantName, String applicationName) {
        return meteringInfo.orElseGet(() -> {
            ResourceAllocation emptyAllocation = new ResourceAllocation(0, 0, 0);
            return new MeteringInfo(emptyAllocation, emptyAllocation, emptyAllocation, Collections.emptyMap());
        });
    }

    public Collection<ResourceSnapshot> consumedResources() {
        return this.resources;
    }

    public void setMeteringInfo(MeteringInfo meteringInfo) {
        this.meteringInfo = Optional.of(meteringInfo);
    }
}
