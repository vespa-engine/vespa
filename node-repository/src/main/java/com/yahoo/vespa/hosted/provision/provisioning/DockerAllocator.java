package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Set of methods to allocate new docker nodes
 * <p>
 * The nodes are not added to the repository here - this is done by caller.
 *
 * @author smorgrav
 */
public class DockerAllocator {

    /**
     * The docker container allocation algorithm
     */
    static List<Node> allocateNewDockerNodes(NodeAllocation allocation,
                                                     NodeSpec requestedNodes,
                                                     List<Node> allNodes,
                                                     List<Node> nodesBefore,
                                                     NodeFlavors flavors,
                                                     Flavor flavor,
                                                     int nofSpares,
                                                     BiConsumer<List<Node>, String> recorder) {
        // Try allocate new nodes with all constraints in place
        List<Node> nodesWithHeadroomAndSpares = DockerCapacityConstraints.addHeadroomAndSpareNodes(allNodes, flavors, nofSpares);
        recorder.accept(nodesWithHeadroomAndSpares, "Headroom and spares");
        List<Node> accepted = DockerAllocator.allocate(allocation, flavor, nodesWithHeadroomAndSpares);

        List<Node> allNodesIncludingAccepted = new ArrayList<>(allNodes);
        allNodesIncludingAccepted.addAll(accepted);
        recorder.accept(allNodesIncludingAccepted, "1st dynamic docker allocation - fullfilled: " + allocation.fullfilled());

        // If still not fully allocated - try to allocate the remaining nodes with only hard constraints
        if (!allocation.fullfilled()) {
            List<Node> nodesWithSpares = DockerCapacityConstraints.addSpareNodes(allNodesIncludingAccepted, nofSpares);
            recorder.accept(nodesWithSpares, "Spares only");

            List<Node> acceptedWithHard = DockerAllocator.allocate(allocation, flavor, nodesWithSpares);
            accepted.addAll(acceptedWithHard);
            allNodesIncludingAccepted.addAll(acceptedWithHard);
            recorder.accept(allNodesIncludingAccepted, "2nd dynamic docker allocation - fullfilled: " + allocation.fullfilled());

            // If still not fully allocated and this is a replacement - drop all constraints
            boolean isReplacement = DockerAllocator.isReplacement(requestedNodes, nodesBefore, allNodes);
            if (!allocation.fullfilled() && isReplacement) {
                List<Node> finalTry = DockerAllocator.allocate(allocation, flavor, allNodesIncludingAccepted);
                accepted.addAll(finalTry);
                allNodesIncludingAccepted.addAll(finalTry);
                recorder.accept(allNodesIncludingAccepted, "Final dynamic docker alloction - fullfilled: " + allocation.fullfilled());
            }
        }

        return accepted;
    }

    /**
     * Offer the node allocation a prioritized set of new nodes according to capacity constraints
     *
     * @param allocation The allocation we want to fulfill
     * @param flavor     Since we create nodes here we need to know the exact flavor
     * @param nodes      The nodes relevant for the allocation (all nodes from node repo give or take)
     * @return Nodes accepted by the node allocation - these nodes does not exist in the noderepo yet.
     * @see DockerHostCapacity
     */
    public static List<Node> allocate(NodeAllocation allocation, Flavor flavor, List<Node> nodes) {

        DockerHostCapacity dockerCapacity = new DockerHostCapacity(nodes);

        // Get all active docker hosts with enough capacity and ip slots - sorted on free capacity
        List<Node> dockerHosts = nodes.stream()
                .filter(node -> node.type().equals(NodeType.host))
                .filter(dockerHost -> dockerHost.state().equals(Node.State.active))
                .filter(dockerHost -> dockerCapacity.hasCapacity(dockerHost, flavor))
                .sorted(dockerCapacity::compare)
                .collect(Collectors.toList());

        // Create one node pr. docker host that we can offer to the allocation
        List<Node> offers = new LinkedList<>();
        for (Node parentHost : dockerHosts) {
            Set<String> ipAddresses = DockerHostCapacity.findFreeIps(parentHost, nodes);
            if (ipAddresses.isEmpty()) continue;
            String ipAddress = ipAddresses.stream().findFirst().get();
            String hostname = lookupHostname(ipAddress);
            if (hostname == null) continue;
            Node node = Node.createDockerNode("fake-" + hostname, Collections.singleton(ipAddress),
                    Collections.emptySet(), hostname, Optional.of(parentHost.hostname()), flavor, NodeType.tenant);
            offers.add(node);
        }

        return allocation.offer(offers, false);
    }

    /**
     * From ipAddress - get hostname
     *
     * @return hostname or null if not able to do the loopup
     */
    private static String lookupHostname(String ipAddress) {
        try {
            return InetAddress.getByName(ipAddress).getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * This is an heuristic way to find if new nodes are to replace failing nodes
     * or are to expand the cluster.
     *
     * The current implementation does not account for failed nodes that are not in the application
     * anymore. The consequence is that we will ignore the spare capacity constraints too often - in
     * particular when the number of failed nodes (not in the application anymore)
     * for the cluster equal to the upscaling of the cluster.
     *
     * The deployment algorithm will still try to allocate the the capacity outside the spare capacity if possible.
     *
     * TODO propagate this information either through the node object or from the configserver deployer
     */
    private static boolean isReplacement(NodeSpec nodeSpec, List<Node> nodesBefore, List<Node> nodesReserved) {
        int wantedCount = 0;
        if (nodeSpec instanceof NodeSpec.CountNodeSpec) {
            NodeSpec.CountNodeSpec countSpec = (NodeSpec.CountNodeSpec) nodeSpec;
            wantedCount = countSpec.getCount();
        }

        List<Node> failedNodes = new ArrayList<>();
        for (Node node : nodesBefore) {
            if (node.state() == Node.State.failed) {
                failedNodes.add(node);
            }
        }

        if (failedNodes.size() == 0) return false;
        return (wantedCount <= nodesReserved.size() + failedNodes.size());
    }
}
