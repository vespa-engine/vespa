package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.provisioning.DockerCapacityConstraints;
import com.yahoo.vespa.hosted.provision.provisioning.DockerHostCapacity;
import com.yahoo.vespa.hosted.provision.provisioning.ResourceCapacity;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Retire nodes from hosts that are/should be reserved for spares and headroom.
 * Retired nodes will then be reallocated through a set of other maintainers.
 * <p>
 * Is currently very restrictive in terms of number of concurrent moves and when
 * to move.
 *
 * @author smorgrav
 */
public class AllocationMaintainer extends Maintainer {

    private final int spares;

    protected AllocationMaintainer(NodeRepository nodeRepository, Duration interval, JobControl jobControl, int spares) {
        super(nodeRepository, interval, jobControl);
        this.spares = spares;
    }

    @Override
    protected void maintain() {
        maintainRetirements();
        maintainSpares();
        maintainHeadroom();
    }

    /**
     * Check if we have a need to retire nodes - if not, unretire nodes previously retired with this maintainer.
     *
     * This happens if e.g. if applications are deleted/downsized, flavor migrated or if new hosts are added.
     */
    private void maintainRetirements() {
        boolean retirementsNeeded = false;

        // Check spares - spare hosts should not have children/nodes
        List<Node> spareHosts = DockerCapacityConstraints.getSpareHosts(nodeRepository().getNodes(), spares);
        for (Node spare : spareHosts) {
            retirementsNeeded = !nodeRepository().getChildNodes(spare.hostname()).isEmpty();
        }

        // Flavors with ideal headroom sorted on smallest to biggest
        List<Flavor> flavors = nodeRepository().getAvailableFlavors().getFlavors().stream()
                .filter(f -> f.getIdealHeadroom() > 0)
                .sorted((a, b) -> ResourceCapacity.of(a).compare(ResourceCapacity.of(b)))
                .collect(Collectors.toList());

        for (Flavor flavor : flavors) {
            long count = nodeRepository().getNodes().stream()
                    .filter(n -> n.allocation().isPresent())
                    .filter(n -> n.allocation().get().owner().tenant().value().equals(DockerCapacityConstraints.HEADROOM_TENANT))
                    .count();
            retirementsNeeded = count != flavor.getIdealHeadroom();
        }

        if (!retirementsNeeded) {
            getNodesRetiredByAllocationMaintainer().map(
                    n -> nodeRepository().write(n.unretire()));
        }
    }

    /**
     * Remove nodes from the spare hosts - if we have space for it.
     *
     * @return true if nodes are retired to reclaim spare capacity
     */
    private void maintainSpares() {
        if (getNodesRetiredByAllocationMaintainer().count() > 0) return;

        List<Node> spareHosts = DockerCapacityConstraints.getSpareHosts(nodeRepository().getNodes(), spares);

        for (Node spare : spareHosts) {
            for (Node nodeOnSpareHost : nodeRepository().getChildNodes(spare.hostname())) {
                if (hasCapacityToReallocateNode(nodeRepository().getNodes(), spare, nodeOnSpareHost)) {
                    retire(nodeOnSpareHost);
                    return;
                }
            }
        }
    }

    /**
     * Remove nodes from the hosts where we would like to have headroom
     * <p>
     * Only retire nodes if we know there should be space on other hosts
     */
    private void maintainHeadroom() {
        if (getNodesRetiredByAllocationMaintainer().count() > 0) return;

        // Get all nodes including the spares and headroom that we can fit as of now
        List<Node> nodes = DockerCapacityConstraints.addHeadroomAndSpareNodes(
                nodeRepository().getNodes(),
                nodeRepository().getAvailableFlavors(),
                spares);

        // Flavors with ideal headroom sorted on smallest to biggest
        List<Flavor> flavors = nodeRepository().getAvailableFlavors().getFlavors().stream()
                .filter(f -> f.getIdealHeadroom() > 0)
                .sorted((a, b) -> ResourceCapacity.of(a).compare(ResourceCapacity.of(b)))
                .collect(Collectors.toList());

        for (Flavor flavor : flavors) {
            // Check if ideal headroom for this flavor is achieved
            long count = nodes.stream()
                    .filter(n -> n.allocation().isPresent())
                    .filter(n -> n.allocation().get().owner().tenant().value().equals(DockerCapacityConstraints.HEADROOM_TENANT))
                    .count();
            if (count == flavor.getIdealHeadroom()) continue;


            DockerHostCapacity capacity = new DockerHostCapacity(nodes);
            ResourceCapacity wantedCapacity = ResourceCapacity.of(flavor);
            for (Node dockerHost : getEligableDockerHostsToFreeUpForHeadroom(nodes, wantedCapacity)) {

                ResourceCapacity freeCapacity = capacity.freeCapacityOf(dockerHost, false);
                ResourceCapacity neededCapacity = ResourceCapacity.subtract(wantedCapacity, freeCapacity);

                // Find the set of child nodes that can give us enough capacity if we move it - sorted from the smallest to the biggest
                List<Node> retirementAlternatives = nodeRepository().getChildNodes(dockerHost.hostname()).stream()
                        .filter(n -> ResourceCapacity.add(ResourceCapacity.of(n), freeCapacity).compare(wantedCapacity) <= 0)
                        .sorted((a, b) -> ResourceCapacity.of(a).compare(ResourceCapacity.of(b)))
                        .collect(Collectors.toList());

                // Now check if we have sufficient space to allocate on a different host - retire if yes
                for (Node child : retirementAlternatives) {
                    if (hasCapacityToReallocateNode(nodes, dockerHost, child)) {
                        boolean sucessfullyRetired = retire(child);
                        if (sucessfullyRetired) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * @return the stream of nodes that are retired by the allocation maintainer.
     */
    private Stream<Node> getNodesRetiredByAllocationMaintainer() {
        return nodeRepository().getNodes(Node.State.active).stream()
                .filter(n -> n.history().event(History.Event.Type.retired).isPresent())
                .filter(n -> n.history().event(History.Event.Type.retired).get().agent().equals(Agent.AllocationMaintainer))
                .filter(n -> n.allocation().isPresent())
                .filter(n -> n.allocation().get().membership().retired());
    }

    /**
     * @param nodes All nodes in the system (including temporary headroom allocations)
     * @param dockerHost The host we want to migrate a node off
     * @param dockerNode The node we want to migrate off the host
     *
     * @return true if we have other hosts that can take over the node (resource wise)
     */
    private boolean hasCapacityToReallocateNode(List<Node> nodes, Node dockerHost, Node dockerNode) {
        DockerHostCapacity capacity = new DockerHostCapacity(nodes);
        long numberOfhostsWhereWeCanReallocateChild = DockerCapacityConstraints.getAvailableDockerHostsSortedOnFreeCapacity(nodes)
                .filter(n -> !n.equals(dockerHost)) // Ignore the childs' parent
                .filter(n -> capacity.freeCapacityOf(n, true).hasCapacityFor(ResourceCapacity.of(dockerNode)))
                .count();
        return numberOfhostsWhereWeCanReallocateChild > 0;
    }

    /**
     * The hosts eligible for headroom maintenance are hosts that is not already allocated with a headroom
     * node, does not currently have space for the headroom in question and we choose the host with the smallest
     * amount of capacity that needs to be moved.
     *
     * @return a prioritized set of docker hosts that is eligible for freeing up resources.
     */
    protected List<Node> getEligableDockerHostsToFreeUpForHeadroom(List<Node> nodes, ResourceCapacity wantedCapacity) {
        DockerHostCapacity capacity = new DockerHostCapacity(nodes);
        return DockerCapacityConstraints.getAvailableDockerHostsSortedOnFreeCapacity(nodes)
                .filter(onlyHostWithoutHeadroomAllocation())
                .filter(host -> !capacity.freeCapacityOf(host, false).hasCapacityFor(wantedCapacity))
                .sorted((a, b) -> capacity.compare(b, a))
                .collect(Collectors.toList());
    }

    /**
     * Get hosts not previously allocated with headroom or spare nodes.
     *
     * We only allow one headroom allocation pr host
     * @return Predicate stating if the host as children of headroom or spare type.
     */
    protected Predicate<Node> onlyHostWithoutHeadroomAllocation() {
        return host -> nodeRepository().getChildNodes(host.hostname()).stream()
                .filter(n -> n.allocation().isPresent())
                .filter(n -> n.allocation().get().owner().tenant().value().equals(DockerCapacityConstraints.HEADROOM_TENANT))
                .count() == 0;
    }

    /**
     * Mark the node as retired in the node repository if certain
     * preconditions are meet:
     * <p>
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

    /**
     * Mark the node as unretired and thus become active again
     *
     * @param node THe node being unretired
     * @return true if the nodes was unretired
     */
    private boolean unretire(Node node) {
        // This should never happen - but documents an assumption
        if (!node.allocation().isPresent()) return false;

        // Lock noderepo do some
        ApplicationId appId = node.allocation().get().owner();
        try (Mutex lock = nodeRepository().lock(appId, Duration.ofSeconds(1))) {

            // Make sure the node is still here and active now that we have acquired the lock
            if (!nodeRepository().getNodes(appId, Node.State.active).contains(node)) {
                return false;
            }

            Node retired = node.unretire();
            nodeRepository().write(retired);
        }

        return true;
    }

    /**
     * @return true if the application has nodess retired for the same cluster
     */
    protected boolean hasRetiredNodes(ApplicationId appid, ClusterSpec cluster) {
        return nodeRepository().getNodes(appid).stream()
                .filter(n -> n.allocation().get().membership().retired())
                .filter(n -> n.allocation().get().membership().cluster().equals(cluster))
                .count() > 0;
    }
}
