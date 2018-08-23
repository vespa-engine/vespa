// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.vespa.hosted.controller.api.integration.noderepository.MaintenanceJobList;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeList;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeMembership;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author bjorncs
 */
public class NodeRepositoryClientMock implements NodeRepositoryClientInterface {

    @Override
    public void addNodes(ZoneId zone, Collection<NodeRepositoryNode> nodes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeRepositoryNode getNode(ZoneId zone, String hostname) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteNode(ZoneId zone, String hostname) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNode(ZoneId zone, String hostname)  {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeList listNodes(ZoneId zone, boolean recursive) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeList listNodes(ZoneId zone, String tenant, String applicationId, String instance) {
        NodeRepositoryNode nodeA = createNodeA();
        NodeRepositoryNode nodeB = createNodeB();
        return new NodeList(Arrays.asList(nodeA, nodeB));
    }

    private static NodeRepositoryNode createNodeA() {
        NodeRepositoryNode node = new NodeRepositoryNode();
        node.setHostname("hostA");
        node.setCost(10);
        node.setFlavor("C-2B/24/500");
        NodeMembership membership = new NodeMembership();
        membership.clusterid = "clusterA";
        membership.clustertype = "container";
        node.setMembership(membership);
        return node;
    }

    private static NodeRepositoryNode createNodeB() {
        NodeRepositoryNode node = new NodeRepositoryNode();
        node.setHostname("hostB");
        node.setCost(20);
        node.setFlavor("C-2C/24/500");
        NodeMembership membership = new NodeMembership();
        membership.clusterid = "clusterB";
        membership.clustertype = "content";
        node.setMembership(membership);
        return node;
    }

    @Override
    public String resetFailureInformation(ZoneId zone, String nodename) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String restart(ZoneId zone, String nodename) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String reboot(ZoneId zone, String nodename) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String cancelReboot(ZoneId zone, String nodename) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String wantTo(ZoneId zone, String nodename, WantTo... actions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String cancelRestart(ZoneId zone, String nodename) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String setHardwareFailureDescription(ZoneId zone, String nodename, String hardwareFailureDescription) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setState(ZoneId zone, NodeState nodeState, String nodename) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String enableMaintenanceJob(ZoneId zone, String jobName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String disableMaintenanceJob(ZoneId zone, String jobName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MaintenanceJobList listMaintenanceJobs(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

}
