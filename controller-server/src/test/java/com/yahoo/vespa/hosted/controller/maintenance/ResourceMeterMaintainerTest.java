// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMeteringClient;
import com.yahoo.vespa.hosted.controller.integration.MetricsMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * @author olaa
 */
public class ResourceMeterMaintainerTest {

    private final ControllerTester tester = new ControllerTester();
    private final MockMeteringClient snapshotConsumer = new MockMeteringClient();
    private final MetricsMock metrics = new MetricsMock();

    @Test
    public void testMaintainer() {
        setUpZones();
        ResourceMeterMaintainer resourceMeterMaintainer = new ResourceMeterMaintainer(tester.controller(), Duration.ofMinutes(5), metrics, snapshotConsumer);
        long lastRefreshTime = tester.clock().millis();
        tester.curator().writeMeteringRefreshTime(lastRefreshTime);
        resourceMeterMaintainer.maintain();
        Collection<ResourceSnapshot> consumedResources = snapshotConsumer.consumedResources();

        // The mocked repository contains two applications, so we should also consume two ResourceSnapshots
        assertEquals(4, consumedResources.size());
        ResourceSnapshot app1 = consumedResources.stream().filter(snapshot -> snapshot.getApplicationId().equals(ApplicationId.from("tenant1", "app1", "default"))).findFirst().orElseThrow();
        ResourceSnapshot app2 = consumedResources.stream().filter(snapshot -> snapshot.getApplicationId().equals(ApplicationId.from("tenant2", "app2", "default"))).findFirst().orElseThrow();

        assertEquals(24, app1.getCpuCores(), Double.MIN_VALUE);
        assertEquals(24, app1.getMemoryGb(), Double.MIN_VALUE);
        assertEquals(500, app1.getDiskGb(), Double.MIN_VALUE);

        assertEquals(40, app2.getCpuCores(), Double.MIN_VALUE);
        assertEquals(24, app2.getMemoryGb(), Double.MIN_VALUE);
        assertEquals(500, app2.getDiskGb(), Double.MIN_VALUE);

        assertEquals(tester.clock().millis()/1000, metrics.getMetric("metering_last_reported"));
        assertEquals(2224.0d, (Double) metrics.getMetric("metering_total_reported"), Double.MIN_VALUE);

        // Metering is not refreshed
        assertFalse(snapshotConsumer.isRefreshed());
        assertEquals(lastRefreshTime, tester.curator().readMeteringRefreshTime());

        var millisAdvanced = 3600 * 1000;
        tester.clock().advance(Duration.ofMillis(millisAdvanced));
        resourceMeterMaintainer.maintain();
        assertTrue(snapshotConsumer.isRefreshed());
        assertEquals(lastRefreshTime + millisAdvanced, tester.curator().readMeteringRefreshTime());
    }

    private void setUpZones() {
        ZoneApiMock nonAwsZone = ZoneApiMock.newBuilder().withId("test.region-1").build();
        ZoneApiMock awsZone1 = ZoneApiMock.newBuilder().withId("prod.region-2").withCloud("aws").build();
        ZoneApiMock awsZone2 = ZoneApiMock.newBuilder().withId("test.region-3").withCloud("aws").build();
        tester.zoneRegistry().setZones(
                nonAwsZone,
                awsZone1,
                awsZone2);
        tester.configServer().nodeRepository().setFixedNodes(nonAwsZone.getId());
        tester.configServer().nodeRepository().setFixedNodes(awsZone1.getId());
        tester.configServer().nodeRepository().setFixedNodes(awsZone2.getId());
        tester.configServer().nodeRepository().putNodes(
                awsZone1.getId(),
                createNodes()
        );
    }

    private List<Node> createNodes() {
        return Stream.of(Node.State.provisioned,
                         Node.State.ready,
                         Node.State.dirty,
                         Node.State.failed,
                         Node.State.parked,
                         Node.State.active)
                     .map(state -> {
                         return new Node.Builder()
                                 .hostname(HostName.from("host" + state))
                                 .parentHostname(HostName.from("parenthost" + state))
                                 .state(state)
                                 .type(NodeType.tenant)
                                 .owner(ApplicationId.from("tenant1", "app1", "default"))
                                 .currentVersion(Version.fromString("7.42"))
                                 .wantedVersion(Version.fromString("7.42"))
                                 .currentOsVersion(Version.fromString("7.6"))
                                 .wantedOsVersion(Version.fromString("7.6"))
                                 .serviceState(Node.ServiceState.expectedUp)
                                 .resources(new NodeResources(24, 24, 500, 1))
                                 .clusterId("clusterA")
                                 .clusterType(state == Node.State.active ? Node.ClusterType.admin : Node.ClusterType.container)
                                 .build();
                     })
                     .collect(Collectors.toUnmodifiableList());
    }
}
