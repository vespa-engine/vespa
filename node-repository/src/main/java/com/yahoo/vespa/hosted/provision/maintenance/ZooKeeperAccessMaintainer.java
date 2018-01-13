// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Maintains the list of hosts that should be allowed to access ZooKeeper in this runtime.
 * These are the zookeeper servers and all nodes in node repository. This is maintained in the background
 * because nodes could be added or removed on another server.
 * 
 * We could limit access to the <i>active</i> subset of nodes, but that 
 * does not seem to have any particular operational or security benefits and might make it more problematic
 * for this job to be behind actual changes to the active set of nodes.
 * 
 * @author bratseth
 */
public class ZooKeeperAccessMaintainer extends Maintainer {

    private final Curator curator;
    
    public ZooKeeperAccessMaintainer(NodeRepository nodeRepository, Curator curator, Duration maintenanceInterval, 
                                     JobControl jobControl) {
        super(nodeRepository, maintenanceInterval, jobControl);
        this.curator = curator;
    }

    @Override
    protected void maintain() {
        Set<String> hosts = new HashSet<>();

        for (Node node : nodeRepository().getNodes())
            hosts.add(node.hostname());

        if ( ! hosts.isEmpty()) { // no nodes -> not a hosted instance: Pass an empty list to deactivate restriction
            for (String hostPort : curator.zooKeeperEnsembleConnectionSpec().split(","))
                hosts.add(hostPort.split(":")[0]);
        }

        ZooKeeperServer.setAllowedClientHostnames(hosts);
    }

}
