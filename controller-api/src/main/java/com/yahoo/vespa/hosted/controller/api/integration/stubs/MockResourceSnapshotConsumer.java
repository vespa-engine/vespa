// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshotConsumer;

import java.util.List;

/**
 * @author olaa
 */
public class MockResourceSnapshotConsumer implements ResourceSnapshotConsumer {

    private List<ResourceSnapshot> resources;

    @Override
    public void consume(List<ResourceSnapshot> resources){
        this.resources = resources;
    }

    @Override
    public List<ResourceSnapshot> getResourceSnapshots(String tenantName, String applicationName) {
        throw new UnsupportedOperationException();
    }

    public List<ResourceSnapshot> consumedResources() {
        return this.resources;
    }
}
