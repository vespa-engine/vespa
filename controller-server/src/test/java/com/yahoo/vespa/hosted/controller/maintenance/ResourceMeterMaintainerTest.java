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

import static org.junit.Assert.assertEquals;

/**
 * @author olaa
 */
public class ResourceMeterMaintainerTest {

    private final ControllerTester tester = new ControllerTester();
    private final double DELTA = Double.MIN_VALUE;
    private MockMeteringClient snapshotConsumer = new MockMeteringClient();
    private MetricsMock metrics = new MetricsMock();

    @Test
    public void testMaintainer() {
        setUpZones();

        ResourceMeterMaintainer resourceMeterMaintainer = new ResourceMeterMaintainer(tester.controller(), Duration.ofMinutes(5), new JobControl(tester.curator()), metrics, snapshotConsumer);
        resourceMeterMaintainer.maintain();
        Collection<ResourceSnapshot> consumedResources = snapshotConsumer.consumedResources();

        // The mocked repository contains two applications, so we should also consume two ResourceSnapshots
        assertEquals(4, consumedResources.size());
        ResourceSnapshot app1 = consumedResources.stream().filter(snapshot -> snapshot.getApplicationId().equals(ApplicationId.from("tenant1", "app1", "default"))).findFirst().orElseThrow();
        ResourceSnapshot app2 = consumedResources.stream().filter(snapshot -> snapshot.getApplicationId().equals(ApplicationId.from("tenant2", "app2", "default"))).findFirst().orElseThrow();

        assertEquals(24, app1.getCpuCores(), DELTA);
        assertEquals(24, app1.getMemoryGb(), DELTA);
        assertEquals(500, app1.getDiskGb(), DELTA);

        assertEquals(40, app2.getCpuCores(), DELTA);
        assertEquals(24, app2.getMemoryGb(), DELTA);
        assertEquals(500, app2.getDiskGb(), DELTA);

        assertEquals(tester.clock().millis()/1000, metrics.getMetric("metering_last_reported"));
        assertEquals(2224.0d, (Double) metrics.getMetric("metering_total_reported"), DELTA);
    }

    private void setUpZones() {
        ZoneApiMock nonAwsZone = ZoneApiMock.newBuilder().withId("test.region-1").build();
        ZoneApiMock awsZone1 = ZoneApiMock.newBuilder().withId("prod.region-2").withCloud("aws").build();
        ZoneApiMock awsZone2 = ZoneApiMock.newBuilder().withId("test.region-3").withCloud("aws").build();
        tester.zoneRegistry().setZones(
                nonAwsZone,
                awsZone1,
                awsZone2);
        tester.configServer().nodeRepository().addFixedNodes(nonAwsZone.getId());
        tester.configServer().nodeRepository().addFixedNodes(awsZone1.getId());
        tester.configServer().nodeRepository().addFixedNodes(awsZone2.getId());
        tester.configServer().nodeRepository().addNodes(
                awsZone1.getId(),
                createNodesInState(
                        Node.State.provisioned,
                        Node.State.ready,
                        Node.State.dirty,
                        Node.State.failed,
                        Node.State.parked
                )
        );
    }

    private List<Node> createNodesInState(Node.State ...states) {
        return Arrays.stream(states)
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
                            .clusterType(Node.ClusterType.container)
                            .build();
                })
                .collect(Collectors.toUnmodifiableList());
    }
}
