// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshotConsumer;

import java.util.Map;

/**
 * @author olaa
 */
public class MockResourceSnapshotConsumer implements ResourceSnapshotConsumer {

    private Map<ApplicationId, ResourceSnapshot> resources;

    @Override
    public void consume(Map<ApplicationId, ResourceSnapshot> resources){
        this.resources = resources;
    }

    public Map<ApplicationId, ResourceSnapshot> consumedResources() {
        return resources;
    }
}
