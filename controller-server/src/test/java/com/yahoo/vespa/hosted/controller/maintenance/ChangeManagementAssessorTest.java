package com.yahoo.vespa.hosted.controller.maintenance;


import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeMembership;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeOwner;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChangeManagementAssessorTest {

    @Test
    public void empty_input_variations() {
        ZoneId zone = ZoneId.from("prod", "eu-trd");
        List<String> hostNames = new ArrayList<>();
        List<NodeRepositoryNode> allNodesInZone = new ArrayList<>();

        // Both zone and hostnames are empty
        List<ChangeManagementAssessor.Assessment> assessments
                = ChangeManagementAssessor.assessmentInner(hostNames, allNodesInZone, zone);
        Assert.assertEquals(0, assessments.size());
    }

    @Test
    public void one_host_one_cluster_no_groups() {
        ZoneId zone = ZoneId.from("prod", "eu-trd");
        List<String> hostNames = Collections.singletonList("host1");
        List<NodeRepositoryNode> allNodesInZone = new ArrayList<>();
        allNodesInZone.add(createNode("node1", "host1", "myapp", "default", 0 ));
        allNodesInZone.add(createNode("node2", "host1", "myapp", "default", 0 ));
        allNodesInZone.add(createNode("node3", "host1", "myapp", "default", 0 ));

        // Add an not impacted hosts
        allNodesInZone.add(createNode("node4", "host2", "myapp", "default", 0 ));

        // Make Assessment
        List<ChangeManagementAssessor.Assessment> assessments
                = ChangeManagementAssessor.assessmentInner(hostNames, allNodesInZone, zone);

        // Assess the assessment :-o
        Assert.assertEquals(1, assessments.size());
        Assert.assertEquals(3, assessments.get(0).clusterImpact);
        Assert.assertEquals(4, assessments.get(0).clusterSize);
        Assert.assertEquals(1, assessments.get(0).groupsImpact);
        Assert.assertEquals(1, assessments.get(0).groupsTotal);
        Assert.assertEquals("content:default", assessments.get(0).cluster);
        Assert.assertEquals("mytenant:myapp:default", assessments.get(0).app);
        Assert.assertEquals("prod.eu-trd", assessments.get(0).zone);
    }

    @Test
    public void one_of_two_groups_in_one_of_two_clusters() {
        ZoneId zone = ZoneId.from("prod", "eu-trd");
        List<String> hostNames = Arrays.asList("host1", "host2");
        List<NodeRepositoryNode> allNodesInZone = new ArrayList<>();

        // Two impacted nodes on host1
        allNodesInZone.add(createNode("node1", "host1", "myapp", "default", 0 ));
        allNodesInZone.add(createNode("node2", "host1", "myapp", "default", 0 ));

        // One impacted nodes on host2
        allNodesInZone.add(createNode("node3", "host2", "myapp", "default", 0 ));

        // Another group on hosts not impacted
        allNodesInZone.add(createNode("node4", "host3", "myapp", "default", 1 ));
        allNodesInZone.add(createNode("node5", "host3", "myapp", "default", 1 ));
        allNodesInZone.add(createNode("node6", "host3", "myapp", "default", 1 ));

        // Another cluster on hosts not impacted - this one also with three different groups (should all be ignored here)
        allNodesInZone.add(createNode("node4", "host4", "myapp", "myman", 4 ));
        allNodesInZone.add(createNode("node5", "host4", "myapp", "myman", 5 ));
        allNodesInZone.add(createNode("node6", "host4", "myapp", "myman", 6 ));

        // Make Assessment
        List<ChangeManagementAssessor.Assessment> assessments
                = ChangeManagementAssessor.assessmentInner(hostNames, allNodesInZone, zone);

        // Assess the assessment :-o
        Assert.assertEquals(1, assessments.size()); //One cluster is impacted
        Assert.assertEquals(3, assessments.get(0).clusterImpact);
        Assert.assertEquals(6, assessments.get(0).clusterSize);
        Assert.assertEquals(1, assessments.get(0).groupsImpact);
        Assert.assertEquals(2, assessments.get(0).groupsTotal);
        Assert.assertEquals("content:default", assessments.get(0).cluster);
        Assert.assertEquals("mytenant:myapp:default", assessments.get(0).app);
        Assert.assertEquals("prod.eu-trd", assessments.get(0).zone);
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