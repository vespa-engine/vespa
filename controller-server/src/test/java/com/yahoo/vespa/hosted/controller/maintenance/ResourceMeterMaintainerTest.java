// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockResourceSnapshotConsumer;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryClientMock;
import org.junit.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author olaa
 */
public class ResourceMeterMaintainerTest {

    private final double DELTA = Double.MIN_VALUE;
    NodeRepositoryClientMock nodeRepository = new NodeRepositoryClientMock();
    MockResourceSnapshotConsumer snapshotConsumer = new MockResourceSnapshotConsumer();

    @Test
    public void testMaintainer() {
        ControllerTester tester = new ControllerTester();
        ResourceMeterMaintainer resourceMeterMaintainer = new ResourceMeterMaintainer(tester.controller(), Duration.ofMinutes(5), new JobControl(tester.curator()), nodeRepository, tester.clock(), snapshotConsumer);
        resourceMeterMaintainer.maintain();
        Map<ApplicationId, ResourceSnapshot> consumedResources = snapshotConsumer.consumedResources();

        assertEquals(2, consumedResources.size());

        ResourceSnapshot app1 = consumedResources.get(ApplicationId.from("tenant1", "app1", "default"));
        ResourceSnapshot app2 = consumedResources.get(ApplicationId.from("tenant2", "app2", "default"));

        assertEquals(96, app1.getResourceAllocation().getCpuCores(), DELTA);
        assertEquals(96, app1.getResourceAllocation().getMemoryGb(), DELTA);
        assertEquals(2000, app1.getResourceAllocation().getDiskGb(), DELTA);

        assertEquals(160, app2.getResourceAllocation().getCpuCores(), DELTA);
        assertEquals(96, app2.getResourceAllocation().getMemoryGb(), DELTA);
        assertEquals(2000, app2.getResourceAllocation().getDiskGb(), DELTA);

    }
}