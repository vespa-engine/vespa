package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.aws.CloudEvent;
import com.yahoo.vespa.hosted.controller.api.integration.aws.MockAwsEventFetcher;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MockIssueHandler;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * @author olaa
 */
public class CloudEventReporterTest {

    private ControllerTester tester = new ControllerTester();
    private ZoneApiMock nonAwsZone = createZone("prod.zone3", "region-1", "other");
    private ZoneApiMock awsZone1 = createZone("prod.zone1", "region-1", "aws");
    private ZoneApiMock awsZone2 = createZone("prod.zone2", "region-2", "aws");


    /**
     * Test scenario:
     * Consider three zones, two of which are based in AWS
     * We want to test the following:
     * 1. Non-AWS zone is completely ignored
     * 2. Tenant hosts affected by cloud event are deprovisioned
     * 3. Infrastructure hosts affected by cloud event are reported by IssueHandler
     */
    @Test
    public void maintain() {
        setUpZones();
        CloudEventReporter cloudEventReporter = new CloudEventReporter(tester.controller(), Duration.ofMinutes(15), new JobControl(tester.curator()));

        assertEquals(Set.of("host1.com", "host2.com", "host3.com"), getHostnames(nonAwsZone.getId()));
        assertEquals(Set.of("host1.com", "host2.com", "host3.com"), getHostnames(awsZone1.getId()));
        assertEquals(Set.of("host4.com", "host5.com", "confighost.com"), getHostnames(awsZone2.getId()));

        mockEvents();
        cloudEventReporter.maintain();

        assertEquals(Set.of("host1.com", "host2.com", "host3.com"), getHostnames(nonAwsZone.getId()));
        assertEquals(Set.of("host3.com"), getHostnames(awsZone1.getId()));
        assertEquals(Set.of("host4.com", "confighost.com"), getHostnames(awsZone2.getId()));

        Map<IssueId, MockIssueHandler.MockIssue> createdIssues = tester.serviceRegistry().issueHandler().issues();
        assertEquals(1, createdIssues.size());
        String description = createdIssues.get(IssueId.from("1")).issue().description();
        assertTrue(description.contains("confighost"));

    }

    private void mockEvents() {
        MockAwsEventFetcher mockAwsEventFetcher = (MockAwsEventFetcher)tester.controller().serviceRegistry().eventFetcherService();

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

        mockAwsEventFetcher.addEvent("region-1", event1);
        mockAwsEventFetcher.addEvent("region-2", event2);
    }

    private void setUpZones() {

        tester.zoneRegistry().setZones(
                nonAwsZone,
                awsZone1,
                awsZone2);

        tester.configServer().nodeRepository().putByHostname(
                nonAwsZone.getId(),
                createNodesWithHostnames(
                        "host1.com",
                        "host2.com",
                        "host3.com"
                )
        );
        tester.configServer().nodeRepository().putByHostname(
                awsZone1.getId(),
                createNodesWithHostnames(
                        "host1.com",
                        "host2.com",
                        "host3.com"
                )
        );
        tester.configServer().nodeRepository().putByHostname(
                awsZone2.getId(),
                createNodesWithHostnames(
                        "host4.com",
                        "host5.com"
                )
        );
        tester.configServer().nodeRepository().putByHostname(
                awsZone2.getId(),
                List.of(createNode("confighost.com", NodeType.confighost))
        );
    }

    private List<Node> createNodesWithHostnames(String... hostnames) {
        return Arrays.stream(hostnames)
                .map(hostname -> createNode(hostname, NodeType.host))
                .collect(Collectors.toUnmodifiableList());
    }

    private Node createNode(String hostname, NodeType nodeType) {
        return new Node.Builder()
                .hostname(HostName.from(hostname))
                .type(nodeType)
                .build();
    }

    private Set<String> getHostnames(ZoneId zoneId) {
        return tester.configServer().nodeRepository().list(zoneId)
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