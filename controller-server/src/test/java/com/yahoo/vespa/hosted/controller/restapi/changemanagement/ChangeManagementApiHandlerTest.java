// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.changemanagement;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeMembership;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeOwner;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.intellij.lang.annotations.Language;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChangeManagementApiHandlerTest extends ControllerContainerTest {

    private static final String responses = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/changemanagement/responses/";
    private static final AthenzIdentity operator = AthenzUser.fromUserId("operatorUser");

    private ContainerTester tester;

    @Before
    public void before() {
        tester = new ContainerTester(container, responses);
        addUserToHostedOperatorRole(operator);
        tester.serviceRegistry().configServer().nodeRepository().addNodes(ZoneId.from("prod.us-east-3"), createNodes());
    }

    @Test
    public void test_api() {
        assertFile(new Request("http://localhost:8080/changemanagement/v1/assessment", "{\"zone\":\"prod.us-east-3\", \"hosts\": [\"host1\"]}", Request.Method.POST), "initial.json");
    }

    private void assertResponse(Request request, @Language("JSON") String body, int statusCode) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, body, statusCode);
    }

    private void assertFile(Request request, String filename) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, new File(filename));
    }

    private List<NodeRepositoryNode> createNodes() {
        List<NodeRepositoryNode> nodes = new ArrayList<>();
        nodes.add(createNode("node1", "host1", "myapp", "default", 0 ));
        nodes.add(createNode("node2", "host1", "myapp", "default", 0 ));
        nodes.add(createNode("node3", "host1", "myapp", "default", 0 ));
        nodes.add(createNode("node4", "host2", "myapp", "default", 0 ));
        return nodes;
    }

    private NodeOwner createOwner(String tenant, String application, String instance) {
        NodeOwner owner = new NodeOwner();
        owner.tenant = tenant;
        owner.application = application;
        owner.instance = instance;
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

    private NodeRepositoryNode createNode(String nodename, String hostname, String appName, String clusterId, int group) {
        NodeRepositoryNode node = new NodeRepositoryNode();
        node.setHostname(nodename);
        node.setParentHostname(hostname);
        node.setState(NodeState.active);
        node.setOwner(createOwner("mytenant", appName, "default"));
        node.setMembership(createMembership(clusterId, group));

        return node;
    }
}
