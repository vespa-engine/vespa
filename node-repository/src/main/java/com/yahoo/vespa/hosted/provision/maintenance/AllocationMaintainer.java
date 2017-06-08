package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.DockerCapacityConstraints;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author smorgrav
 */
public class AllocationMaintainer extends Maintainer {

    private final Deployer deployer;
    private final int spares;
    private final Executor deploymentExecutor = Executors.newCachedThreadPool();

    protected AllocationMaintainer(Deployer deployer, NodeRepository nodeRepository, Duration interval, JobControl jobControl, int spares) {
        super(nodeRepository, interval, jobControl);
        this.deployer = deployer;
        this.spares = spares;
    }

    @Override
    protected void maintain() {
        // Spare nodes maintainer
        List<Node> nodes = DockerCapacityConstraints.getSpareHosts(nodeRepository().getNodes(), spares);
        for (Node node : nodes) {
            List<Node> children = nodeRepository().getChildNodes(node.hostname());
            for (Node child : children) {
                ApplicationId appid = child.allocation().get().owner();
                try (Mutex lock = nodeRepository().lock(appid, Duration.ofSeconds(1))) {

                    // Make sure the node is still here and active now that we have aquired the lock
                    nodeRepository().getNodes(appid, Node.State.active).contains(node);

                    // Only retire one node in the same cluster at the time
                    if (!hasRetiredNodes(appid, child.allocation().get().membership().cluster())) {
                        Node retired = child.retire(Agent.AllocationMaintainer, Instant.now());
                        nodeRepository().write(retired);
                    }
                }

            }
        }

        // Headroom maintainer
        List<Node> nodes = DockerCapacityConstraints.addHeadroomAndSpareNodes(nodeRepository().getNodes(), nodeRepository().getAvailableFlavors(), spares);
    }

    private boolean hasRetiredNodes(ApplicationId appid, ClusterSpec cluster) {
        return nodeRepository().getNodes(appid).stream()
                .filter(n -> n.allocation().get().membership().retired())
                .filter(n -> n.allocation().get().membership().cluster().equals(cluster))
                .count() > 0;
    }
}
