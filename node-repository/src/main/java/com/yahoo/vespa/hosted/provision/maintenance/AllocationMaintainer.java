package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Flavor;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.DockerCapacityConstraints;
import com.yahoo.vespa.hosted.provision.provisioning.DockerHostCapacity;
import com.yahoo.vespa.hosted.provision.provisioning.ResourceCapacity;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Move nodes from hosts that are/should be reserved for spares and headroom.
 *
 * Is currently very restrictive in terms of number of concurrent moves and when
 * to move.
 *
 * @author smorgrav
 */
public class AllocationMaintainer extends Maintainer {

    private final Deployer deployer;
    private final int spares;

    protected AllocationMaintainer(Deployer deployer, NodeRepository nodeRepository, Duration interval, JobControl jobControl, int spares) {
        super(nodeRepository, interval, jobControl);
        this.deployer = deployer;
        this.spares = spares;
    }

    @Override
    protected void maintain() {
        maintainSpares();
        maintainHeadroom();
    }

    /**
     * Remove nodes from the spare hosts
     */
    private void maintainSpares() {
        List<Node> spareHosts = DockerCapacityConstraints.getSpareHosts(nodeRepository().getNodes(), spares);
        for (Node spare : spareHosts) {
            for (Node nodeOnSpareHost : nodeRepository().getChildNodes(spare.hostname())) {
                retire(nodeOnSpareHost);
            }
        }

        // TODO Now check if we have nodes that are retired by this methods on hosts that no-longer is
        // considered spa
    }

    /**
     * Remove nodes from the nodes that we would like to have as headroom
     *
     * Only retire nodes if we know there should be space on other hosts
     */
    private void maintainHeadroom() {
        // Get all nodes including the spares and headroom that we can fit as of now
        List<Node> nodes = DockerCapacityConstraints.addHeadroomAndSpareNodes(
                nodeRepository().getNodes(),
                nodeRepository().getAvailableFlavors(),
                spares);

        // Flavors with ideal headroom sorted on smallest to biggest
        List<Flavor> flavors = nodeRepository().getAvailableFlavors().getFlavors().stream()
                .filter(f -> f.getIdealHeadroom() > 0)
                .sorted((a,b) -> ResourceCapacity.of(a).compare(ResourceCapacity.of(b)))
                .collect(Collectors.toList());

        for (Flavor flavor : flavors) {
            // Check if ideal headroom for this flavor is achieved
            long count = nodes.stream()
                    .filter(n -> n.allocation().isPresent())
                    .filter(n -> n.allocation().get().owner().tenant().equals(DockerCapacityConstraints.HEADROOM_TENANT))
                    .count();
            if (count == flavor.getIdealHeadroom()) continue;

            // Find the node that is closest to fulfill the headroom requirement for the current flavor
            // TODO we can look at all nodes when the same amount of free capacity - not just the first
            Optional<Node> targetNode = DockerCapacityConstraints.getAvailableDockerHostsSortedOnFreeCapacity(nodes)
                    .filter(n -> !n.allocation().isPresent() || !n.allocation().get().owner().tenant().equals(DockerCapacityConstraints.HEADROOM_TENANT))
                    .findFirst();

            // On that host - see if we can retire one child node to resurrect the headroom
            if (targetNode.isPresent()) {
                Node parentNode = targetNode.get();
                // Figure out how much we are missing to get the headroom for this flavor on this node
                DockerHostCapacity capacity = new DockerHostCapacity(nodes);
                ResourceCapacity freeCapacity = capacity.freeCapacityOf(parentNode, false);
                ResourceCapacity wantedCapacity = ResourceCapacity.of(flavor);
                ResourceCapacity neededCapacity = ResourceCapacity.subtract(wantedCapacity, freeCapacity);

                if (!(neededCapacity.getCpu() > 0 || neededCapacity.getDisk() > 0 || neededCapacity.getMemory() > 0)) {
                    // TODO log warning and continue - something is wrong
                }

                // Find the set of childnodes that can give us enough capacity if we move it - sorted from the smallest to the biggest
                List<Node> retirementAlternatives = nodeRepository().getChildNodes(parentNode.hostname()).stream()
                        .filter(n -> ResourceCapacity.of(n).hasCapacityFor(neededCapacity))
                        .sorted((a,b) -> ResourceCapacity.of(a).compare(ResourceCapacity.of(b)))
                        .collect(Collectors.toList());

                // Now check if we have sufficient pace to allocate on a different host
                for (Node child : retirementAlternatives) {
                    long numberOfhostsWhereWeCanReallocateChild = DockerCapacityConstraints.getAvailableDockerHostsSortedOnFreeCapacity(nodes)
                            .filter(n -> !n.equals(parentNode))
                            .filter(n -> capacity.freeCapacityOf(n, false).compare(ResourceCapacity.of(child)) >= 0)
                            .count();

                    if (numberOfhostsWhereWeCanReallocateChild > 0) {
                        boolean sucessfullyRetired = retire(child);
                        if (sucessfullyRetired) {
                            break; // We are done for this iteration - only one at the time
                        }
                    }
                }
            }
        }

        // TODO check if we have nodes that should be unretired
    }

    /**
     * Mark the node as retired in the node repository if certain
     * preconditions are meet:
     *
     * 1. The cluster has no other retired nodes
     * 2. There are space on other hosts to re-allocate the node
     *
     * @param node The node being marked as retired
     * @return True if the node was marked with retire
     */
    private boolean retire(Node node) {
        // This should never happen - but documents an assumption
        if (!node.allocation().isPresent()) return false;

        ApplicationId appId = node.allocation().get().owner();
        ClusterSpec clusterSpec = node.allocation().get().membership().cluster();

        // Lock noderepo do some
        try (Mutex lock = nodeRepository().lock(appId, Duration.ofSeconds(1))) {

            // Make sure the node is still here and active now that we have acquired the lock
            nodeRepository().getNodes(appId, Node.State.active).contains(node);

            // Only retire if the cluster this node belongs to does not have other retirements pending
            if (hasRetiredNodes(appId, clusterSpec)) return false;

            Node retired = node.retire(Agent.AllocationMaintainer, nodeRepository().clock().instant());
            nodeRepository().write(retired);
        }

        return true;
    }

    private boolean hasRetiredNodes(ApplicationId appid, ClusterSpec cluster) {
        return nodeRepository().getNodes(appid).stream()
                .filter(n -> n.allocation().get().membership().retired())
                .filter(n -> n.allocation().get().membership().cluster().equals(cluster))
                .count() > 0;
    }
}
