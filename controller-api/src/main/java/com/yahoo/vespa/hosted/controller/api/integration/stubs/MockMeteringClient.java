// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringInfo;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceAllocation;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author olaa
 */
public class MockMeteringClient implements MeteringClient {

    private List<ResourceSnapshot> resources = new ArrayList<>();

    @Override
    public void consume(List<ResourceSnapshot> resources){
        this.resources = resources;
    }

    @Override
    public MeteringInfo getResourceSnapshots(String tenantName, String applicationName) {
        ResourceAllocation emptyAllocation = new ResourceAllocation(0, 0, 0);
        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, "default");
        Map<ApplicationId, List<ResourceSnapshot>> snapshotHistory = Map.of(applicationId, new ArrayList<>());
        return new MeteringInfo(emptyAllocation, emptyAllocation, emptyAllocation, snapshotHistory);
    }

    public List<ResourceSnapshot> consumedResources() {
        return this.resources;
    }
}
