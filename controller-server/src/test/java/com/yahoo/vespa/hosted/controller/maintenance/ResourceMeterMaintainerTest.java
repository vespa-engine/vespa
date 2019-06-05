// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockResourceSnapshotConsumer;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryClientMock;
import com.yahoo.vespa.hosted.controller.integration.MetricsMock;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author olaa
 */
public class ResourceMeterMaintainerTest {

    private final double DELTA = Double.MIN_VALUE;
    private NodeRepositoryClientMock nodeRepository = new NodeRepositoryClientMock();
    private MockResourceSnapshotConsumer snapshotConsumer = new MockResourceSnapshotConsumer();
    private MetricsMock metrics = new MetricsMock();

    @Test
    public void testMaintainer() {
        ControllerTester tester = new ControllerTester();
        List<ZoneId> zones = new ArrayList<>();
        zones.add(ZoneId.from("prod", "us-east-3"));
        zones.add(ZoneId.from("prod", "us-west-1"));
        zones.add(ZoneId.from("prod", "us-central-1"));
        zones.add(ZoneId.from("prod", "aws-us-east-1", "aws"));
        tester.zoneRegistry().setZones(zones);
        ResourceMeterMaintainer resourceMeterMaintainer = new ResourceMeterMaintainer(tester.controller(), Duration.ofMinutes(5), new JobControl(tester.curator()), nodeRepository, tester.clock(), metrics, snapshotConsumer);
        resourceMeterMaintainer.maintain();
        Map<ApplicationId, ResourceSnapshot> consumedResources = snapshotConsumer.consumedResources();

        assertEquals(2, consumedResources.size());

        ResourceSnapshot app1 = consumedResources.get(ApplicationId.from("tenant1", "app1", "default"));
        ResourceSnapshot app2 = consumedResources.get(ApplicationId.from("tenant2", "app2", "default"));

        assertEquals(24, app1.getResourceAllocation().getCpuCores(), DELTA);
        assertEquals(24, app1.getResourceAllocation().getMemoryGb(), DELTA);
        assertEquals(500, app1.getResourceAllocation().getDiskGb(), DELTA);

        assertEquals(40, app2.getResourceAllocation().getCpuCores(), DELTA);
        assertEquals(24, app2.getResourceAllocation().getMemoryGb(), DELTA);
        assertEquals(500, app2.getResourceAllocation().getDiskGb(), DELTA);

        assertEquals(tester.clock().millis()/1000, metrics.getMetric("metering_last_reported"));
        assertEquals(1112.0d, (Double) metrics.getMetric("metering_total_reported"), DELTA);
    }
}