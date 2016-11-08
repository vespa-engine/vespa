package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * Maintains the system property which tells ZooKeeper which nodes should have access to it.
 * These are the zokeeper servers and all tenant and proxy nodes. This is maintained in the background because
 * nodes could be added or removed on another server. 
 * 
 * We could limit access to the <i>active</i> subset of nodes, but that 
 * does not seem to have any particular operational or security benefits and might make it more problematic
 * for this job to be behind actual changes to the active set of nodes.
 * 
 * @author bratseth
 */
public class ZooKeeperAccessMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(ZooKeeperAccessMaintainer.class.getName());
    private final Curator curator;
    
    public ZooKeeperAccessMaintainer(NodeRepository nodeRepository, Curator curator, Duration maintenanceInterval) {
        super(nodeRepository, maintenanceInterval);
        this.curator = curator;
    }
    
    @Override
    protected void maintain() {
        StringBuilder hostList = new StringBuilder();

        for (Node node : nodeRepository().getNodes(NodeType.tenant))
            hostList.append(node.hostname()).append(",");
        for (Node node : nodeRepository().getNodes(NodeType.proxy))
            hostList.append(node.hostname()).append(",");
        for (String hostPort : curator.connectionSpec().split(","))
            hostList.append(hostPort.split(":")[0]).append(",");

        if (hostList.length() > 0)
            hostList.setLength(hostList.length()-1); // remove last comma

        log.fine("On " + Runtime.getRuntime().toString()+ ": Setting " + ZooKeeperServer.ZOOKEEPER_VESPA_CLIENTS_PROPERTY + " to " + hostList.toString());
        System.setProperty(ZooKeeperServer.ZOOKEEPER_VESPA_CLIENTS_PROPERTY, hostList.toString());
    }

    @Override
    public String toString() {
        return "ZooKeeper access maintainer";
    }

}
