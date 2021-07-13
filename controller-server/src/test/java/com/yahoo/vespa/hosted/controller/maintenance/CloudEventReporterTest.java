// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.aws.CloudEvent;
import com.yahoo.vespa.hosted.controller.api.integration.aws.MockAwsEventFetcher;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author olaa
 */
public class CloudEventReporterTest {

    private final ControllerTester tester = new ControllerTester();
    private final ZoneApiMock unsupportedZone = createZone("prod.zone3", "region-1", "other");
    private final ZoneApiMock zone1 = createZone("prod.zone1", "region-1", "aws");
    private final ZoneApiMock zone2 = createZone("prod.zone2", "region-2", "aws");


    /**
     * Test scenario: Consider three zones, two of which are supported
     *
     * We want to test the following:
     * 1. Unsupported zone is completely ignored
     * 2. Hosts affected by cloud event are deprovisioned
     */
    @Test
    public void maintain() {
        setUpZones();
        CloudEventReporter cloudEventReporter = new CloudEventReporter(tester.controller(), Duration.ofMinutes(15));
        assertEquals(Set.of("host1.com", "host2.com", "host3.com"), getHostnames(unsupportedZone.getId()));
        assertEquals(Set.of("host1.com", "host2.com", "host3.com"), getHostnames(zone1.getId()));
        assertEquals(Set.of("host4.com", "host5.com", "confighost.com"), getHostnames(zone2.getId()));

        mockEvents();
        cloudEventReporter.maintain();
        assertEquals(Set.of("host1.com", "host2.com", "host3.com"), getHostnames(unsupportedZone.getId()));
        assertEquals(Set.of("host3.com"), getHostnames(zone1.getId()));
        assertEquals(Set.of("host4.com"), getHostnames(zone2.getId()));
    }

    private void mockEvents() {
        MockAwsEventFetcher eventFetcher = (MockAwsEventFetcher) tester.controller().serviceRegistry().eventFetcherService();

        Date date = new Date();
        CloudEvent event1 = new CloudEvent("event 1",
                "instance code",
                "description",
                date,
                date,
                date,
                "region-1",
                Set.of("host1", "host2"));

        CloudEvent event2 = new CloudEvent("event 2",
                "instance code",
                "description",
                date,
                date,
                date,
                "region-2",
                Set.of("host5", "confighost"));

        eventFetcher.addEvent("region-1", event1);
        eventFetcher.addEvent("region-2", event2);
    }

    private void setUpZones() {
        tester.zoneRegistry().setZones(
                unsupportedZone,
                zone1,
                zone2);

        tester.configServer().nodeRepository().putNodes(
                unsupportedZone.getId(),
                createNodesWithHostnames(
                        "host1.com",
                        "host2.com",
                        "host3.com"
                )
        );
        tester.configServer().nodeRepository().putNodes(
                zone1.getId(),
                createNodesWithHostnames(
                        "host1.com",
                        "host2.com",
                        "host3.com"
                )
        );
        tester.configServer().nodeRepository().putNodes(
                zone2.getId(),
                createNodesWithHostnames(
                        "host4.com",
                        "host5.com"
                )
        );
        tester.configServer().nodeRepository().putNodes(
                zone2.getId(),
                List.of(createNode("confighost.com", NodeType.confighost))
        );
    }

    private List<Node> createNodesWithHostnames(String... hostnames) {
        return Arrays.stream(hostnames)
                .map(hostname -> createNode(hostname, NodeType.host))
                .collect(Collectors.toUnmodifiableList());
    }

    private Node createNode(String hostname, NodeType nodeType) {
        return Node.builder()
                   .hostname(HostName.from(hostname))
                   .type(nodeType)
                   .build();
    }

    private Set<String> getHostnames(ZoneId zoneId) {
        return tester.configServer().nodeRepository().list(zoneId, false)
                .stream()
                .map(node -> node.hostname().value())
                .collect(Collectors.toSet());
    }

    private ZoneApiMock createZone(String zoneId, String cloudNativeRegionName, String cloud) {
        return ZoneApiMock.newBuilder().withId(zoneId)
                .withCloudNativeRegionName(cloudNativeRegionName)
                .withCloud(cloud)
                .build();
    }

}
