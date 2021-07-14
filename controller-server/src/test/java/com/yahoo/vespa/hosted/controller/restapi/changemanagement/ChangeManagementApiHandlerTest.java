// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.changemanagement;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeMembership;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeOwner;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeType;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.HostAction;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.intellij.lang.annotations.Language;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ChangeManagementApiHandlerTest extends ControllerContainerTest {

    private static final String responses = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/changemanagement/responses/";
    private static final AthenzIdentity operator = AthenzUser.fromUserId("operatorUser");
    private static final String changeRequestId = "id123";

    private ContainerTester tester;

    @Before
    public void before() {
        tester = new ContainerTester(container, responses);
        addUserToHostedOperatorRole(operator);
        tester.serviceRegistry().configServer().nodeRepository().addNodes(ZoneId.from("prod.us-east-3"), createNodes());
        tester.serviceRegistry().configServer().nodeRepository().putNodes(ZoneId.from("prod.us-east-3"), createNode());
        tester.controller().curator().writeChangeRequest(createChangeRequest());

    }

    @Test
    public void test_api() {
        assertFile(new Request("http://localhost:8080/changemanagement/v1/assessment", "{\"zone\":\"prod.us-east-3\", \"hosts\": [\"host1\"]}", Request.Method.POST), "initial.json");
        assertFile(new Request("http://localhost:8080/changemanagement/v1/assessment", "{\"zone\":\"prod.us-east-3\", \"switches\": [\"switch1\"]}", Request.Method.POST), "initial.json");
        assertFile(new Request("http://localhost:8080/changemanagement/v1/vcmr"), "vcmrs.json");
    }

    @Test
    public void deletes_vcmr() {
        assertEquals(1, tester.controller().curator().readChangeRequests().size());
        assertFile(new Request("http://localhost:8080/changemanagement/v1/vcmr/" + changeRequestId, "", Request.Method.DELETE), "vcmr.json");
        assertEquals(0, tester.controller().curator().readChangeRequests().size());
    }

    @Test
    public void get_vcmr() {
        assertFile(new Request("http://localhost:8080/changemanagement/v1/vcmr/" + changeRequestId, "", Request.Method.GET), "vcmr.json");
    }

    @Test
    public void patch_vcmr() {
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

    private void assertResponse(Request request, @Language("JSON") String body, int statusCode) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, body, statusCode);
    }

    private void assertFile(Request request, String filename) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, new File(filename));
    }

    private Node createNode() {
        return new Node.Builder()
                .hostname(HostName.from("host1"))
                .switchHostname("switch1")
                .build();
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

    private List<NodeRepositoryNode> createNodes() {
        List<NodeRepositoryNode> nodes = new ArrayList<>();
        nodes.add(createNode("node1", "host1", "default", 0 ));
        nodes.add(createNode("node2", "host1", "default", 0 ));
        nodes.add(createNode("node3", "host1", "default", 0 ));
        nodes.add(createNode("node4", "host2", "default", 0 ));
        nodes.add(createHost("host1", "switch1"));
        nodes.add(createHost("host2", "switch2"));
        return nodes;
    }

    private NodeOwner createOwner() {
        NodeOwner owner = new NodeOwner();
        owner.tenant = "mytenant";
        owner.application = "myapp";
        owner.instance = "default";
        return owner;
    }

    private NodeMembership createMembership(String clusterId, int group) {
        NodeMembership membership = new NodeMembership();
        membership.group = "" + group;
        membership.clusterid = clusterId;
        membership.clustertype = "content";
        membership.index = 2;
        membership.retired = false;
        return membership;
    }

    private NodeRepositoryNode createNode(String nodename, String hostname, String clusterId, int group) {
        NodeRepositoryNode node = new NodeRepositoryNode();
        node.setHostname(nodename);
        node.setParentHostname(hostname);
        node.setState(NodeState.active);
        node.setOwner(createOwner());
        node.setMembership(createMembership(clusterId, group));
        node.setType(NodeType.tenant);

        return node;
    }

    private NodeRepositoryNode createHost(String hostname, String switchName) {
        NodeRepositoryNode node = new NodeRepositoryNode();
        node.setHostname(hostname);
        node.setSwitchHostname(switchName);
        node.setOwner(createOwner());
        node.setType(NodeType.host);
        node.setMembership(createMembership("host", 0));
        return node;
    }

}
