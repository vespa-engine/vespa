// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.integration.resource.CostReportConsumerMock;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceAllocation;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author ldalves
 */
public class CostReportMaintainerTest {

    private final ControllerTester tester = new ControllerTester();

    @Test
    void maintain() {
        tester.clock().setInstant(Instant.EPOCH);
        tester.zoneRegistry().setZones(
                ZoneApiMock.newBuilder().withId("prod.us-east-3").withCloud("yahoo").build(),
                ZoneApiMock.newBuilder().withId("prod.us-west-1").withCloud("yahoo").build(),
                ZoneApiMock.newBuilder().withId("prod.us-central-1").withCloud("yahoo").build(),
                ZoneApiMock.newBuilder().withId("prod.eu-west-1").withCloud("yahoo").build());
        addNodes();

        CostReportConsumerMock costReportConsumer = new CostReportConsumerMock(
                (csv) -> assertEquals(
                        "Date,Property,Reserved Cpu Cores,Reserved Memory GB,Reserved Disk Space GB,Usage Fraction\n" +
                                "1970-01-01,Property1,96.0,96.0,2000.0,0.3055555555555555\n" +
                                "1970-01-01,Property3,128.0,96.0,2000.0,0.3333333333333333\n" +
                                "1970-01-01,Property2,160.0,96.0,2000.0,0.3611111111111111",
                        csv),
                Map.of(new Property("Property3"), new ResourceAllocation(256, 192, 4000, NodeResources.Architecture.getDefault()))
        );


        tester.createTenant("tenant1", "app1", 1L);
        tester.createTenant("tenant2", "app2", 2L);
        CostReportMaintainer maintainer = new CostReportMaintainer(
                tester.controller(),
                Duration.ofDays(1),
                costReportConsumer
        );
        maintainer.maintain();
    }

    private void addNodes() {
        for (var zone : tester.zoneRegistry().zones().all().zones()) {
            tester.configServer().nodeRepository().addFixedNodes(zone.getId());
        }
    }

}
