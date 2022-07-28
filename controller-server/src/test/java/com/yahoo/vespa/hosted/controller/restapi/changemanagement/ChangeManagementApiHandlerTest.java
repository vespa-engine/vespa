// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.changemanagement;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.HostAction;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChangeManagementApiHandlerTest extends ControllerContainerTest {

    private static final String responses = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/changemanagement/responses/";
    private static final AthenzIdentity operator = AthenzUser.fromUserId("operatorUser");
    private static final String changeRequestId = "id123";

    private ContainerTester tester;

    @BeforeEach
    public void before() {
        tester = new ContainerTester(container, responses);
        addUserToHostedOperatorRole(operator);
        tester.serviceRegistry().configServer().nodeRepository().putNodes(ZoneId.from("prod.us-east-3"), createNodes());
        tester.controller().curator().writeChangeRequest(createChangeRequest());

    }

    @Test
    void test_api() {
        assertFile(new Request("http://localhost:8080/changemanagement/v1/assessment", "{\"zone\":\"prod.us-east-3\", \"hosts\": [\"host1\"]}", Request.Method.POST), "initial.json");
        assertFile(new Request("http://localhost:8080/changemanagement/v1/assessment", "{\"zone\":\"prod.us-east-3\", \"switches\": [\"switch1\"]}", Request.Method.POST), "initial.json");
        assertFile(new Request("http://localhost:8080/changemanagement/v1/vcmr"), "vcmrs.json");
    }

    @Test
    void deletes_vcmr() {
        assertEquals(1, tester.controller().curator().readChangeRequests().size());
        assertFile(new Request("http://localhost:8080/changemanagement/v1/vcmr/" + changeRequestId, "", Request.Method.DELETE), "vcmr.json");
        assertEquals(0, tester.controller().curator().readChangeRequests().size());
    }

    @Test
    void get_vcmr() {
        assertFile(new Request("http://localhost:8080/changemanagement/v1/vcmr/" + changeRequestId, "", Request.Method.GET), "vcmr.json");
    }

    @Test
    void patch_vcmr() {
        var payload = "{" +
                "\"approval\": \"REJECTED\"," +
                "\"status\": \"COMPLETED\"," +
                "\"actionPlan\": {" +
                "   \"hosts\": [{" +
                "       \"hostname\": \"host1\"," +
                "       \"state\": \"REQUIRES_OPERATOR_ACTION\"," +
                "       \"lastUpdated\": \"2021-05-10T14:08:15Z\"" +
                "}]}" +
                "}";
        assertFile(new Request("http://localhost:8080/changemanagement/v1/vcmr/" + changeRequestId, payload, Request.Method.PATCH), "patched-vcmr.json");
        var changeRequest = tester.controller().curator().readChangeRequest(changeRequestId).orElseThrow();
        assertEquals(ChangeRequest.Approval.REJECTED, changeRequest.getApproval());
        assertEquals(VespaChangeRequest.Status.COMPLETED, changeRequest.getStatus());
    }

    private void assertFile(Request request, String filename) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, new File(filename));
    }

    private VespaChangeRequest createChangeRequest() {
        var instant = Instant.ofEpochMilli(9001);
        var date = ZonedDateTime.ofInstant(instant, java.time.ZoneId.of("UTC"));
        var source = new ChangeRequestSource("aws", "id321", "url", ChangeRequestSource.Status.STARTED, date, date);
        var actionPlan = List.of(
                new HostAction("host1", HostAction.State.RETIRING, instant),
                new HostAction("host2", HostAction.State.RETIRED, instant)
        );

        return new VespaChangeRequest(
                changeRequestId,
                source,
                List.of("switch1"),
                List.of("host1", "host2"),
                ChangeRequest.Approval.APPROVED,
                ChangeRequest.Impact.VERY_HIGH,
                VespaChangeRequest.Status.IN_PROGRESS,
                actionPlan,
                ZoneId.defaultId()
        );
    }

    private List<Node> createNodes() {
        List<Node> nodes = new ArrayList<>();
        nodes.add(createNode("node1", "host1", "default", 0 ));
        nodes.add(createNode("node2", "host1", "default", 0 ));
        nodes.add(createNode("node3", "host1", "default", 0 ));
        nodes.add(createNode("node4", "host2", "default", 0 ));
        nodes.add(createHost("host1", "switch1"));
        nodes.add(createHost("host2", "switch2"));
        return nodes;
    }

    private Node createNode(String nodename, String hostname, String clusterId, int group) {
        return Node.builder()
                   .hostname(nodename)
                   .parentHostname(hostname).state(Node.State.active)
                   .owner(ApplicationId.from("mytenant", "myapp", "default"))
                   .type(com.yahoo.config.provision.NodeType.tenant)
                   .clusterId(clusterId)
                   .group(String.valueOf(group))
                   .clusterType(Node.ClusterType.content)
                   .build();
    }

    private Node createHost(String hostname, String switchName) {
        return Node.builder()
                   .hostname(hostname)
                   .switchHostname(switchName)
                   .owner(ApplicationId.from("mytenant", "myapp", "default"))
                   .type(com.yahoo.config.provision.NodeType.host)
                   .clusterId("host")
                   .group("0")
                   .build();
    }

}
