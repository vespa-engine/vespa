// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.aws.MockResourceTagger;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author olaa
 */
public class ResourceTagMaintainerTest {

    final ControllerTester tester = new ControllerTester();

    @Test
    public void maintain() {
        setUpZones();
        MockResourceTagger mockResourceTagger = new MockResourceTagger();
        ResourceTagMaintainer resourceTagMaintainer = new ResourceTagMaintainer(tester.controller(),
                                                                                Duration.ofMinutes(5),
                                                                                mockResourceTagger);
        resourceTagMaintainer.maintain();
        assertEquals(2, mockResourceTagger.getValues().size());
        Map<HostName, Optional<ApplicationId>> applicationForHost = mockResourceTagger.getValues().get(ZoneId.from("prod.region-2"));
        assertEquals(ApplicationId.from("t1", "a1", "i1"), applicationForHost.get(HostName.from("parentHostA.prod.region-2")).get());
        assertEquals(Optional.empty(), applicationForHost.get(HostName.from("parentHostB.prod.region-2")));
    }

    private void setUpZones() {
        ZoneApiMock nonAwsZone = ZoneApiMock.newBuilder().withId("test.region-1").build();
        ZoneApiMock awsZone1 = ZoneApiMock.newBuilder().withId("prod.region-2").withCloud("aws").build();
        ZoneApiMock awsZone2 = ZoneApiMock.newBuilder().withId("test.region-3").withCloud("aws").build();
        tester.zoneRegistry().setZones(nonAwsZone, awsZone1, awsZone2);
        setNodes(awsZone1.getId());
        setNodes(nonAwsZone.getId());
    }

    public void setNodes(ZoneId zone) {
        var hostA = new Node.Builder()
                .hostname(HostName.from("parentHostA." + zone.value()))
                .type(NodeType.host)
                .owner(ApplicationId.from("hosted-vespa", "tenant-host", "default"))
                .exclusiveTo(ApplicationId.from("t1", "a1", "i1"))
                .build();
        var nodeA = new Node.Builder()
                .hostname(HostName.from("hostA." + zone.value()))
                .type(NodeType.tenant)
                .parentHostname(HostName.from("parentHostA." + zone.value()))
                .owner(ApplicationId.from("tenant1", "app1", "default"))
                .build();
        var hostB = new Node.Builder()
                .hostname(HostName.from("parentHostB." + zone.value()))
                .type(NodeType.host)
                .owner(ApplicationId.from("hosted-vespa", "tenant-host", "default"))
                .build();
        tester.configServer().nodeRepository().setNodes(zone, List.of(hostA, nodeA, hostB));
    }

}
