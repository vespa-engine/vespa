// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.legacy;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Configuration;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class ProvisionResourceTest {

    NodeRepository nodeRepository;
    NodeFlavors nodeFlavors;
    ProvisionResource provisionResource;
    Curator curator;
    int capacity = 2;
    ApplicationId application;
    private NodeRepositoryProvisioner provisioner;

    @Before
    public void setUpTest() throws Exception {
        curator = new MockCurator();
        nodeFlavors = FlavorConfigBuilder.createDummies("default");
        nodeRepository = new NodeRepository(nodeFlavors, curator);
        provisionResource = new ProvisionResource(nodeRepository, nodeFlavors);
        application = ApplicationId.from(TenantName.from("myTenant"), ApplicationName.from("myApplication"), InstanceName.from("default"));
        provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, Zone.defaultZone());
    }

    private void createNodesInRepository(int readyCount, int provisionedCount) {
        List<Node> readyNodes = new ArrayList<>();
        for (HostInfo hostInfo : createHostInfos(readyCount, 0))
            readyNodes.add(nodeRepository.createNode(hostInfo.openStackId, hostInfo.hostname,
                    Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default"))));
        readyNodes = nodeRepository.addNodes(readyNodes);
        nodeRepository.setReady(readyNodes);

        List<Node> provisionedNodes = new ArrayList<>();
        for (HostInfo hostInfo : createHostInfos(provisionedCount, readyCount))
            provisionedNodes.add(nodeRepository.createNode(hostInfo.openStackId, hostInfo.hostname,
                    Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default"))));
        nodeRepository.addNodes(provisionedNodes);
    }

    @Test
    public void test_node_allocation() {
        createNodesInRepository(10, 0);
        List<Node> assignments = assignNode(application, capacity);
        assertEquals(2, assignments.size());
    }

    @Test
    public void test_node_reallocation() {
        createNodesInRepository(10, 0);
        List<Node> assignments1 = assignNode(application, capacity);
        List<Node> assignments2 = assignNode(application, capacity);

        assertEquals(assignments2.size(), assignments1.size());
    }

    @Test
    public void test_node_reallocation_add_hostalias() {
        createNodesInRepository(5, 0);

        List<Node> assignments1 = assignNode(application, 2);
        List<Node> assignments2 = assignNode(application, 3);

        assertEquals(assignments2.size(), assignments1.size() + 1);
    }

    @Test
    public void test_node_allocation_remove_hostalias() {
        createNodesInRepository(10, 0);

        List<Node> assignments1 = assignNode(application, 3, ClusterSpec.Type.container);
        List<Node> assignments2 = assignNode(application, 2, ClusterSpec.Type.container);

        assertEquals(assignments2.size(), assignments1.size() - 1);
        ProvisionStatus provisionStatus = provisionResource.getStatus();
        assertEquals(1, provisionStatus.decomissionNodes.size());
    }

    @Test
    public void test_recycle_deallocated() {
        createNodesInRepository(2, 0);
        assignNode(application, 2);
        nodeRepository.deactivate(application);
        List<Node> nodes = nodeRepository.deallocate(nodeRepository.getNodes(application, Node.State.inactive));
        assertEquals(0, nodeRepository.getNodes(Node.State.ready).size());
        assertEquals(2, nodeRepository.getNodes(Node.State.dirty).size());
        provisionResource.setReady(nodes.get(0).hostname());
        provisionResource.setReady(nodes.get(1).hostname());
        assertEquals(2, nodeRepository.getNodes(Node.State.ready).size());
        assertEquals(0, nodeRepository.getNodes(Node.State.dirty).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_ready_node_unknown() {
        provisionResource.setReady("does.not.exist");
    }

    private List<HostInfo> createHostInfos(int count, int startIndex) {
        String format = "node%d";
        List<HostInfo> hostInfos = new ArrayList<>();
        for (int i = 0; i < count; ++i)
            hostInfos.add(HostInfo.createHostInfo(String.format(format, i + startIndex), UUID.randomUUID().toString(), "medium"));
        return hostInfos;
    }

    private List<Node> assignNode(ApplicationId applicationId, int capacity) {
        return assignNode(applicationId, capacity, ClusterSpec.Type.content);
    }

    private List<Node> assignNode(ApplicationId applicationId, int capacity, ClusterSpec.Type type) {
        ClusterSpec cluster = ClusterSpec.from(type, ClusterSpec.Id.from("test"), Optional.empty());
        List<HostSpec> hosts = provisioner.prepare(applicationId, cluster, Capacity.fromNodeCount(capacity), 1, null);
        NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
        provisioner.activate(transaction, applicationId, hosts);
        transaction.commit();
        return nodeRepository.getNodes(applicationId, Node.State.active);
    }

}
